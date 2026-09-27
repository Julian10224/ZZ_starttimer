package nl.julian.zztimer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class Ack(val ok: Boolean, val error: String? = null, val schedId: Long? = null)

/**
 * Verbinding met de ESP via WebSocket (ws://<ip>:81/).
 *
 * Tijd: de ESP is leidend. De app houdt een klokverschil bij tussen de ESP-klok en de
 * monotone telefoonklok (SystemClock.elapsedRealtime). De wedstrijdtijd wordt uit de
 * starttijd van de ESP en dat klokverschil berekend, dus de app-weergave loopt ook door
 * als er even geen berichten binnenkomen en kan niet langzaam gaan afwijken.
 */
class EspClient(context: Context, private val scope: CoroutineScope, host: String) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile var host: String = host
        private set

    @Volatile private var wifiNetwork: Network? = null
    @Volatile private var socket: WebSocket? = null
    @Volatile private var lastMessageAt = 0L
    @Volatile private var clockOffset: Long? = null        // ESP-ms minus elapsedRealtime
    @Volatile private var offsetFromSync = false

    private val nextId = AtomicLong(1)
    private val pendingAcks = ConcurrentHashMap<Long, CompletableDeferred<Ack>>()
    private val pendingSyncs = ConcurrentHashMap<Long, PendingSync>()
    private val syncMutex = Mutex()
    private val jobs = mutableListOf<Job>()

    private val _status = MutableStateFlow<EspStatus?>(null)
    val status: StateFlow<EspStatus?> = _status.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _rttMs = MutableStateFlow<Long?>(null)
    val rttMs: StateFlow<Long?> = _rttMs.asStateFlow()

    private val _wifiSignal = MutableStateFlow<Int?>(null)
    val wifiSignal: StateFlow<Int?> = _wifiSignal.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private class SyncSample(val rtt: Long, val offsetRealtime: Long, val offsetEpoch: Long)
    private class PendingSync(val real0: Long, val epoch0: Long, val result: CompletableDeferred<SyncSample>)

    // ------------------------------------------------------------------ levenscyclus

    fun start() {
        registerWifi()
        jobs += scope.launch { connectionLoop() }
        jobs += scope.launch { watchdog() }
        jobs += scope.launch { periodicSync() }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        socket?.cancel()
        socket = null
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    fun setHost(newHost: String) {
        host = newHost
        reconnect()
    }

    /** Geschatte huidige ESP-klok in ms, of null zolang er nog niets bekend is. */
    fun espNow(): Long? = clockOffset?.let { SystemClock.elapsedRealtime() + it }

    // ------------------------------------------------------------------ verbinding

    private fun reconnect() {
        val s = socket
        socket = null
        _connected.value = false
        s?.cancel()
    }

    private suspend fun connectionLoop() {
        while (scope.isActive) {
            if (socket == null) connect()
            delay(2000)
        }
    }

    private fun connect() {
        val net = wifiNetwork
        val client = if (net != null) {
            // Via het WiFi-netwerk van de ESP, ook als dat geen internet heeft en
            // Android mobiele data als standaardnetwerk gebruikt.
            baseClient.newBuilder().socketFactory(net.socketFactory).build()
        } else {
            baseClient
        }
        val request = Request.Builder().url("ws://$host:81/").build()
        socket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socket !== webSocket) return
            lastMessageAt = SystemClock.elapsedRealtime()
            scope.launch { syncClock() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (socket !== webSocket) return
            lastMessageAt = SystemClock.elapsedRealtime()
            _connected.value = true
            handleMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(webSocket)
    }

    private fun lost(webSocket: WebSocket) {
        if (socket === webSocket) {
            socket = null
            _connected.value = false
        }
        pendingAcks.values.forEach { it.complete(Ack(false, "Verbinding met de timer verbroken.")) }
        pendingAcks.clear()
    }

    private suspend fun watchdog() {
        while (scope.isActive) {
            val silentFor = SystemClock.elapsedRealtime() - lastMessageAt
            _connected.value = socket != null && silentFor < 3500
            // De ESP stuurt minimaal elke seconde een status; blijft die weg, dan opnieuw verbinden.
            if (socket != null && lastMessageAt > 0 && silentFor > 7000) reconnect()
            delay(500)
        }
    }

    private suspend fun periodicSync() {
        while (scope.isActive) {
            delay(30_000)
            if (_connected.value) syncClock()
        }
    }

    // ------------------------------------------------------------------ WiFi-netwerk

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetwork = network
            reconnect()
        }

        override fun onLost(network: Network) {
            if (wifiNetwork == network) {
                wifiNetwork = null
                _wifiSignal.value = null
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val s = caps.signalStrength
                if (s != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) _wifiSignal.value = s
            }
        }
    }

    private fun registerWifi() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ berichten

    private fun handleMessage(text: String) {
        val o = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        when (o.optString("type")) {
            "status" -> handleStatus(o)
            "ack" -> {
                val ack = Ack(
                    ok = o.optBoolean("ok"),
                    error = if (o.has("error")) o.optString("error") else null,
                    schedId = if (o.has("sched_id")) o.optLong("sched_id") else null,
                )
                pendingAcks.remove(o.optLong("id"))?.complete(ack)
            }
            "sync" -> {
                val real1 = SystemClock.elapsedRealtime()
                val epoch1 = System.currentTimeMillis()
                val p = pendingSyncs.remove(o.optLong("id")) ?: return
                val espMs = o.optLong("esp_ms")
                p.result.complete(
                    SyncSample(
                        rtt = real1 - p.real0,
                        offsetRealtime = espMs - (p.real0 + real1) / 2,
                        offsetEpoch = (p.epoch0 + epoch1) / 2 - espMs,
                    )
                )
            }
            "event" -> {
                val target = TimeFormat.time(o.optLong("target_epoch"))
                val msg = when (o.optString("event")) {
                    "sched_started" -> "Geplande start ($target) is uitgevoerd."
                    "sched_missed" -> if (o.optString("reason") == "running")
                        "Geplande start $target overgeslagen: er liep al een procedure."
                    else
                        "Geplande start $target gemist: de timer was niet op tijd."
                    else -> null
                }
                if (msg != null) _events.tryEmit(msg)
            }
        }
    }

    private fun handleStatus(o: JSONObject) {
        val now = SystemClock.elapsedRealtime()
        val espMs = o.optLong("esp_ms")
        val coarse = espMs - now
        val current = clockOffset
        if (current == null) {
            clockOffset = coarse
        } else if (abs(coarse - current) > 750) {
            // ESP opnieuw opgestart of klok verschoven: direct opnieuw synchroniseren.
            clockOffset = coarse
            offsetFromSync = false
            scope.launch { syncClock() }
        } else if (!offsetFromSync) {
            clockOffset = coarse
        }

        val list = mutableListOf<Schedule>()
        val arr = o.optJSONArray("sched")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                list += Schedule(s.optLong("id"), s.optLong("press"), s.optLong("target"), s.optInt("kind"))
            }
        }
        list.sortBy { it.pressEpoch }

        _status.value = EspStatus(
            firmware = o.optString("fw"),
            espMs = espMs,
            running = o.optBoolean("running"),
            startMs = o.optLong("start_ms"),
            repeat = o.optBoolean("repeat"),
            switchRepeat = o.optBoolean("switch_repeat"),
            minutes = o.optInt("min"),
            seconds = o.optInt("sec"),
            ledPanel = o.optBoolean("led_panel", true),
            horn = o.optBoolean("horn"),
            manualAllowed = o.optBoolean("manual_allowed"),
            timeSynced = o.optBoolean("time_synced"),
            ssid = o.optString("ssid"),
            ip = o.optString("ip"),
            clients = o.optInt("clients"),
            uptimeS = o.optLong("uptime_s"),
            schedules = list,
        )
    }

    // ------------------------------------------------------------------ tijdsynchronisatie

    private suspend fun requestSync(): SyncSample? {
        val ws = socket ?: return null
        val id = nextId.getAndIncrement()
        val pending = PendingSync(SystemClock.elapsedRealtime(), System.currentTimeMillis(), CompletableDeferred())
        pendingSyncs[id] = pending
        val json = JSONObject().put("cmd", "sync").put("id", id).put("t", pending.epoch0)
        if (!ws.send(json.toString())) {
            pendingSyncs.remove(id)
            return null
        }
        return try {
            withTimeoutOrNull(1500) { pending.result.await() }
        } finally {
            pendingSyncs.remove(id)
        }
    }

    /**
     * Meet vijf keer de rondreistijd en gebruikt de snelste meting (minste onzekerheid).
     * Stuurt daarna het verschil met de telefoonklok naar de ESP voor geplande starts.
     */
    suspend fun syncClock(): Boolean = syncMutex.withLock {
        val samples = mutableListOf<SyncSample>()
        repeat(5) {
            requestSync()?.let { samples += it }
            delay(120)
        }
        val best = samples.minByOrNull { it.rtt } ?: return@withLock false
        if (best.rtt > 400) return@withLock false
        clockOffset = best.offsetRealtime
        offsetFromSync = true
        _rttMs.value = best.rtt
        command("settime") { put("offset_ms", best.offsetEpoch) }.ok
    }

    suspend fun testConnection(): Long? = requestSync()?.rtt?.also { _rttMs.value = it }

    // ------------------------------------------------------------------ commando's

    suspend fun command(cmd: String, build: JSONObject.() -> Unit = {}): Ack {
        val ws = socket ?: return Ack(false, "Geen verbinding met de timer.")
        val id = nextId.getAndIncrement()
        val json = JSONObject().put("cmd", cmd).put("id", id)
        json.build()
        val result = CompletableDeferred<Ack>()
        pendingAcks[id] = result
        if (!ws.send(json.toString())) {
            pendingAcks.remove(id)
            return Ack(false, "Verzenden naar de timer mislukt.")
        }
        return try {
            withTimeoutOrNull(4000) { result.await() } ?: Ack(false, "Geen antwoord van de timer.")
        } finally {
            pendingAcks.remove(id)
        }
    }
}
