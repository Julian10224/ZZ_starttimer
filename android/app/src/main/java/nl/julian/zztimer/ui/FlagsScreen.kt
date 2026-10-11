package nl.julian.zztimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.julian.zztimer.ClassColor
import nl.julian.zztimer.EspStatus
import nl.julian.zztimer.FlagKind
import nl.julian.zztimer.FlagSettings
import nl.julian.zztimer.FlagState
import nl.julian.zztimer.Interrupt
import nl.julian.zztimer.Phase
import nl.julian.zztimer.PrepFlag
import nl.julian.zztimer.ScheduleInput
import nl.julian.zztimer.ShownFlag
import nl.julian.zztimer.TimerMath
import nl.julian.zztimer.TimerViewModel
import nl.julian.zztimer.kind

private enum class Confirm(val title: String, val text: String) {
    INDIVIDUAL("Individuele terugroep?", "X-vlag op met één geluidssein. De vlag gaat uiterlijk na 4 minuten vanzelf neer."),
    GENERAL("Algemene terugroep?", "Eerste vervangende op met twee geluidsseinen. De timer stopt; na het neerhalen volgt het waarschuwingssein 1 minuut later."),
    POSTPONE("Uitstel?", "Uitstelwimpel op met twee geluidsseinen. De timer stopt; na het neerhalen volgt het waarschuwingssein 1 minuut later."),
}

@Composable
fun FlagsScreen(vm: TimerViewModel, bottomBar: @Composable () -> Unit) {
    val state by vm.flags.state.collectAsStateWithLifecycle()
    val settings by vm.flags.settings.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var scheduleEdit by remember { mutableStateOf<ScheduleInput?>(null) }
    val status by vm.status.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val scroll2 = rememberScrollState()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            ScreenHeader("Vlaggen", scrolled = scroll.value > 0 || scroll2.value > 0, actions = {
                HeaderIconButton(Icons.Rounded.Settings, "Instellingen vlaggen en stem") { showSettings = true }
            })
        },
        bottomBar = bottomBar,
        snackbarHost = { ZzSnackbarHost(snackbar) },
        containerColor = Zz.colors.background,
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll2).padding(Space.screen),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.l),
                    ) { FlagDisplay(state) }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).padding(Space.screen),
                        verticalArrangement = Arrangement.spacedBy(Space.xl),
                    ) { FlagControls(vm, state, settings, connected, status, { confirm = it }, { scheduleEdit = it }) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = Space.screen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.l),
                ) {
                    FlagDisplay(state)
                    Spacer(Modifier.height(Space.xs))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                        FlagControls(vm, state, settings, connected, status, { confirm = it }, { scheduleEdit = it })
                    }
                    Spacer(Modifier.height(Space.s))
                }
            }
        }
    }

    confirm?.let { c ->
        ZzDialog(
            title = c.title,
            onDismiss = { confirm = null },
            confirmText = "Uitvoeren",
            destructive = true,
            onConfirm = {
                when (c) {
                    Confirm.INDIVIDUAL -> vm.flags.individualRecall()
                    Confirm.GENERAL -> vm.flags.generalRecall()
                    Confirm.POSTPONE -> vm.flags.postpone()
                }
                confirm = null
            },
        ) {
            Text(c.text, style = ZzType.callout, color = Zz.colors.secondaryLabel)
        }
    }

    scheduleEdit?.let { input ->
        ScheduleDialog(vm = vm, initial = input, onDismiss = { scheduleEdit = null })
    }

    if (showSettings) {
        FlagSettingsDialog(
            vm = vm,
            initial = settings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun phaseColor(p: Phase): Color {
    val c = Zz.colors
    return when (p) {
        Phase.WARNING, Phase.PREPARATORY -> c.accent
        Phase.LAST_MINUTE, Phase.RESUMING -> c.warning
        Phase.STARTED -> c.positive
        Phase.POSTPONED, Phase.GENERAL_RECALL -> c.destructive
        Phase.IDLE, Phase.NO_CONNECTION -> c.secondaryLabel
    }
}

@Composable
private fun ColumnScope.FlagDisplay(state: FlagState) {
    val c = Zz.colors

    // Fase en tijd
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = Space.s)) {
        StatusPill(state.phase.label, phaseColor(state.phase))
        Text(
            state.timeText,
            style = ZzType.digits.copy(fontSize = 76.sp, letterSpacing = (-1.5).sp),
            color = c.label,
            modifier = Modifier.semantics { contentDescription = "Tijd ${state.timeText}" },
        )
        val sub = buildString {
            if (state.startNumber > 0) append("Start ${state.startNumber}")
            if (state.className.isNotEmpty() && state.running) {
                if (isNotEmpty()) append(" · ")
                append(state.className)
            }
        }
        if (sub.isNotEmpty()) Text(sub, style = ZzType.callout, color = c.secondaryLabel)
    }

    AnimatedVisibility(
        visible = state.local,
        enter = fadeIn(tween(Motion.STANDARD)) + expandVertically(tween(Motion.STANDARD)),
        exit = fadeOut(tween(Motion.STANDARD)) + shrinkVertically(tween(Motion.STANDARD)),
    ) {
        NoticeCard(
            "Klok niet verbonden",
            "De vlaggen lopen op de telefoon. Druk START of RESET van de klok op het aangegeven moment; geluidsseinen geef je zelf.",
            c.destructive,
        )
    }
    state.banner?.let { InstructionBanner(it, state.bannerUrgent) }

    // Vlaggen die nu op staan
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        if (state.flags.isEmpty()) NoFlags(112.dp)
        state.flags.forEach { FlagCard(it, 112.dp) }
    }

    // Welke vlag er als eerste op/neer gaat, en die daarna (zoals in Start Timer)
    if (state.upcoming.isNotEmpty()) UpcomingFlags(state.upcoming)
    else state.nextText?.let {
        Text("Volgende: $it", style = ZzType.bodyEmphasis, color = c.label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FlagControls(
    vm: TimerViewModel,
    state: FlagState,
    settings: FlagSettings,
    connected: Boolean,
    status: EspStatus?,
    onConfirm: (Confirm) -> Unit,
    onEditSchedule: (ScheduleInput) -> Unit,
) {
    val c = Zz.colors

    // Start / stop met de keuze 10 s aftellen of direct (gelijk aan de Timer-pagina)
    StartControls(vm, connected)

    // Onderbrekingen
    when (state.interrupt) {
        Interrupt.POSTPONED, Interrupt.GENERAL_RECALL -> Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SectionHeader("Seinen")
            ZzButton(
                if (state.interrupt == Interrupt.POSTPONED) "Uitstelwimpel neer" else "Eerste vervangende neer",
                onClick = vm.flags::resume,
                enabled = state.canResume,
                color = c.warning,
                large = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Footnote(
                "1 geluidssein; de timer geeft 1 minuut later zelf het waarschuwingssein.",
                modifier = Modifier.padding(horizontal = Space.l),
            )
        }
        else -> Section(header = "Seinen", footer = "Terugroepen kan tot 4 minuten na een start.") {
            if (state.interrupt == Interrupt.INDIVIDUAL_RECALL) {
                ActionRow("X-vlag neer (alle boten terug)", vm.flags::lowerX)
                RowDivider()
            }
            SignalRow(ShownFlag(FlagKind.X, "X-vlag"), "Individuele terugroep", "X-vlag, 1 geluidssein", state.canIndividualRecall) {
                onConfirm(Confirm.INDIVIDUAL)
            }
            RowDivider(inset = 72.dp)
            SignalRow(ShownFlag(FlagKind.FIRST_SUBSTITUTE, "Eerste vervangende"), "Algemene terugroep", "Eerste vervangende, 2 geluidsseinen", state.canGeneralRecall) {
                onConfirm(Confirm.GENERAL)
            }
            RowDivider(inset = 72.dp)
            SignalRow(ShownFlag(FlagKind.AP, "Uitstelwimpel"), "Uitstel", "Uitstelwimpel, 2 geluidsseinen", state.canPostpone) {
                onConfirm(Confirm.POSTPONE)
            }
        }
    }

    // Verloop volgens regel 26
    Section(header = if (state.procS == TimerMath.SHORT_S) "Verloop · 3 min doorstart" else "Verloop · regel 26") {
        // Index = timelineStep uit FlagLogic.runningFlags(); bij 3 minuten begint het verloop bij stap 1.
        val steps = if (state.procS == TimerMath.SHORT_S) listOf(
            Triple(1, "3:00", "Waarschuwings- en voorbereidingssein · klassevlag en ${settings.prep.label} op"),
            Triple(2, "1:00", "Eén minuut · ${settings.prep.label} neer"),
            Triple(3, "0:00", "Start · klassevlag neer"),
        ) else listOf(
            Triple(0, "5:00", "Waarschuwingssein · klassevlag op"),
            Triple(1, "4:00", "Voorbereidingssein · ${settings.prep.label} op"),
            Triple(2, "1:00", "Eén minuut · ${settings.prep.label} neer"),
            Triple(3, "0:00", "Start · klassevlag neer"),
        )
        steps.forEachIndexed { n, (i, t, text) ->
            if (n > 0) RowDivider(inset = 44.dp)
            TimelineRow(t, text, done = state.timelineStep > i, current = state.timelineStep == i)
        }
    }

    // Geplande starts (zelfde lijst als op de Timer-pagina)
    ScheduleSection(vm, status, connected, onEditSchedule)

    // Stem
    Section(header = "Stem") {
        SwitchRow(
            "Gesproken aftellen",
            checked = settings.voice,
            onChange = { vm.flags.updateSettings(settings.copy(voice = it)) },
            subtitle = when {
                !vm.flags.voice.dutchAvailable -> "Nederlandse stem niet beschikbaar op deze telefoon"
                settings.voice -> "Aan · Nederlandse stem"
                else -> "Uit"
            },
        )
    }
}

/** Sein met een kleine vlag ervoor; tik opent de bevestiging. */
@Composable
private fun SignalRow(flag: ShownFlag, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    ListRow(
        title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
        leading = {
            val h = when (flag.kind) {
                FlagKind.AP -> 13.dp
                FlagKind.FIRST_SUBSTITUTE -> 22.dp
                else -> 24.dp
            }
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { SignalFlag(flag, h) }
        },
    )
}

@Composable
private fun TimelineRow(time: String, text: String, done: Boolean, current: Boolean) {
    val c = Zz.colors
    val dot by animateColorAsState(
        when {
            current -> c.accent
            done -> c.positive
            else -> c.fill
        },
        tween(Motion.STANDARD), label = "stap",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (current) c.accent.copy(alpha = if (c.isDark) 0.14f else 0.06f) else Color.Transparent)
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Space.l))
        Text(
            time,
            style = ZzType.body.tabular().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            color = c.label,
            modifier = Modifier.widthIn(min = 48.dp),
        )
        Text(
            text,
            style = if (current) ZzType.bodyEmphasis else ZzType.body,
            color = if (current || done) c.label else c.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        if (done) {
            Icon(Icons.Rounded.Check, contentDescription = "Gedaan", tint = c.positive, modifier = Modifier.padding(start = Space.s).size(20.dp))
        }
    }
}

@Composable
private fun FlagSettingsDialog(vm: TimerViewModel, initial: FlagSettings, onDismiss: () -> Unit) {
    val c = Zz.colors
    var prep by remember { mutableStateOf(initial.prep) }
    var classes by remember { mutableStateOf(initial.classes.joinToString(", ")) }
    var everyMinute by remember { mutableStateOf(initial.everyMinute) }
    var every10s by remember { mutableStateOf(initial.every10s) }
    var lastTen by remember { mutableStateOf(initial.lastTen) }
    var classColor by remember { mutableStateOf(initial.classColor) }

    ZzDialog(
        title = "Vlaggen en stem",
        onDismiss = onDismiss,
        confirmText = "Opslaan",
        onConfirm = {
            vm.flags.updateSettings(
                initial.copy(
                    prep = prep,
                    classes = classes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Klasse") },
                    everyMinute = everyMinute,
                    every10s = every10s,
                    lastTen = lastTen,
                    classColor = classColor,
                )
            )
            onDismiss()
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            DialogLabel("Voorbereidingsvlag")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PrepFlag.entries.forEach { f ->
                    ChoicePill(f.label, prep == f, onClick = { prep = f }) {
                        SignalFlag(ShownFlag(f.kind(), f.label), 16.dp)
                    }
                }
            }
            DialogLabel("Klassen")
            ZzTextField(
                value = classes,
                onValueChange = { classes = it },
                label = "Bijv. Optimist, ILCA 7, Laser",
            )
            Footnote("Gescheiden door komma's. Bij doorstart krijgt elke start de volgende klasse.")
            DialogLabel("Kleur klassevlag")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ClassColor.entries.forEach { col ->
                    ChoicePill(col.label, classColor == col, onClick = { classColor = col }) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(c.separator)
                                .padding(1.dp)
                                .clip(CircleShape)
                                .background(Color(col.argb)),
                        )
                    }
                }
            }
            DialogLabel("Aankondigingen")
            DialogSwitch("Elke minuut", everyMinute) { everyMinute = it }
            DialogSwitch("Laatste minuut elke 10 seconden", every10s) { every10s = it }
            DialogSwitch("10 seconden aftellen vóór elk sein", lastTen) { lastTen = it }
            ZzButton("Stem testen", vm.flags::testVoice, style = ButtonStyle.Tinted, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DialogLabel(text: String) {
    Text(text, style = ZzType.bodyEmphasis, color = Zz.colors.label, modifier = Modifier.padding(top = Space.xs))
}

@Composable
private fun DialogSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.small))
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ZzType.body, color = Zz.colors.label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.s))
        ZzSwitch(checked = checked, onCheckedChange = onChange)
    }
}
