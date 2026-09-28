package nl.julian.zztimer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import nl.julian.zztimer.ui.FlagsScreen
import nl.julian.zztimer.ui.MainScreen
import nl.julian.zztimer.ui.NavIcons
import nl.julian.zztimer.ui.SettingsScreen
import nl.julian.zztimer.ui.SplashScreen
import nl.julian.zztimer.ui.ZzTheme

class MainActivity : ComponentActivity() {

    private val vm: TimerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Scherm aan houden zolang de app op de voorgrond is (wedstrijdgebruik).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ZzTheme { AppRoot(vm) }
        }
    }
}

@Composable
private fun AppRoot(vm: TimerViewModel) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var screen by rememberSaveable { mutableStateOf("main") }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(1200)
            showSplash = false
        }
    }

    Crossfade(targetState = if (showSplash) "splash" else screen, label = "scherm") { target ->
        when (target) {
            "splash" -> SplashScreen()
            "settings" -> SettingsScreen(vm, onBack = {
                vm.lockSettings()
                screen = "main"
            })
            "flags" -> {
                BackHandler { screen = "main" }
                FlagsScreen(vm, bottomBar = { BottomNav("flags") { screen = it } })
            }
            else -> MainScreen(
                vm,
                onOpenSettings = { screen = "settings" },
                bottomBar = { BottomNav("main") { screen = it } },
            )
        }
    }
}

/** Navigatie tussen de timer en de vlaggenpagina. */
@Composable
private fun BottomNav(current: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current == "main",
            onClick = { onSelect("main") },
            icon = { Icon(NavIcons.Stopwatch, contentDescription = null) },
            label = { Text("Timer") },
        )
        NavigationBarItem(
            selected = current == "flags",
            onClick = { onSelect("flags") },
            icon = { Icon(NavIcons.Flag, contentDescription = null) },
            label = { Text("Vlaggen") },
        )
    }
}
