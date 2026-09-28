package nl.julian.zztimer

import android.app.Application
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stuurt de vlaggenpagina: bepaalt welke vlaggen er staan, spreekt het aftellen uit en
 * voert terugroepen en uitstel uit. De tijd komt altijd van de ESP; deze klasse telt zelf
 * niet, zodat vlaggen, stem, display en hoorn altijd gelijk lopen.
 */
class FlagController(
    app: Application,
    private val scope: CoroutineScope,
    private val client: EspClient,
    private val prefs: SharedPreferences,
    private val hornOn: () -> Unit,
    private val hornOff: () -> Unit,
    private val message: suspend (String) -> Unit,
) {
    val voice = Voice(app)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<FlagSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(FlagState())
    val state: StateFlow<FlagState> = _state.asStateFlow()

    private var interrupt = Interrupt.NONE
    private var xSinceEsp = 0L               // ESP-tijd waarop de X-vlag op ging
    private var lastStartEsp: Long? = null   // ESP-tijd van de laatste start (0:00)
    private var lastK = -1L
    private var wasRunning = false
    private var classOffset = 0              // eerste klasse van de lopende reeks
    private var pendingOffset: Int? = null   // klasse waarmee de volgende reeks begint
    private var resumePressEpoch: Long? = null
    private var lastResumeSpoken = -1
    private var busy = false

    fun start() {
        scope.launch {
            while (isActive) {
                tick()
                delay(50)
            }
        }
    }

    fun shutdown() = voice.shutdown()

    // ------------------------------------------------------------------ instellingen

    private fun loadSettings(): FlagSettings {
        val prep = runCatching { PrepFlag.valueOf(prefs.getString(K_PREP, PrepFlag.P.name)!!) }.getOrDefault(PrepFlag.P)
        val classes = (prefs.getString(K_CLASSES, "Klasse") ?: "Klasse")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return FlagSettings(
            prep = prep,
            classes = classes.ifEmpty { listOf("Klasse") },
            voice = prefs.getBoolean(K_VOICE, true),
            everyMinute = prefs.getBoolean(K_MIN, true),
            every10s = prefs.getBoolean(K_10S, true),
            lastTen = prefs.getBoolean(K_LAST10, true),
        )
    }

    fun updateSettings(s: FlagSettings) {
        _settings.value = s
        prefs.edit()
            .putString(K_PREP, s.prep.name)
            .putString(K_CLASSES, s.classes.joinToString(", "))
            .putBoolean(K_VOICE, s.voice)
            .putBoolean(K_MIN, s.everyMinute)
            .putBoolean(K_10S, s.every10s)
            .putBoolean(K_LAST10, s.lastTen)
            .apply()
    }

    fun testVoice() = voice.say("Dit is de stem van de ZZ Wedstrijd Timer. Tien, negen, acht.")

    private fun speak(text: String) {
        if (_settings.value.voice) voice.say(text)
    }

    // ------------------------------------------------------------------ elke 50 ms

    private fun tick() {
        val s = client.status.value
        val now = client.espNow()
        val set = _settings.value
        if (s == null || now == null) {
            _state.value = FlagState(phase = Phase.NO_CONNECTION)
            return
        }

        val running = s.running
        if (running && !wasRunning) {
            // Nieuwe procedure gestart (knop, app, of geplande start na uitstel/terugroep)
            classOffset = pendingOffset ?: 0
            pendingOffset = null
            if (interrupt == Interrupt.GENERAL_RECALL || interrupt == Interrupt.POSTPONED) interrupt = Interrupt.NONE
            resumePressEpoch = null
            lastK = -1
        }
        if (!running && wasRunning) lastK = -1
        wasRunning = running

        var k = -1L
        if (running) {
            val elapsed = now - s.startMs
            k = Math.floorDiv(elapsed, 1000L)
            if (k >= 300) lastStartEsp = s.startMs + (k / 300) * 300_000L

            // Aankondigingen: iets vóór de seconde, zodat de stem gelijk valt met het display
            val kl = Math.floorDiv(elapsed + VOICE_LEAD_MS, 1000L)
            if (kl != lastK) {
                if (lastK == -1L && kl <= 1L) {
                    for (kk in 0L..kl) FlagLogic.announcement(kk, s.repeat, set, classOffset)?.let { speak(it) }
                } else if (kl == lastK + 1) {
                    FlagLogic.announcement(kl, s.repeat, set, classOffset)?.let { speak(it) }
                }
                lastK = kl
            }
        }

        // X-vlag gaat uiterlijk na 4 minuten neer
        if (interrupt == Interrupt.INDIVIDUAL_RECALL && now - xSinceEsp >= FlagLogic.X_MAX_MS) {
            interrupt = Interrupt.NONE
            speak("X-vlag neer.")
        }

        // Aftellen tot het waarschuwingssein na uitstel of algemene terugroep
        val resumeIn = resumePressEpoch?.let { ((it - System.currentTimeMillis() + 999) / 1000).toInt() }
        if (resumeIn != null && !running) {
            if (resumeIn != lastResumeSpoken && resumeIn in listOf(30, 10, 5, 4, 3, 2, 1)) {
                speak(if (resumeIn >= 10) "Waarschuwingssein over ${FlagLogic.words(resumeIn)} seconden." else FlagLogic.words(resumeIn) + ".")
                lastResumeSpoken = resumeIn
            }
            if (resumeIn < -5) resumePressEpoch = null      // geplande start niet uitgevoerd
        }

        _state.value = buildState(s, now, running, k, set, resumeIn)
    }

    private fun sinceStart(now: Long): Long? = lastStartEsp?.let { now - it }

    private fun buildState(s: EspStatus, now: Long, running: Boolean, k: Long, set: FlagSettings, resumeIn: Int?): FlagState {
        val since = sinceStart(now)
        val justStarted = since != null && since in 0..FlagLogic.X_MAX_MS
        val base = FlagState(
            interrupt = interrupt,
            running = running,
            canStart = !running && s.ledPanel && interrupt != Interrupt.POSTPONED && interrupt != Interrupt.GENERAL_RECALL && resumeIn == null,
            canIndividualRecall = justStarted && interrupt == Interrupt.NONE && !busy,
            canGeneralRecall = justStarted && (interrupt == Interrupt.NONE || interrupt == Interrupt.INDIVIDUAL_RECALL) && !busy,
            canPostpone = interrupt != Interrupt.POSTPONED && interrupt != Interrupt.GENERAL_RECALL && resumeIn == null && !busy &&
                (running || !justStarted),
            canResume = (interrupt == Interrupt.POSTPONED || interrupt == Interrupt.GENERAL_RECALL) && !busy,
        )
        val xFlag = if (interrupt == Interrupt.INDIVIDUAL_RECALL) listOf(ShownFlag(FlagKind.X, "X-vlag")) else emptyList()

        return when {
            interrupt == Interrupt.POSTPONED -> base.copy(
                phase = Phase.POSTPONED, timeText = "--:--",
                flags = listOf(ShownFlag(FlagKind.AP, "Uitstelwimpel")),
                nextText = "Wimpel neer: 1 geluidssein, waarschuwingssein 1 minuut later",
            )
            interrupt == Interrupt.GENERAL_RECALL -> base.copy(
                phase = Phase.GENERAL_RECALL, timeText = "--:--",
                flags = listOf(ShownFlag(FlagKind.FIRST_SUBSTITUTE, "Eerste vervangende")),
                nextText = "Vlag neer: 1 geluidssein, waarschuwingssein 1 minuut later",
            )
            resumeIn != null && !running -> base.copy(
                phase = Phase.RESUMING, timeText = FlagLogic.mmss(resumeIn), resuming = true,
                className = set.className(pendingOffset ?: 0),
                nextText = "Waarschuwingssein over ${FlagLogic.mmss(resumeIn)}",
            )
            running -> {
                val rem = FlagLogic.remainingForK(k)
                val cycle = FlagLogic.cycleForK(k)
                val cls = set.className(classOffset + cycle)
                val (flags, next, step) = FlagLogic.runningFlags(rem, cls, set)
                val phase = when (step) {
                    0 -> Phase.WARNING
                    1 -> Phase.PREPARATORY
                    2 -> Phase.LAST_MINUTE
                    else -> Phase.STARTED
                }
                base.copy(
                    phase = phase, timeText = TimerMath.format(rem), startNumber = cycle + 1, className = cls,
                    flags = flags + xFlag, nextText = next, timelineStep = step,
                )
            }
            else -> base.copy(
                phase = if (justStarted) Phase.STARTED else Phase.IDLE,
                timeText = TimerMath.format(300),
                className = set.className(0),
                flags = xFlag,
                timelineStep = if (justStarted) 3 else -1,
                nextText = if (justStarted) "Gestart ${FlagLogic.mmss(((since ?: 0) / 1000).toInt())} geleden" else null,
            )
        }
    }

    // ------------------------------------------------------------------ acties

    private suspend fun signals(count: Int) {
        repeat(count) { i ->
            hornOn()
            delay(1000)
            hornOff()
            if (i < count - 1) delay(700)
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    /** X-vlag op met één geluidssein (RvW 29.1). */
    fun individualRecall() = action {
        val now = client.espNow() ?: return@action
        interrupt = Interrupt.INDIVIDUAL_RECALL
        xSinceEsp = now
        speak("Individuele terugroep. X-vlag op.")
        signals(1)
    }

    fun lowerX() {
        if (interrupt != Interrupt.INDIVIDUAL_RECALL) return
        interrupt = Interrupt.NONE
        speak("X-vlag neer.")
    }

    /** Eerste vervangende op met twee geluidsseinen (RvW 29.2). De timer stopt. */
    fun generalRecall() = action {
        val s = client.status.value ?: return@action
        val startEsp = lastStartEsp ?: return@action
        val recalledCycle = ((startEsp - s.startMs) / 300_000L - 1).toInt().coerceAtLeast(0)
        pendingOffset = classOffset + recalledCycle
        val ack = client.command("reset")
        if (!ack.ok) {
            message(ack.error ?: "Timer niet bereikbaar.")
            return@action
        }
        interrupt = Interrupt.GENERAL_RECALL
        lastStartEsp = null
        speak("Algemene terugroep. Eerste vervangende op.")
        signals(2)
    }

    /** Uitstelwimpel op met twee geluidsseinen (RvW 27.3 / Seinen). De timer stopt. */
    fun postpone() = action {
        val s = client.status.value
        val now = client.espNow()
        if (s != null && now != null && s.running) {
            pendingOffset = classOffset + FlagLogic.cycleForK(Math.floorDiv(now - s.startMs, 1000L))
        } else {
            pendingOffset = pendingOffset ?: 0
        }
        val ack = client.command("reset")
        if (!ack.ok) {
            message(ack.error ?: "Timer niet bereikbaar.")
            return@action
        }
        interrupt = Interrupt.POSTPONED
        lastStartEsp = null
        speak("Uitstel. Uitstelwimpel op.")
        signals(2)
    }

    /**
     * Uitstelwimpel of eerste vervangende neer met één geluidssein. De timer start het
     * waarschuwingssein zelf één minuut later (geplande start op de ESP), ook als de app
     * dan niet meer open is.
     */
    fun resume() = action {
        val was = interrupt
        if (was != Interrupt.POSTPONED && was != Interrupt.GENERAL_RECALL) return@action
        if (client.status.value?.timeSynced != true) client.syncClock()
        val press = ((System.currentTimeMillis() + 60_000L + 999L) / 1000L) * 1000L
        val ack = client.command("sched_add") {
            put("press_epoch", press)
            put("target_epoch", press)
            put("kind", 1)
        }
        if (!ack.ok) {
            message(ack.error ?: "Kon het waarschuwingssein niet plannen.")
            return@action
        }
        interrupt = Interrupt.NONE
        resumePressEpoch = press
        lastResumeSpoken = -1
        speak(if (was == Interrupt.POSTPONED) "Uitstelwimpel neer. Waarschuwingssein over één minuut."
              else "Eerste vervangende neer. Waarschuwingssein over één minuut.")
        signals(1)
    }

    /** Geplande hervatting annuleren (timer blijft stil). */
    fun cancelResume() = action {
        val press = resumePressEpoch ?: return@action
        val sched = client.status.value?.schedules?.firstOrNull { it.pressEpoch == press }
        if (sched != null) {
            val ack = client.command("sched_del") { put("sched_id", sched.id) }
            if (!ack.ok) {
                message(ack.error ?: "Annuleren mislukt.")
                return@action
            }
        }
        resumePressEpoch = null
        pendingOffset = null
    }

    companion object {
        private const val VOICE_LEAD_MS = 250L
        private const val K_PREP = "flag_prep"
        private const val K_CLASSES = "flag_classes"
        private const val K_VOICE = "voice_on"
        private const val K_MIN = "voice_minutes"
        private const val K_10S = "voice_10s"
        private const val K_LAST10 = "voice_last10"
    }
}
