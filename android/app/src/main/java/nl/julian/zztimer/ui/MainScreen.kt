package nl.julian.zztimer.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import nl.julian.zztimer.Schedule
import nl.julian.zztimer.ScheduleInput
import nl.julian.zztimer.TimeFormat
import nl.julian.zztimer.TimerMath
import nl.julian.zztimer.TimerViewModel
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: TimerViewModel, onOpenSettings: () -> Unit, bottomBar: @Composable () -> Unit = {}) {
    val status by vm.status.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPin by remember { mutableStateOf(false) }
    var scheduleEdit by remember { mutableStateOf<ScheduleInput?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZZ Wedstrijd Timer", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showPin = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Instellingen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TimerPanel(vm, status, connected, fullWidth / 2 - 32.dp)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Controls(vm, status, connected) { scheduleEdit = it }
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimerPanel(vm, status, connected, fullWidth - 32.dp)
                    Controls(vm, status, connected) { scheduleEdit = it }
                    Spacer(Modifier.height(8.dp))
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

/** Telefoontijd, wedstrijdtijd en verbindingsstatus. Ververst zichzelf elke 50 ms. */
@Composable
private fun TimerPanel(vm: TimerViewModel, status: EspStatus?, connected: Boolean, width: Dp) {
    rememberTick()
    val phoneTime = TimeFormat.time(System.currentTimeMillis())
    val localFlags by vm.flags.state.collectAsStateWithLifecycle()
    // Klok niet verbonden maar de vlaggen lopen op de telefoon: toon die tijd
    val race = if (localFlags.local && (localFlags.running || localFlags.resuming)) localFlags.timeText
               else TimerMath.raceText(status, vm.client.espNow())
    val digitSize = with(LocalDensity.current) { (width / 3.2f).toSp() }
    val panelOff = status != null && !status.ledPanel

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("TELEFOONTIJD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
        Text(phoneTime, fontFamily = FontFamily.Monospace, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(18.dp))
        Text("WEDSTRIJDTIJD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
        Text(
            race,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (digitSize.value > 150f) 150.sp else digitSize,
            maxLines = 1,
            modifier = Modifier.alpha(if (connected && !panelOff) 1f else 0.45f),
        )
        val flagState by vm.flags.state.collectAsStateWithLifecycle()
        Text(
            text = when {
                status == null -> "Wachten op de timer…"
                panelOff -> "LED-paneel staat uit"
                flagState.phase == nl.julian.zztimer.Phase.RESUMING -> "Waarschuwingssein over ${flagState.timeText}"
                status.running && status.repeat -> "Procedure loopt · herhalen"
                status.running -> "Procedure loopt · één keer"
                else -> "Gereed"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        StatusText(connected, "VERBONDEN", "GEEN VERBINDING", ZzColors.Ok, ZzColors.Error)
        if (!connected) {
            Text(
                "Verbind de telefoon met het WiFi-netwerk van de timer. De timer zelf loopt gewoon door.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
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
    val running = status?.running == true
    val panelOn = status?.ledPanel != false

    StartControls(vm, connected)

    // Modus: wordt bepaald door de schakelaar op de timer
    SectionCard("MODUS") {
        val repeat = status?.repeat
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ModeChip("Eén keer", selected = repeat == false, modifier = Modifier.weight(1f))
            ModeChip("Herhalen", selected = repeat == true, modifier = Modifier.weight(1f))
        }
        Text(
            if (running) "Modus van de lopende procedure. Wijzigen met de schakelaar op de timer (geldt bij de volgende start)."
            else "Stand van de schakelaar op de timer. Wijzigen kan alleen met die schakelaar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("STATUS") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LED-PANEEL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                StatusText(panelOn && status != null, "AAN", "UIT", ZzColors.Ok)
            }
            OutlinedButton(
                onClick = { vm.setLedPanel(!panelOn) },
                enabled = connected && status != null && (!panelOn || !running),
            ) { Text(if (panelOn) "Zet uit" else "Zet aan") }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TOETER", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                StatusText(status?.horn == true, "AAN", "UIT", ZzColors.Horn)
            }
            Text(
                if (status?.manualAllowed == true) "Handmatig toegestaan" else "Handmatig geblokkeerd",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    SectionCard("GEPLANDE STARTS") {
        // Starts die de app zelf plant (10 s aftellen, hervatten) staan bij de START-knop
        val schedules = status?.schedules.orEmpty().filter { it.pressEpoch !in vm.client.silentTargets }
        if (status != null && !status.timeSynced) {
            Text(
                "De klok van de timer wordt gesynchroniseerd met deze telefoon…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (schedules.isEmpty()) {
            Text("Geen geplande starts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        schedules.forEach { s ->
            ScheduleRow(
                s,
                enabled = connected,
                onEdit = {
                    val t = Instant.ofEpochMilli(s.targetEpoch).atZone(ZoneId.systemDefault()).toLocalTime()
                    onEditSchedule(ScheduleInput(s.id, t.hour, t.minute, s.kind))
                },
                onDelete = { vm.deleteSchedule(s.id) },
            )
        }
        OutlinedButton(
            onClick = {
                val t = LocalTime.now().plusMinutes(10)
                onEditSchedule(ScheduleInput(null, t.hour, t.minute, 0))
            },
            enabled = connected && status != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Geplande start toevoegen")
        }
        Text(
            "De timer voert geplande starts zelf uit, ook als de app gesloten is. " +
                "Na een stroomonderbreking moet de app één keer verbinden om de klok te synchroniseren.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Handmatige toeter: klinkt zolang de knop wordt ingedrukt (zelfde regels als de fysieke knop). */
@Composable
private fun HornButton(enabled: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val color = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        pressed -> ZzColors.Horn
        else -> ZzColors.Horn.copy(alpha = 0.75f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color,
        contentColor = if (enabled) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                if (pressed) "TOETER KLINKT" else "TOETER · INGEDRUKT HOUDEN",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(if (selected) "● $label" else label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ScheduleRow(s: Schedule, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                TimeFormat.time(s.targetEpoch),
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            val what = if (s.kind == 0)
                "Startschot · procedure start ${TimeFormat.time(s.pressEpoch)}"
            else
                "Procedure start · startschot ${TimeFormat.time(s.pressEpoch + 300_000)}"
            Text("${TimeFormat.dayLabel(s.targetEpoch)} · $what", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val until = s.pressEpoch - System.currentTimeMillis()
            if (until in 0..3_600_000L) {
                Text(
                    "Waarschuwingssein over ${TimerMath.format(((until + 999) / 1000).toInt())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZzColors.Horn,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Filled.Edit, contentDescription = "Aanpassen") }
        IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Filled.Delete, contentDescription = "Verwijderen") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDialog(vm: TimerViewModel, initial: ScheduleInput, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var kind by remember { mutableIntStateOf(initial.kind) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "Geplande start" else "Geplande start aanpassen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimeInput(state = picker)
                KindOption("Startschot (0:00) om dit tijdstip", "Procedure begint 5 minuten eerder", kind == 0) { kind = 0 }
                KindOption("Procedure start om dit tijdstip", "Startschot valt 5 minuten later", kind == 1) { kind = 1 }
                error?.let { Text(it, color = ZzColors.Error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val err = vm.saveSchedule(initial.copy(hour = picker.hour, minute = picker.minute, kind = kind))
                        busy = false
                        if (err == null) onDismiss() else error = err
                    }
                },
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun KindOption(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinDialog(vm: TimerViewModel, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beheerders-PIN") },
        text = {
            Column {
                Text("Voer de PIN in om de instellingen te openen.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                error?.let { Text(it, color = ZzColors.Error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && pin.length >= 4,
                onClick = {
                    busy = true
                    scope.launch {
                        val err = vm.unlockSettings(pin)
                        busy = false
                        if (err == null) onUnlocked() else error = err
                    }
                },
            ) { Text("Openen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
