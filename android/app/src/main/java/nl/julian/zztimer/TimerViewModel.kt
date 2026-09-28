package nl.julian.zztimer

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime

/** Invoer uit het dialoogvenster voor een geplande start. */
data class ScheduleInput(val id: Long?, val hour: Int, val minute: Int, val kind: Int)

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("zz_timer", Context.MODE_PRIVATE)

    val client = EspClient(app, viewModelScope, prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST)

    val status: StateFlow<EspStatus?> = client.status
    val connected: StateFlow<Boolean> = client.connected
    val rttMs: StateFlow<Long?> = client.rttMs
    val wifiSignal: StateFlow<Int?> = client.wifiSignal

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: Flow<String> = merge(_messages, client.events)

    /** Alleen in het geheugen zolang de instellingen open zijn; nooit opgeslagen. */
    var adminPin: String? by mutableStateOf(null)
        private set

    val host: String get() = client.host

    /** Vlaggenpagina: vlaggen, spraak, terugroepen en uitstel. */
    val flags = FlagController(
        app = app,
        scope = viewModelScope,
        client = client,
        prefs = prefs,
        hornOn = ::hornPressed,
        hornOff = ::hornReleased,
        message = { _messages.emit(it) },
    )

    init {
        client.start()
        flags.startTicker()
    }

    override fun onCleared() {
        hornReleased()
        flags.shutdown()
        client.stop()
    }

    // ------------------------------------------------------------------ bediening

    /** START: volgens de keuze onder de knoppen 10 seconden aftellen of direct starten. */
    fun start() = flags.start()

    /** STOP en RESET doen hetzelfde: terug naar 5:00, net als de resetknop. */
    fun reset() = flags.reset()

    fun setLedPanel(on: Boolean) = send("led") { put("on", on) }

    private var hornJob: Job? = null

    /**
     * Toeter aan zolang de knop is ingedrukt. Het "aan"-commando wordt elke 200 ms herhaald;
     * de ESP zet de toeter zelf uit als dat 600 ms uitblijft (bijv. bij verbindingsverlies).
     */
    fun hornPressed() {
        hornJob?.cancel()
        hornJob = viewModelScope.launch {
            while (true) {
                client.sendNow("horn") { put("on", true) }
                delay(200)
            }
        }
    }

    fun hornReleased() {
        hornJob?.cancel()
        hornJob = null
        client.sendNow("horn") { put("on", false) }
    }

    private fun send(cmd: String, build: JSONObject.() -> Unit = {}) {
        viewModelScope.launch {
            val ack = client.command(cmd, build)
            if (!ack.ok) _messages.emit(ack.error ?: "Opdracht mislukt.")
        }
    }

    // ------------------------------------------------------------------ geplande starts

    /**
     * Rekent de gekozen tijd om naar een absoluut tijdstip. Ligt het (START-)moment vandaag al
     * in het verleden, dan wordt het morgen.
     */
    suspend fun saveSchedule(input: ScheduleInput): String? {
        val zone = ZoneId.systemDefault()
        var target = ZonedDateTime.now(zone)
            .withHour(input.hour).withMinute(input.minute).withSecond(0).withNano(0)
        var press = if (input.kind == 0) target.minusMinutes(5) else target
        if (press.toInstant().toEpochMilli() <= System.currentTimeMillis() + 5_000) {
            target = target.plusDays(1)
            press = if (input.kind == 0) target.minusMinutes(5) else target
        }
        val targetMs = target.toInstant().toEpochMilli()
        val pressMs = press.toInstant().toEpochMilli()

        if (status.value?.timeSynced != true) client.syncClock()

        val ack = client.command(if (input.id == null) "sched_add" else "sched_edit") {
            if (input.id != null) put("sched_id", input.id)
            put("press_epoch", pressMs)
            put("target_epoch", targetMs)
            put("kind", input.kind)
        }
        if (!ack.ok) return ack.error ?: "Opslaan mislukt."
        _messages.emit("Gepland: ${TimeFormat.dayLabel(targetMs)} ${TimeFormat.time(targetMs)}")
        return null
    }

    fun deleteSchedule(id: Long) = send("sched_del") { put("sched_id", id) }

    // ------------------------------------------------------------------ instellingen

    suspend fun unlockSettings(pin: String): String? {
        val ack = client.command("pin_check") { put("pin", pin) }
        if (!ack.ok) return ack.error ?: "Onjuiste PIN."
        adminPin = pin
        return null
    }

    fun lockSettings() {
        adminPin = null
    }

    suspend fun saveWifi(ssid: String, password: String): String? {
        val ack = client.command("wifi_set") {
            put("pin", adminPin ?: "")
            put("ssid", ssid)
            put("pass", password)
        }
        return if (ack.ok) null else ack.error ?: "Opslaan mislukt."
    }

    suspend fun changePin(newPin: String): String? {
        val ack = client.command("pin_set") {
            put("pin", adminPin ?: "")
            put("new_pin", newPin)
        }
        if (!ack.ok) return ack.error ?: "PIN wijzigen mislukt."
        adminPin = newPin
        return null
    }

    fun saveHost(newHost: String) {
        prefs.edit().putString(KEY_HOST, newHost).apply()
        client.setHost(newHost)
    }

    suspend fun testConnection(): Long? = client.testConnection()

    companion object {
        const val DEFAULT_HOST = "192.168.4.1"
        private const val KEY_HOST = "host"
    }
}
