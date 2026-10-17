package nl.julian.zztimer.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.julian.zztimer.EspStatus
import nl.julian.zztimer.Phase
import nl.julian.zztimer.Schedule
import nl.julian.zztimer.ScheduleInput
import nl.julian.zztimer.TimeFormat
import nl.julian.zztimer.TimerMath
import nl.julian.zztimer.TimerViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun MainScreen(vm: TimerViewModel, onOpenSettings: () -> Unit, bottomBar: @Composable () -> Unit = {}) {
    val status by vm.status.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPin by remember { mutableStateOf(false) }
    var scheduleEdit by remember { mutableStateOf<ScheduleInput?>(null) }
    val scroll = rememberScrollState()
    val scroll2 = rememberScrollState()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            ScreenHeader("Timer", scrolled = scroll.value > 0 || scroll2.value > 0, actions = {
                HeaderIconButton(Icons.Rounded.Settings, "Instellingen") { showPin = true }
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
            val fullWidth = maxWidth
            val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scroll2)
                            .padding(Space.screen),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TimerHero(vm, status, connected, fullWidth / 2 - Space.screen * 2)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scroll)
                            .padding(Space.screen),
                        verticalArrangement = Arrangement.spacedBy(Space.xl),
                    ) {
                        Controls(vm, status, connected) { scheduleEdit = it }
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = Space.screen),
                    verticalArrangement = Arrangement.spacedBy(Space.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimerHero(vm, status, connected, fullWidth - Space.screen * 2)
                    Controls(vm, status, connected) { scheduleEdit = it }
                    Spacer(Modifier.height(Space.s))
                }
            }
        }
    }

    if (showPin) {
        PinDialog(
            vm = vm,
            onDismiss = { showPin = false },
            onUnlocked = {
                showPin = false
                onOpenSettings()
            },
        )
    }

    scheduleEdit?.let { input ->
        ScheduleDialog(
            vm = vm,
            initial = input,
            onDismiss = { scheduleEdit = null },
        )
    }
}

@Composable
private fun rememberTick(periodMs: Long = 50): Long {
    val tick by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(periodMs)
        }
    }
    return tick
}

/** Wedstrijdtijd groot in beeld, met verbinding, status, volgende vlag en telefoontijd. */
@Composable
private fun TimerHero(vm: TimerViewModel, status: EspStatus?, connected: Boolean, width: Dp) {
    rememberTick()
    val c = Zz.colors
    val phoneTime = TimeFormat.time(System.currentTimeMillis())
    val flagState by vm.flags.state.collectAsStateWithLifecycle()
    // Klok niet verbonden maar de vlaggen lopen op de telefoon: toon die tijd
    val race = if (flagState.local && (flagState.running || flagState.resuming)) flagState.timeText
               else TimerMath.raceText(status, vm.client.espNow())
    val digitSize = with(LocalDensity.current) { (width / 3.1f).toSp() }
    val panelOff = status != null && !status.ledPanel
    val dim = !connected || panelOff
    // Gedimd (geen verbinding of paneel uit) blijft leesbaar: secundaire tekstkleur, 5,1:1
    val digitColor by animateColorAsState(if (dim) c.secondaryLabel else c.label, tween(Motion.STANDARD), label = "cijfers")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = Space.s),
    ) {
        StatusPill(
            if (connected) "Verbonden met de timer" else "Geen verbinding",
            if (connected) c.positive else c.destructive,
        )
        Spacer(Modifier.height(Space.l))
        Text("WEDSTRIJDTIJD", style = ZzType.caption, color = c.secondaryLabel)
        Text(
            race,
            style = ZzType.digits.copy(fontSize = if (digitSize.value > 140f) 140.sp else digitSize, letterSpacing = (-2).sp),
            color = digitColor,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "Wedstrijdtijd $race" },
        )
        Text(
            text = when {
                status == null -> "Wachten op de timer…"
                panelOff -> "LED-paneel staat uit"
                flagState.phase == Phase.RESUMING -> "Waarschuwingssein over ${flagState.timeText}"
                status.running && status.repeat -> "Procedure loopt · ${TimerMath.procLabel(status.procS)} doorstart"
                status.running -> "Procedure loopt · één keer"
                else -> "Gereed"
            },
            style = ZzType.callout,
            color = c.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        flagState.upcoming.firstOrNull()?.let {
            Spacer(Modifier.height(Space.m))
            UpcomingLine(it)
        }
        Spacer(Modifier.height(Space.m))
        Text(
            "Telefoontijd  $phoneTime",
            style = ZzType.footnote.tabular().copy(letterSpacing = 0.sp),
            color = c.secondaryLabel,
        )
        AnimatedVisibility(visible = !connected, enter = fadeIn(tween(Motion.STANDARD)), exit = fadeOut(tween(Motion.STANDARD))) {
            Footnote(
                "Verbind de telefoon met het WiFi-netwerk van de timer. De timer zelf loopt gewoon door.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.s, start = Space.l, end = Space.l),
            )
        }
    }
}

@Composable
private fun Controls(
    vm: TimerViewModel,
    status: EspStatus?,
    connected: Boolean,
    onEditSchedule: (ScheduleInput) -> Unit,
) {
    val c = Zz.colors
    val running = status?.running == true
    val panelOn = status?.ledPanel != false

    StartControls(vm, connected)

    // Modus: wordt bepaald door de schakelaar op de timer (open = 5 min, dicht = 3 min doorstart)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Section(
            header = "Timer",
            footer = if (running) "Modus van de lopende procedure. Wijzigen met de schakelaar op de timer (geldt bij de volgende start)."
                     else "Modus volgt de schakelaar op de timer (open = 5 min, dicht = 3 min). Wijzigen kan alleen met die schakelaar.",
        ) {
            ValueRow(
                "Modus",
                when (status?.procS) {
                    TimerMath.PROCEDURE_S -> "5 min doorstart"
                    TimerMath.SHORT_S -> "3 min doorstart"
                    else -> "–"
                },
            )
            RowDivider()
            ListRow("LED-paneel", trailing = {
                StatusValue(panelOn && status != null, "Aan", "Uit", c.positive)
                Spacer(Modifier.width(Space.m))
                ZzButton(
                    if (panelOn) "Zet uit" else "Zet aan",
                    onClick = { vm.setLedPanel(!panelOn) },
                    style = ButtonStyle.Gray,
                    enabled = connected && status != null && (!panelOn || !running),
                )
            })
            RowDivider()
            ListRow(
                "Toeter",
                subtitle = if (status?.manualAllowed == true) "Handmatig toegestaan" else "Handmatig geblokkeerd",
                trailing = { StatusValue(status?.horn == true, "Klinkt", "Uit", c.warning) },
            )
        }
        HornButton(
            enabled = connected && status?.manualAllowed == true,
            onPress = vm::hornPressed,
            onRelease = vm::hornReleased,
        )
    }

    ScheduleSection(vm, status, connected, onEditSchedule)
}

/** Lijst met geplande starts; op de Timer- en de Vlaggenpagina. */
@Composable
internal fun ScheduleSection(
    vm: TimerViewModel,
    status: EspStatus?,
    connected: Boolean,
    onEditSchedule: (ScheduleInput) -> Unit,
) {
    val c = Zz.colors
    // Starts die de app zelf plant (10 s aftellen, hervatten) staan bij de START-knop
    val phone by vm.flags.phoneSchedules.collectAsStateWithLifecycle()
    val onClock = status?.schedules.orEmpty().filter { it.pressEpoch !in vm.client.silentTargets }
    val now = System.currentTimeMillis()
    val schedules = (onClock.map { it to true } + phone.map { it to false })
        .filter { it.first.pressEpoch >= now - 2000 }
        .sortedBy { it.first.pressEpoch }

    Section(
        header = "Geplande starts",
        footer = "Een start op de klok gaat ook door als de app dicht is.",
    ) {
        if (!connected) {
            Footnote(
                "Klok niet verbonden: nieuwe starts worden alleen op de telefoon gepland.",
                color = c.destructive,
                modifier = Modifier.padding(SectionPadding),
            )
            RowDivider()
        }
        if (connected && status != null && !status.timeSynced) {
            Footnote(
                "De klok van de timer wordt gesynchroniseerd met deze telefoon…",
                modifier = Modifier.padding(SectionPadding),
            )
            RowDivider()
        }
        if (schedules.isEmpty()) {
            Text(
                "Geen geplande starts",
                style = ZzType.body,
                color = c.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.l, horizontal = Space.l),
            )
            RowDivider()
        }
        schedules.forEach { (s, clock) ->
            ScheduleRow(
                s,
                onClock = clock,
                connected = connected,
                enabled = if (clock) connected else true,
                onEdit = {
                    val t = Instant.ofEpochMilli(s.targetEpoch).atZone(ZoneId.systemDefault()).toLocalTime()
                    onEditSchedule(ScheduleInput(s.id, t.hour, t.minute, s.kind, s.procS))
                },
                onDelete = { vm.deleteSchedule(s.id) },
                onMoveToClock = { vm.flags.moveToClock(s) },
            )
            RowDivider()
        }
        ListRow(
            "Geplande start toevoegen",
            subtitle = if (connected) "Op de klok" else "Alleen op de telefoon",
            titleColor = c.accent,
            onClick = {
                val t = LocalTime.now().plusMinutes(10)
                onEditSchedule(ScheduleInput(null, t.hour, t.minute, 0, status?.switchProcS ?: vm.flags.switchProc))
            },
            leading = { Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(24.dp)) },
        )
    }
}

/** Handmatige toeter: klinkt zolang de knop wordt ingedrukt (zelfde regels als de fysieke knop). */
@Composable
private fun HornButton(enabled: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val c = Zz.colors
    var pressed by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            !enabled -> c.fill
            pressed -> c.warningFill
            else -> c.warning.copy(alpha = if (c.isDark) 0.22f else 0.12f)
        },
        tween(Motion.FAST), label = "toeter",
    )
    val fg = when {
        !enabled -> c.secondaryLabel
        pressed -> c.onWarningFill
        else -> c.warningText
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = fg,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (enabled) "Toeter, ingedrukt houden" else "Toeter, handmatig geblokkeerd"
                if (enabled) {
                    // Alternatief voor ingedrukt houden (bijv. met TalkBack): toeter 1 seconde
                    onClick(label = "Toeter 1 seconde laten klinken") {
                        scope.launch {
                            pressed = true
                            onPress()
                            try {
                                delay(1000)
                            } finally {
                                pressed = false
                                onRelease()
                            }
                        }
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onPress()
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            onRelease()
                        }
                    },
                )
            },
    ) {
        Column(
            Modifier.padding(vertical = Space.m, horizontal = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (pressed) "Toeter klinkt" else "Toeter", style = ZzType.headline)
            Text(
                if (enabled) "Ingedrukt houden" else "Alleen in rust en in de eerste minuut",
                style = ZzType.footnote,
                color = fg,
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    s: Schedule,
    onClock: Boolean,
    connected: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveToClock: () -> Unit,
) {
    val c = Zz.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.l, end = Space.xs, top = Space.m, bottom = Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(hm(s.targetEpoch), style = ZzType.title.tabular(), color = c.label)
                Spacer(Modifier.width(Space.s))
                Text(
                    "${TimeFormat.dayLabel(s.targetEpoch)} · ${TimerMath.procLabel(s.procS)}",
                    style = ZzType.footnote,
                    color = c.secondaryLabel,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                if (s.kind == 0) "Startschot · procedure start ${hm(s.pressEpoch)}"
                else "Procedure start · startschot ${hm(s.pressEpoch + s.procS * 1000L)}",
                style = ZzType.footnote,
                color = c.secondaryLabel,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (onClock) c.positive else c.destructive),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        onClock && connected -> "Op de klok · start zelf"
                        onClock -> "Op de klok · start zelf (laatst bekend)"
                        else -> "Alleen telefoon · druk zelf START van de klok"
                    },
                    style = ZzType.footnote.copy(fontWeight = FontWeight.Medium),
                    color = if (onClock) c.positive else c.destructive,
                )
            }
            if (!onClock && connected) {
                ZzButton(
                    "Op klok zetten",
                    onClick = onMoveToClock,
                    style = ButtonStyle.Tinted,
                    modifier = Modifier.padding(top = Space.s),
                )
            }
            val until = s.pressEpoch - System.currentTimeMillis()
            if (until in 0..3_600_000L) {
                Text(
                    "Waarschuwingssein over ${TimerMath.format(((until + 999) / 1000).toInt())}",
                    style = ZzType.footnote.tabular().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                    color = c.warning,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(Icons.Rounded.Edit, contentDescription = "Start van ${hm(s.targetEpoch)} aanpassen", tint = if (enabled) c.secondaryLabel else c.tertiaryLabel)
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(Icons.Rounded.Delete, contentDescription = "Start van ${hm(s.targetEpoch)} verwijderen", tint = if (enabled) c.secondaryLabel else c.tertiaryLabel)
        }
    }
}

/** Tijd zonder seconden (geplande starts vallen altijd op een hele minuut). */
private fun hm(epochMs: Long): String = TimeFormat.time(epochMs).substringBeforeLast(':')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDialog(vm: TimerViewModel, initial: ScheduleInput, onDismiss: () -> Unit) {
    val c = Zz.colors
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var kind by remember { mutableIntStateOf(initial.kind) }
    var procS by remember { mutableIntStateOf(TimerMath.proc(initial.procS)) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ZzDialog(
        title = if (initial.id == null) "Geplande start" else "Geplande start aanpassen",
        onDismiss = onDismiss,
        confirmText = "Opslaan",
        confirmEnabled = !busy,
        onConfirm = {
            busy = true
            scope.launch {
                val err = vm.saveSchedule(initial.copy(hour = picker.hour, minute = picker.minute, kind = kind, procS = procS))
                busy = false
                if (err == null) onDismiss() else error = err
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimeInput(
                    state = picker,
                    colors = TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = c.accent.copy(alpha = if (c.isDark) 0.3f else 0.14f),
                        timeSelectorUnselectedContainerColor = c.fill,
                        timeSelectorSelectedContentColor = c.accent,
                        timeSelectorUnselectedContentColor = c.label,
                    ),
                )
            }
            SegmentedControl(
                options = listOf("5 min doorstart", "3 min doorstart"),
                selected = if (procS == TimerMath.SHORT_S) 1 else 0,
                onSelect = { procS = if (it == 1) TimerMath.SHORT_S else TimerMath.PROCEDURE_S },
            )
            val min = procS / 60
            Column(Modifier.selectableGroup()) {
                KindOption("Startschot (0:00) om dit tijdstip", "Procedure begint $min minuten eerder", kind == 0) { kind = 0 }
                KindOption("Procedure start om dit tijdstip", "Startschot valt $min minuten later", kind == 1) { kind = 1 }
            }
            error?.let { Text(it, style = ZzType.footnote, color = c.destructive) }
        }
    }
}

@Composable
private fun KindOption(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    val c = Zz.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.small))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.tertiaryLabel),
        )
        Spacer(Modifier.width(Space.m))
        Column {
            Text(title, style = ZzType.body, color = c.label)
            Text(subtitle, style = ZzType.footnote, color = c.secondaryLabel)
        }
    }
}

@Composable
private fun PinDialog(vm: TimerViewModel, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ZzDialog(
        title = "Beheerders-PIN",
        onDismiss = onDismiss,
        confirmText = "Openen",
        confirmEnabled = !busy && pin.length >= 4,
        onConfirm = {
            busy = true
            scope.launch {
                val err = vm.unlockSettings(pin)
                busy = false
                if (err == null) onUnlocked() else error = err
            }
        },
    ) {
        Text("Voer de PIN in om de instellingen te openen.", style = ZzType.callout, color = Zz.colors.secondaryLabel)
        ZzTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) },
            label = "PIN",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        error?.let { Text(it, style = ZzType.footnote, color = Zz.colors.destructive) }
    }
}
