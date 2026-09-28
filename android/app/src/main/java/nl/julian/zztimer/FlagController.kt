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
 * voert terugroepen en uitstel uit. Verbonden komt de tijd van de klok (ESP), zodat vlaggen,
 * stem, display en hoorn gelijk lopen. Zonder verbinding telt de telefoon zelf en geeft aan
 * wanneer START of RESET op de klok moet worden gedrukt.
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
    private var xSinceTl = 0L                // tijdlijn-tijd waarop de X-vlag op ging
    private var lastStartTl: Long? = null    // tijdlijn-tijd van de laatste start (0:00)
    private var lastK = -1L
    private var wasRunning = false
    private var classOffset = 0              // eerste klasse van de lopende reeks
    private var pendingOffset: Int? = null   // klasse waarmee de volgende reeks begint
    private var lastPreSpoken = Int.MIN_VALUE  // laatst uitgesproken seconde vóór een geplande start
    private var warningSpokenAt = 0L         // telefoontijd waarop het waarschuwingssein al is uitgesproken
    private var busy = false

    fun startTicker() {
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

    // ------------------------------------------------------------------ tijdlijn

    /**
     * Tijdlijn waar de vlaggen op lopen. Verbonden: de klok (ESP) is leidend. Niet
     * verbonden: de telefoon telt zelf (telefoontijd, ms) en geeft aan wanneer START of
     * RESET op de klok moet worden gedrukt.
     */
    private var local = false
    private var localStart: Long? = null          // telefoontijd van START (5:00)
    private var localRepeat = false
    private var localPress: Long? = null          // telefoontijd van een door de app ingezette start
    private var localPressManual = false          // bij die start moet START van de klok worden gedrukt
    private var knownSchedules: List<Schedule> = emptyList()
    private var doneSchedules = mutableSetOf<Long>()
    private var banner: String? = null
    private var bannerUrgent = false
    private var bannerUntil = 0L

    // laatste waarden, voor de acties
    private var curNow = 0L
    private var curStart = 0L
    private var curRunning = false

    private fun showBanner(text: String, urgent: Boolean, ms: Long) {
        banner = text
        bannerUrgent = urgent
        bannerUntil = System.currentTimeMillis() + ms
    }

    // ------------------------------------------------------------------ elke 50 ms

    private fun tick() {
        val s = client.status.value
        val espNow = client.espNow()
        val set = _settings.value
        val phoneNow = System.currentTimeMillis()
        val conn = client.connected.value && s != null && espNow != null

        if (s != null) {
            knownSchedules = s.schedules
            if (!local) localRepeat = if (s.running) s.repeat else s.switchRepeat
        }

        // Wisselen tussen klok en telefoon
        if (conn && local) {
            local = false
            if (localStart != null && s?.running != true) {
                scope.launch { message("Klok weer verbonden, maar de klok loopt niet. Vlaggen volgen nu de klok.") }
            }
            localStart = null
            localPress = null
            lastStartTl = null
            lastK = -1
            wasRunning = s?.running == true
        } else if (!conn && !local) {
            local = true
            // Liep de klok? Dan loopt de telefoon naadloos verder vanaf dezelfde tijd.
            val delta = if (espNow != null) phoneNow - espNow else 0L
            localStart = if (s?.running == true && espNow != null) s.startMs + delta else null
            lastStartTl = lastStartTl?.let { it + delta }
            if (interrupt == Interrupt.INDIVIDUAL_RECALL) xSinceTl += delta
            lastK = -1
            doneSchedules.clear()
        }

        // Tijdlijn
        val now: Long
        val running: Boolean
        val startMs: Long
        val repeat: Boolean
        val ledPanel: Boolean
        if (!local) {
            now = espNow!!
            running = s!!.running
            startMs = s.startMs
            repeat = s.repeat
            ledPanel = s.ledPanel
        } else {
            now = phoneNow
            // Door de app ingezette start of geplande start bereikt?
            val lp = localPress
            if (localStart == null && lp != null && phoneNow >= lp) {
                localStart = lp
                localPress = null
                if (localPressManual) showBanner("DRUK NU OP START VAN DE KLOK", true, 4000)
            }
            if (localStart == null) {
                knownSchedules.firstOrNull { it.pressEpoch <= phoneNow && it.pressEpoch >= phoneNow - 2000 && it.pressEpoch !in doneSchedules }?.let {
                    doneSchedules += it.pressEpoch
                    localStart = it.pressEpoch
                    showBanner("Geplande start: de klok start zelf", false, 4000)
                }
            }
            // Eenmalige procedure afgelopen
            val ls = localStart
            if (ls != null && !localRepeat && now - ls >= 301_000L) {
                lastStartTl = ls + 300_000L
                localStart = null
            }
            running = localStart != null
            startMs = localStart ?: 0L
            repeat = localRepeat
            ledPanel = true
        }
        curNow = now
        curStart = startMs
        curRunning = running

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
            val elapsed = now - startMs
            k = Math.floorDiv(elapsed, 1000L)
            if (k >= 300) lastStartTl = startMs + (k / 300) * 300_000L

            // Aankondigingen: iets vóór de seconde, zodat de stem gelijk valt met het display
            val kl = Math.floorDiv(elapsed + VOICE_LEAD_MS, 1000L)
            if (kl != lastK) {
                if (lastK == -1L && kl <= 1L) {
                    // Waarschuwingssein al uitgesproken aan het eind van het aftellen? Dan niet herhalen.
                    val alreadySpoken = phoneNow - warningSpokenAt < 3000
                    for (kk in 0L..kl) {
                        if (kk == 0L && alreadySpoken) continue
                        FlagLogic.announcement(kk, repeat, set, classOffset)?.let { speak(it) }
                    }
                } else if (kl == lastK + 1) {
                    FlagLogic.announcement(kl, repeat, set, classOffset)?.let { speak(it) }
                }
                lastK = kl
            }
        }

        // X-vlag gaat uiterlijk na 4 minuten neer
        if (interrupt == Interrupt.INDIVIDUAL_RECALL && now - xSinceTl >= FlagLogic.X_MAX_MS) {
            interrupt = Interrupt.NONE
            speak("X-vlag neer.")
        }

        // Aftellen tot een start die eraan komt (START met aftellen, hervatten, geplande start)
        var pendingIn: Int? = null
        var manual = false
        if (!running) {
            val press = pendingPress(phoneNow)
            if (press != null) {
                manual = local && press == localPress && localPressManual
                pendingIn = ceilDiv(press - phoneNow, 1000L).toInt()
                if (pendingIn > PENDING_WINDOW_S) pendingIn = null
                val left = ceilDiv(press - (phoneNow + VOICE_LEAD_MS), 1000L).toInt()
                if (left != lastPreSpoken) {
                    when {
                        left == 30 && set.every10s -> speak("Waarschuwingssein over dertig seconden.")
                        left in 1..10 && (set.countdownStart || manual) -> speak(FlagLogic.countWord(left))
                        left == 0 && lastPreSpoken in 1..3 -> {
                            val w = FlagLogic.warningText(set.className(pendingOffset ?: 0))
                            speak(if (manual) "Druk nu op start. $w" else w)
                            warningSpokenAt = phoneNow
                        }
                    }
                    lastPreSpoken = left
                }
                if (manual && pendingIn in 0..10) {
                    banner = "Druk bij 0 op START van de klok"
                    bannerUrgent = false
                    bannerUntil = phoneNow + 1000
                }
            } else {
                lastPreSpoken = Int.MIN_VALUE
            }
        }

        if (phoneNow > bannerUntil) banner = null
        _state.value = buildState(now, running, k, repeat, ledPanel, set, pendingIn)
    }

    /** Eerstvolgende start die eraan komt (telefoontijd), of null. */
    private fun pendingPress(phoneNow: Long): Long? {
        val fromSchedules = knownSchedules.asSequence().map { it.pressEpoch }
            .filter { it >= phoneNow - 1500 && !(local && it in doneSchedules) }.minOrNull()
        val lp = if (local) localPress else null
        return listOfNotNull(fromSchedules, lp).minOrNull()
    }

    private fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)

    private fun buildState(
        now: Long, running: Boolean, k: Long, repeat: Boolean, ledPanel: Boolean,
        set: FlagSettings, pendingIn: Int?,
    ): FlagState {
        val since = lastStartTl?.let { now - it }
        val justStarted = since != null && since in 0..FlagLogic.X_MAX_MS
        val base = FlagState(
            interrupt = interrupt,
            running = running,
            local = local,
            banner = banner,
            bannerUrgent = bannerUrgent,
            canStart = !running && ledPanel && interrupt != Interrupt.POSTPONED && interrupt != Interrupt.GENERAL_RECALL &&
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
        if (local) return                     // geen verbinding: de gebruiker geeft zelf het geluidssein
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
        val phoneNow = System.currentTimeMillis()
        if (curRunning) {
            message("De procedure loopt al.")
            return@action
        }
        val pending = pendingPress(phoneNow)
        if (pending != null && pending - phoneNow < 15_000L) {
            message("De start is al ingezet.")
            return@action
        }
        if (local) {
            localPress = phoneNow + COUNTDOWN_MS
            localPressManual = true
            return@action
        }
        val s = client.status.value ?: return@action
        if (!s.ledPanel) {
            message("LED-paneel staat uit. Zet het eerst aan.")
            return@action
        }
        val err = planStart(phoneNow + COUNTDOWN_MS)
        if (err != null) message(err)
    }

    /** START in de stand "Direct": de timer start meteen met het waarschuwingssein. */
    fun startDirect() = action {
        if (curRunning) {
            message("De procedure loopt al.")
            return@action
        }
        if (local) {
            val phoneNow = System.currentTimeMillis()
            localStart = phoneNow
            localPress = null
            showBanner("DRUK NU OP START VAN DE KLOK", true, 4000)
            speak("Druk nu op start. " + FlagLogic.warningText(_settings.value.className(pendingOffset ?: 0)))
            warningSpokenAt = phoneNow
            return@action
        }
        val ack = client.command("start")
        if (!ack.ok) message(ack.error ?: "Starten mislukt.")
    }

    /** START volgens de gekozen stand (10 s aftellen of direct). */
    fun start() {
        if (_settings.value.countdownStart) startWithCountdown() else startDirect()
    }

    /** STOP / RESET. Zonder verbinding stopt de telefoon en moet RESET op de klok. */
    fun reset() = action {
        if (local) {
            localStart = null
            localPress = null
            showBanner("DRUK OP RESET VAN DE KLOK", true, 5000)
            return@action
        }
        val ack = client.command("reset")
        if (!ack.ok) message(ack.error ?: "Reset mislukt.")
    }

    fun setCountdownStart(on: Boolean) = updateSettings(_settings.value.copy(countdownStart = on))

    /** X-vlag op met één geluidssein (RvW 29.1). */
    fun individualRecall() = action {
        interrupt = Interrupt.INDIVIDUAL_RECALL
        xSinceTl = curNow
        speak("Individuele terugroep. X-vlag op.")
        if (local) showBanner("Geef zelf 1 geluidssein", true, 5000)
        signals(1)
    }

    fun lowerX() {
        if (interrupt != Interrupt.INDIVIDUAL_RECALL) return
        interrupt = Interrupt.NONE
        speak("X-vlag neer.")
    }

    /** Reset van de procedure bij terugroep/uitstel: klok via de app, of met de hand. */
    private suspend fun stopProcedure(): Boolean {
        if (local) {
            localStart = null
            localPress = null
            return true
        }
        val ack = client.command("reset")
        if (!ack.ok) message(ack.error ?: "Timer niet bereikbaar.")
        return ack.ok
    }

    /** Eerste vervangende op met twee geluidsseinen (RvW 29.2). De timer stopt. */
    fun generalRecall() = action {
        val startTl = lastStartTl ?: return@action
        val recalledCycle = ((startTl - curStart) / 300_000L - 1).toInt().coerceAtLeast(0)
        pendingOffset = classOffset + if (curRunning) recalledCycle else 0
        if (!stopProcedure()) return@action
        interrupt = Interrupt.GENERAL_RECALL
        lastStartTl = null
        speak("Algemene terugroep. Eerste vervangende op.")
        if (local) showBanner("DRUK OP RESET VAN DE KLOK · geef zelf 2 geluidsseinen", true, 6000)
        signals(2)
    }

    /** Uitstelwimpel op met twee geluidsseinen. De timer stopt. */
    fun postpone() = action {
        pendingOffset = if (curRunning) classOffset + FlagLogic.cycleForK(Math.floorDiv(curNow - curStart, 1000L))
                        else pendingOffset ?: 0
        if (!stopProcedure()) return@action
        interrupt = Interrupt.POSTPONED
        lastStartTl = null
        speak("Uitstel. Uitstelwimpel op.")
        if (local) showBanner("DRUK OP RESET VAN DE KLOK · geef zelf 2 geluidsseinen", true, 6000)
        signals(2)
    }

    /**
     * Uitstelwimpel of eerste vervangende neer met één geluidssein. Het waarschuwingssein
     * volgt één minuut later: verbonden plant de klok het zelf, anders telt de telefoon af
     * en geeft aan wanneer START van de klok moet worden gedrukt.
     */
    fun resume() = action {
        val was = interrupt
        if (was != Interrupt.POSTPONED && was != Interrupt.GENERAL_RECALL) return@action
        val press = System.currentTimeMillis() + 60_000L
        if (local) {
            localPress = press
            localPressManual = true
            showBanner("Geef zelf 1 geluidssein", true, 5000)
        } else {
            val err = planStart(press)
            if (err != null) {
                message(err)
                return@action
            }
        }
        interrupt = Interrupt.NONE
        speak(if (was == Interrupt.POSTPONED) "Uitstelwimpel neer. Waarschuwingssein over één minuut."
              else "Eerste vervangende neer. Waarschuwingssein over één minuut.")
        signals(1)
    }

    /** Eerstvolgende start annuleren (timer blijft stil). */
    fun cancelResume() = action {
        val phoneNow = System.currentTimeMillis()
        val press = pendingPress(phoneNow) ?: return@action
        if (local) {
            if (press == localPress) localPress = null else doneSchedules += press
            pendingOffset = null
            speak("Start geannuleerd.")
            return@action
        }
        val s = client.status.value ?: return@action
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
