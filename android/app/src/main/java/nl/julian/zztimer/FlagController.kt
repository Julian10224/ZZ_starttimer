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
    private var lastPreSpoken = Int.MIN_VALUE  // laatst uitgesproken seconde vóór een geplande start
    private var warningSpokenAt = 0L         // telefoontijd waarop het waarschuwingssein al is uitgesproken
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
            countdownStart = prefs.getBoolean(K_COUNTDOWN, true),
            classColor = runCatching { ClassColor.valueOf(prefs.getString(K_CLASS_COLOR, ClassColor.WHITE.name)!!) }
                .getOrDefault(ClassColor.WHITE),
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
            .putBoolean(K_COUNTDOWN, s.countdownStart)
            .putString(K_CLASS_COLOR, s.classColor.name)
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
                    // Waarschuwingssein al uitgesproken aan het eind van het aftellen? Dan niet herhalen.
                    val alreadySpoken = System.currentTimeMillis() - warningSpokenAt < 3000
                    for (kk in 0L..kl) {
                        if (kk == 0L && alreadySpoken) continue
                        FlagLogic.announcement(kk, s.repeat, set, classOffset)?.let { speak(it) }
                    }
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

        // Aftellen tot een geplande start (START-knop, hervatten na uitstel/terugroep of
        // een geplande start uit de lijst): 30 s, dan 10 … 1 en het waarschuwingssein.
        var pendingIn: Int? = null
        if (!running) {
            val phoneNow = System.currentTimeMillis()
            val press = nextPress(s, phoneNow)
            if (press != null) {
                pendingIn = ceilDiv(press - phoneNow, 1000L).toInt()
                if (pendingIn > PENDING_WINDOW_S) pendingIn = null
                val left = ceilDiv(press - (phoneNow + VOICE_LEAD_MS), 1000L).toInt()
                if (left != lastPreSpoken) {
                    when {
                        left == 30 && set.every10s -> speak("Waarschuwingssein over dertig seconden.")
                        left in 1..10 && set.countdownStart -> speak(FlagLogic.countWord(left))
                        left == 0 && lastPreSpoken in 1..3 -> {
                            speak(FlagLogic.warningText(set.className(pendingOffset ?: 0)))
                            warningSpokenAt = phoneNow
                        }
                    }
                    lastPreSpoken = left
                }
            } else {
                lastPreSpoken = Int.MIN_VALUE
            }
        }

        _state.value = buildState(s, now, running, k, set, pendingIn)
    }

    /** Eerstvolgende geplande start (telefoontijd), of null. */
    private fun nextPress(s: EspStatus, phoneNow: Long): Long? =
        s.schedules.asSequence().map { it.pressEpoch }.filter { it >= phoneNow - 1500 }.minOrNull()

    private fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)

    private fun sinceStart(now: Long): Long? = lastStartEsp?.let { now - it }

    private fun buildState(s: EspStatus, now: Long, running: Boolean, k: Long, set: FlagSettings, pendingIn: Int?): FlagState {
        val since = sinceStart(now)
        val justStarted = since != null && since in 0..FlagLogic.X_MAX_MS
        val base = FlagState(
            interrupt = interrupt,
            running = running,
            canStart = !running && s.ledPanel && interrupt != Interrupt.POSTPONED && interrupt != Interrupt.GENERAL_RECALL &&
                (pendingIn == null || pendingIn > 15) && !busy,
            canIndividualRecall = justStarted && interrupt == Interrupt.NONE && !busy,
            canGeneralRecall = justStarted && (interrupt == Interrupt.NONE || interrupt == Interrupt.INDIVIDUAL_RECALL) && !busy,
            canPostpone = interrupt != Interrupt.POSTPONED && interrupt != Interrupt.GENERAL_RECALL && !busy &&
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
            pendingIn != null && !running -> base.copy(
                phase = Phase.RESUMING, timeText = FlagLogic.mmss(pendingIn), resuming = true,
                className = set.className(pendingOffset ?: 0),
                nextText = "Waarschuwingssein over ${FlagLogic.mmss(pendingIn)} · ${set.className(pendingOffset ?: 0)}",
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

    /**
     * Plant een start op de ESP (procedure start om [press]). De ESP voert hem zelf uit,
     * ook als de app of de verbinding wegvalt. Geeft een foutmelding terug, of null.
     */
    private suspend fun planStart(press: Long): String? {
        if (client.status.value?.timeSynced != true) client.syncClock()
        client.silentTargets.add(press)
        val ack = client.command("sched_add") {
            put("press_epoch", press)
            put("target_epoch", press)
            put("kind", 1)
        }
        if (!ack.ok) client.silentTargets.remove(press)
        return if (ack.ok) null else ack.error ?: "Kon de start niet plannen."
    }

    /** START: 10 seconden aftellen, daarna start de timer met het waarschuwingssein. */
    fun startWithCountdown() = action {
        val s = client.status.value
        if (s == null) {
            message("Geen verbinding met de timer.")
            return@action
        }
        if (s.running) {
            message("De procedure loopt al.")
            return@action
        }
        if (!s.ledPanel) {
            message("LED-paneel staat uit. Zet het eerst aan.")
            return@action
        }
        val phoneNow = System.currentTimeMillis()
        val pending = nextPress(s, phoneNow)
        if (pending != null && pending - phoneNow < 15_000L) {
            message("De start is al ingezet.")
            return@action
        }
        val press = phoneNow + COUNTDOWN_MS
        val err = planStart(press)
        if (err != null) message(err)
    }

    /** START in de stand "Direct": de timer start meteen met het waarschuwingssein. */
    fun startDirect() = action {
        val s = client.status.value
        if (s == null) {
            message("Geen verbinding met de timer.")
            return@action
        }
        val ack = client.command("start")
        if (!ack.ok) message(ack.error ?: "Starten mislukt.")
    }

    /** START volgens de gekozen stand (10 s aftellen of direct). */
    fun start() {
        if (_settings.value.countdownStart) startWithCountdown() else startDirect()
    }

    fun setCountdownStart(on: Boolean) = updateSettings(_settings.value.copy(countdownStart = on))

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
        val press = System.currentTimeMillis() + 60_000L
        val err = planStart(press)
        if (err != null) {
            message(err)
            return@action
        }
        interrupt = Interrupt.NONE
        speak(if (was == Interrupt.POSTPONED) "Uitstelwimpel neer. Waarschuwingssein over één minuut."
              else "Eerste vervangende neer. Waarschuwingssein over één minuut.")
        signals(1)
    }

    /** Eerstvolgende geplande start annuleren (timer blijft stil). */
    fun cancelResume() = action {
        val s = client.status.value ?: return@action
        val press = nextPress(s, System.currentTimeMillis()) ?: return@action
        val sched = s.schedules.firstOrNull { it.pressEpoch == press } ?: return@action
        val ack = client.command("sched_del") { put("sched_id", sched.id) }
        if (!ack.ok) {
            message(ack.error ?: "Annuleren mislukt.")
            return@action
        }
        client.silentTargets.remove(press)
        pendingOffset = null
        speak("Start geannuleerd.")
    }

    companion object {
        private const val VOICE_LEAD_MS = 250L
        private const val COUNTDOWN_MS = 10_000L      // aftellen vóór het waarschuwingssein na START
        private const val PENDING_WINDOW_S = 600      // geplande start binnen 10 min: tonen op de vlaggenpagina
        private const val K_PREP = "flag_prep"
        private const val K_CLASSES = "flag_classes"
        private const val K_VOICE = "voice_on"
        private const val K_MIN = "voice_minutes"
        private const val K_10S = "voice_10s"
        private const val K_LAST10 = "voice_last10"
        private const val K_COUNTDOWN = "start_countdown"
        private const val K_CLASS_COLOR = "class_color"
    }
}
