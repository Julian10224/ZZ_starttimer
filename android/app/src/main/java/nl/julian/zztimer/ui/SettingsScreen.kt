package nl.julian.zztimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instellingen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cardModifier = Modifier.widthIn(max = 640.dp)

            SectionCard("ESP STATUS", cardModifier) {
                InfoRow("WiFi", if (connected) "● Verbonden" else "○ Geen verbinding")
                InfoRow("Netwerknaam", status?.ssid ?: "–")
                InfoRow("IP-adres", status?.ip ?: vm.host)
                InfoRow("Verbonden apparaten", status?.clients?.toString() ?: "–")
                InfoRow("Firmware", status?.firmware ?: "–")
                InfoRow("Actief sinds", status?.let { TimeFormat.duration(it.uptimeS) } ?: "–")
                InfoRow("Klok gesynchroniseerd", if (status?.timeSynced == true) "Ja" else "Nee")
                InfoRow("Reactietijd", rtt?.let { "$it ms" } ?: "–")
                InfoRow("Signaal (telefoon)", signal?.let { "$it dBm" } ?: "–")
                OutlinedButton(
                    onClick = {
                        testResult = "Bezig…"
                        scope.launch {
                            val r = vm.testConnection()
                            testResult = if (r != null) "Timer bereikbaar, reactietijd $r ms." else "Timer niet bereikbaar."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("VERBINDING TESTEN") }
                testResult?.let { Text(it) }
            }

            SectionCard("ESP WIFI", cardModifier) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it.take(32) },
                    label = { Text("Netwerknaam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(63) },
                    label = { Text("Nieuw wachtwoord (8–63 tekens)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Verberg" else "Toon")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { confirmWifi = true },
                    enabled = connected && ssid.isNotBlank() && password.length >= 8,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("OPSLAAN") }
                wifiError?.let { Text(it, color = ZzColors.Error) }
                Text(
                    "Na opslaan start de timer het WiFi-netwerk opnieuw met de nieuwe gegevens. " +
                        "Verbind de telefoon daarna opnieuw. Niet mogelijk tijdens een lopende procedure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("BEHEERDERS-PIN", cardModifier) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { v -> newPin = v.filter { it.isDigit() }.take(8) },
                    label = { Text("Nieuwe PIN (4–8 cijfers)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPin2,
                    onValueChange = { v -> newPin2 = v.filter { it.isDigit() }.take(8) },
                    label = { Text("Herhaal nieuwe PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
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
                    enabled = connected && newPin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("PIN WIJZIGEN") }
                pinMessage?.let { Text(it) }
            }

            SectionCard("APP", cardModifier) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP-adres van de timer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        vm.saveHost(host.ifBlank { TimerViewModel.DEFAULT_HOST })
                        hostMessage = "Opgeslagen. Er wordt opnieuw verbonden."
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("OPSLAAN") }
                hostMessage?.let { Text(it) }
                Text(
                    "Standaard: ${TimerViewModel.DEFAULT_HOST}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "ZZ Wedstrijd Timer · Made by Julian",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmWifi) {
        AlertDialog(
            onDismissRequest = { confirmWifi = false },
            title = { Text("WiFi wijzigen?") },
            text = { Text("De verbinding met de timer valt even weg. Daarna verbind je met \"$ssid\".") },
            confirmButton = {
                TextButton(onClick = {
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
                }) { Text("Wijzigen") }
            },
            dismissButton = { TextButton(onClick = { confirmWifi = false }) { Text("Annuleren") } },
        )
    }

    wifiChangedTo?.let { name ->
        AlertDialog(
            onDismissRequest = { wifiChangedTo = null },
            title = { Text("WiFi-instellingen gewijzigd") },
            text = { Text("Maak opnieuw verbinding met:\n$name\n\nOpen daarvoor de WiFi-instellingen van je telefoon. De app verbindt daarna vanzelf.") },
            confirmButton = { TextButton(onClick = { wifiChangedTo = null }) { Text("OK") } },
        )
    }
}
