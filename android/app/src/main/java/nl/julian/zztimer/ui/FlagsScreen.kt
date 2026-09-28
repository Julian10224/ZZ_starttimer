package nl.julian.zztimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.julian.zztimer.ClassColor
import nl.julian.zztimer.EspStatus
import nl.julian.zztimer.FlagSettings
import nl.julian.zztimer.ScheduleInput
import nl.julian.zztimer.FlagState
import nl.julian.zztimer.Interrupt
import nl.julian.zztimer.Phase
import nl.julian.zztimer.PrepFlag
import nl.julian.zztimer.TimerViewModel

private enum class Confirm(val title: String, val text: String) {
    INDIVIDUAL("Individuele terugroep?", "X-vlag op met één geluidssein. De vlag gaat uiterlijk na 4 minuten vanzelf neer."),
    GENERAL("Algemene terugroep?", "Eerste vervangende op met twee geluidsseinen. De timer stopt; na het neerhalen volgt het waarschuwingssein 1 minuut later."),
    POSTPONE("Uitstel?", "Uitstelwimpel op met twee geluidsseinen. De timer stopt; na het neerhalen volgt het waarschuwingssein 1 minuut later."),
}

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vlaggen", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Instellingen vlaggen en stem")
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
            val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { FlagDisplay(state, connected) }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { FlagControls(vm, state, settings, connected, status, { confirm = it }, { scheduleEdit = it }) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlagDisplay(state, connected)
                    FlagControls(vm, state, settings, connected, status, { confirm = it }, { scheduleEdit = it })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.text) },
            confirmButton = {
                TextButton(onClick = {
                    when (c) {
                        Confirm.INDIVIDUAL -> vm.flags.individualRecall()
                        Confirm.GENERAL -> vm.flags.generalRecall()
                        Confirm.POSTPONE -> vm.flags.postpone()
                    }
                    confirm = null
                }) { Text("Uitvoeren") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuleren") } },
        )
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

private fun phaseColor(p: Phase): Color = when (p) {
    Phase.WARNING, Phase.PREPARATORY -> ZzColors.Sea
    Phase.LAST_MINUTE -> ZzColors.Horn
    Phase.STARTED -> ZzColors.Ok
    Phase.POSTPONED, Phase.GENERAL_RECALL -> ZzColors.Stop
    Phase.RESUMING -> ZzColors.Horn
    Phase.IDLE, Phase.NO_CONNECTION -> ZzColors.Inactive
}

@Composable
private fun ColumnScope.FlagDisplay(state: FlagState, connected: Boolean) {
    // Fase
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = phaseColor(state.phase).copy(alpha = 0.18f),
        contentColor = phaseColor(state.phase),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            state.phase.label.uppercase(),
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }

    // Tijd en start
    Text(
        state.timeText,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 72.sp,
    )
    val sub = buildString {
        if (state.startNumber > 0) append("Start ${state.startNumber}")
        if (state.className.isNotEmpty() && state.running) {
            if (isNotEmpty()) append(" · ")
            append(state.className)
        }
    }
    if (sub.isNotEmpty()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.local) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ZzColors.Error.copy(alpha = 0.18f),
            contentColor = ZzColors.Error,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("○ KLOK NIET VERBONDEN", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "De vlaggen lopen op de telefoon. Druk START of RESET van de klok op het aangegeven moment; geluidsseinen geef je zelf.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                )
            }
        }
    }
    state.banner?.let { InstructionBanner(it, state.bannerUrgent) }

    // Vlaggen
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        if (state.flags.isEmpty()) NoFlags(110.dp)
        state.flags.forEach { FlagCard(it, 110.dp) }
    }

    // Welke vlag er als eerste op/neer gaat, en die daarna (zoals in Start Timer)
    if (state.upcoming.isNotEmpty()) UpcomingFlags(state.upcoming)
    else state.nextText?.let {
        Text("Volgende: $it", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
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
    // Start / stop met de keuze 10 s aftellen of direct (gelijk aan de Timer-pagina)
    StartControls(vm, connected)

    // Onderbrekingen
    SectionCard("SEINEN") {
        when {
            state.interrupt == Interrupt.POSTPONED -> {
                BigButton("UITSTELWIMPEL NEER", ZzColors.Horn, state.canResume, vm.flags::resume, Modifier.fillMaxWidth())
                Hint("1 geluidssein; de timer geeft 1 minuut later zelf het waarschuwingssein.")
            }
            state.interrupt == Interrupt.GENERAL_RECALL -> {
                BigButton("EERSTE VERVANGENDE NEER", ZzColors.Horn, state.canResume, vm.flags::resume, Modifier.fillMaxWidth())
                Hint("1 geluidssein; de timer geeft 1 minuut later zelf het waarschuwingssein.")
            }
            else -> {
                if (state.interrupt == Interrupt.INDIVIDUAL_RECALL) {
                    OutlinedButton(onClick = vm.flags::lowerX, modifier = Modifier.fillMaxWidth()) {
                        Text("X-vlag neer (alle boten terug)")
                    }
                }
                SignalButton("X", "Individuele terugroep", state.canIndividualRecall) { onConfirm(Confirm.INDIVIDUAL) }
                SignalButton("1e", "Algemene terugroep", state.canGeneralRecall) { onConfirm(Confirm.GENERAL) }
                SignalButton("AP", "Uitstel", state.canPostpone) { onConfirm(Confirm.POSTPONE) }
                Hint("Terugroepen kan tot 4 minuten na een start.")
            }
        }
    }

    // Verloop volgens regel 26
    SectionCard("VERLOOP (REGEL 26)") {
        val steps = listOf(
            "5:00" to "Waarschuwingssein · klassevlag op",
            "4:00" to "Voorbereidingssein · ${settings.prep.label} op",
            "1:00" to "Eén minuut · ${settings.prep.label} neer",
            "0:00" to "Start · klassevlag neer",
        )
        steps.forEachIndexed { i, (t, text) ->
            val done = state.timelineStep > i
            val current = state.timelineStep == i
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        current -> ZzColors.Sea
                        done -> ZzColors.Ok
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(14.dp),
                ) {}
                Spacer(Modifier.width(10.dp))
                Text(t, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                Text(
                    text + if (done) "  ✓" else "",
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Geplande starts (zelfde lijst als op de Timer-pagina)
    ScheduleSection(vm, status, connected, onEditSchedule)

    // Stem
    SectionCard("STEM") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Gesproken aftellen", fontWeight = FontWeight.Medium)
                Text(
                    when {
                        !vm.flags.voice.dutchAvailable -> "Nederlandse stem niet beschikbaar op deze telefoon"
                        settings.voice -> "● AAN"
                        else -> "○ UIT"
                    },
                    color = if (settings.voice && vm.flags.voice.dutchAvailable) ZzColors.Ok else ZzColors.Inactive,
                    fontSize = 13.sp,
                )
            }
            Switch(checked = settings.voice, onCheckedChange = { vm.flags.updateSettings(settings.copy(voice = it)) })
        }
    }
}

@Composable
private fun SignalButton(code: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(code, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FlagSettingsDialog(vm: TimerViewModel, initial: FlagSettings, onDismiss: () -> Unit) {
    var prep by remember { mutableStateOf(initial.prep) }
    var classes by remember { mutableStateOf(initial.classes.joinToString(", ")) }
    var everyMinute by remember { mutableStateOf(initial.everyMinute) }
    var every10s by remember { mutableStateOf(initial.every10s) }
    var lastTen by remember { mutableStateOf(initial.lastTen) }
    var classColor by remember { mutableStateOf(initial.classColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vlaggen en stem") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Voorbereidingsvlag", fontWeight = FontWeight.Medium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrepFlag.entries.forEach { f ->
                        FilterChip(selected = prep == f, onClick = { prep = f }, label = { Text(f.label) })
                    }
                }
                HorizontalDivider()
                Text("Klassen", fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = classes,
                    onValueChange = { classes = it },
                    label = { Text("Bijv. Optimist, ILCA 7, Laser") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint("Gescheiden door komma's. Bij herhalen (schakelaar op de timer) krijgt elke start de volgende klasse.")
                Text("Kleur klassevlag", fontWeight = FontWeight.Medium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClassColor.entries.forEach { c ->
                        FilterChip(selected = classColor == c, onClick = { classColor = c }, label = { Text(c.label) })
                    }
                }
                HorizontalDivider()
                Text("Aankondigingen", fontWeight = FontWeight.Medium)
                SwitchRow("Elke minuut", everyMinute) { everyMinute = it }
                SwitchRow("Laatste minuut elke 10 seconden", every10s) { every10s = it }
                SwitchRow("10 seconden aftellen vóór elk sein", lastTen) { lastTen = it }
                OutlinedButton(onClick = vm.flags::testVoice, modifier = Modifier.fillMaxWidth()) { Text("Stem testen") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
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
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
