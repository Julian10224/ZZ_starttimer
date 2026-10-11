package nl.julian.zztimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.julian.zztimer.TimeFormat
import nl.julian.zztimer.TimerViewModel

@Composable
fun SettingsScreen(vm: TimerViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val status by vm.status.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val rtt by vm.rttMs.collectAsStateWithLifecycle()
    val signal by vm.wifiSignal.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Zonder geldige PIN (bijv. na het herstarten van de app) terug naar het hoofdscherm.
    LaunchedEffect(vm.adminPin) { if (vm.adminPin == null) onBack() }

    var testResult by remember { mutableStateOf<String?>(null) }
    var ssid by remember { mutableStateOf(status?.ssid ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var wifiError by remember { mutableStateOf<String?>(null) }
    var confirmWifi by remember { mutableStateOf(false) }
    var wifiChangedTo by remember { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf("") }
    var newPin2 by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var host by remember { mutableStateOf(vm.host) }
    var hostMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(status?.ssid) { if (ssid.isEmpty()) ssid = status?.ssid ?: "" }

    val c = Zz.colors
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            ScreenHeader(
                "Instellingen",
                scrolled = scroll.value > 0,
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Terug", tint = c.accent)
                    }
                },
            )
        },
        containerColor = c.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = Space.screen, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cardModifier = Modifier.widthIn(max = 640.dp)

            Section("Timer", footer = testResult, modifier = cardModifier) {
                ListRow("WiFi", trailing = { StatusValue(connected, "Verbonden", "Geen verbinding", c.positive) })
                RowDivider()
                ValueRow("Netwerknaam", status?.ssid ?: "–")
                RowDivider()
                ValueRow("IP-adres", status?.ip ?: vm.host)
                RowDivider()
                ValueRow("Verbonden apparaten", status?.clients?.toString() ?: "–")
                RowDivider()
                ValueRow("Firmware", status?.firmware ?: "–")
                RowDivider()
                ValueRow("Actief sinds", status?.let { TimeFormat.duration(it.uptimeS) } ?: "–")
                RowDivider()
                ValueRow("Klok gesynchroniseerd", if (status?.timeSynced == true) "Ja" else "Nee")
                RowDivider()
                ValueRow("Reactietijd", rtt?.let { "$it ms" } ?: "–")
                RowDivider()
                ValueRow("Signaal (telefoon)", signal?.let { "$it dBm" } ?: "–")
                RowDivider()
                ActionRow("Verbinding testen", onClick = {
                    testResult = "Bezig…"
                    scope.launch {
                        val r = vm.testConnection()
                        testResult = if (r != null) "Timer bereikbaar, reactietijd $r ms." else "Timer niet bereikbaar."
                    }
                })
            }

            Section(
                "WiFi van de timer",
                footer = "Na opslaan start de timer het WiFi-netwerk opnieuw met de nieuwe gegevens. " +
                    "Verbind de telefoon daarna opnieuw. Niet mogelijk tijdens een lopende procedure.",
                modifier = cardModifier,
            ) {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    ZzTextField(
                        value = ssid,
                        onValueChange = { ssid = it.take(32) },
                        label = "Netwerknaam",
                    )
                    ZzTextField(
                        value = password,
                        onValueChange = { password = it.take(63) },
                        label = "Nieuw wachtwoord (8–63 tekens)",
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Verberg" else "Toon", style = ZzType.callout, color = c.accent)
                            }
                        },
                    )
                    ZzButton(
                        "Opslaan",
                        onClick = { confirmWifi = true },
                        enabled = connected && ssid.isNotBlank() && password.length >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    wifiError?.let { Footnote(it, color = c.destructive) }
                }
            }

            Section("Beheerders-PIN", modifier = cardModifier) {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    ZzTextField(
                        value = newPin,
                        onValueChange = { v -> newPin = v.filter { it.isDigit() }.take(8) },
                        label = "Nieuwe PIN (4–8 cijfers)",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    ZzTextField(
                        value = newPin2,
                        onValueChange = { v -> newPin2 = v.filter { it.isDigit() }.take(8) },
                        label = "Herhaal nieuwe PIN",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    ZzButton(
                        "PIN wijzigen",
                        onClick = {
                            if (newPin != newPin2) {
                                pinMessage = "De PIN-codes zijn niet gelijk."
                            } else {
                                scope.launch {
                                    val err = vm.changePin(newPin)
                                    pinMessage = err ?: "PIN gewijzigd."
                                    if (err == null) {
                                        newPin = ""
                                        newPin2 = ""
                                    }
                                }
                            }
                        },
                        style = ButtonStyle.Tinted,
                        enabled = connected && newPin.length >= 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    pinMessage?.let { Footnote(it, color = if (it == "PIN gewijzigd.") c.positive else c.destructive) }
                }
            }

            Section("App", footer = "Standaard: ${TimerViewModel.DEFAULT_HOST}", modifier = cardModifier) {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    ZzTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = "IP-adres van de timer",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    ZzButton(
                        "Opslaan",
                        onClick = {
                            vm.saveHost(host.ifBlank { TimerViewModel.DEFAULT_HOST })
                            hostMessage = "Opgeslagen. Er wordt opnieuw verbonden."
                        },
                        style = ButtonStyle.Tinted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    hostMessage?.let { Footnote(it, color = c.positive) }
                }
            }

            Footnote(
                "ZZ Wedstrijd Timer · Made by Julian",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
            )
        }
    }

    if (confirmWifi) {
        ZzDialog(
            title = "WiFi wijzigen?",
            onDismiss = { confirmWifi = false },
            confirmText = "Wijzigen",
            onConfirm = {
                confirmWifi = false
                val target = ssid
                scope.launch {
                    val err = vm.saveWifi(target, password)
                    wifiError = err
                    if (err == null) {
                        password = ""
                        wifiChangedTo = target
                    }
                }
            },
        ) {
            Text("De verbinding met de timer valt even weg. Daarna verbind je met \"$ssid\".", style = ZzType.callout, color = Zz.colors.secondaryLabel)
        }
    }

    wifiChangedTo?.let { name ->
        ZzDialog(
            title = "WiFi-instellingen gewijzigd",
            onDismiss = { wifiChangedTo = null },
            confirmText = "OK",
            dismissText = null,
            onConfirm = { wifiChangedTo = null },
        ) {
            Text("Maak opnieuw verbinding met:", style = ZzType.callout, color = Zz.colors.secondaryLabel)
            Text(name, style = ZzType.headline, color = Zz.colors.label)
            Text(
                "Open daarvoor de WiFi-instellingen van je telefoon. De app verbindt daarna vanzelf.",
                style = ZzType.callout,
                color = Zz.colors.secondaryLabel,
            )
        }
    }
}
