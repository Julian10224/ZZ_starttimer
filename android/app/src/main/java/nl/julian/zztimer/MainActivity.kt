package nl.julian.zztimer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.julian.zztimer.ui.FlagsScreen
import nl.julian.zztimer.ui.MainScreen
import nl.julian.zztimer.ui.Motion
import nl.julian.zztimer.ui.NavIcons
import nl.julian.zztimer.ui.SettingsScreen
import nl.julian.zztimer.ui.Zz
import nl.julian.zztimer.ui.ZzTheme
import nl.julian.zztimer.ui.ZzType

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
    // Geen eigen openingsscherm: de app start direct (het systeem toont kort het icoon).
    var screen by rememberSaveable { mutableStateOf("main") }

    Crossfade(
        targetState = screen,
        animationSpec = tween(Motion.STANDARD),
        label = "scherm",
    ) { target ->
        when (target) {
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

/**
 * Tabbalk onderin: rustig, met een haarlijn erboven en de accentkleur voor de actieve tab.
 * Liggend compacter (icoon naast de tekst), zodat er meer ruimte voor de inhoud is.
 */
@Composable
private fun BottomNav(current: String, onSelect: (String) -> Unit) {
    val c = Zz.colors
    // Compacter bij weinig hoogte (liggend op een telefoon)
    val landscape = LocalConfiguration.current.screenHeightDp < 480
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(thickness = 0.5.dp, color = c.separator)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = if (landscape) 44.dp else 56.dp)
                .selectableGroup(),
        ) {
            TabItem("Timer", NavIcons.Stopwatch, current == "main", landscape) { onSelect("main") }
            TabItem("Vlaggen", NavIcons.Flag, current == "flags", landscape) { onSelect("flags") }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: ImageVector, selected: Boolean, horizontal: Boolean, onClick: () -> Unit) {
    val c = Zz.colors
    val color by animateColorAsState(if (selected) c.accent else c.secondaryLabel, tween(Motion.FAST), label = "tab")
    val style = ZzType.caption.copy(letterSpacing = 0.sp, fontSize = if (horizontal) 13.sp else 11.sp)
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = if (horizontal) 44.dp else 56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (horizontal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = style, color = color)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 6.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(2.dp))
                Text(label, style = style, color = color)
            }
        }
    }
}
