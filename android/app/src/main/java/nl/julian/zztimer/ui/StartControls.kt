package nl.julian.zztimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.julian.zztimer.TimerViewModel

/**
 * START / STOP-RESET met daaronder de keuze "10 s aftellen" of "Direct", en de melding
 * als er een start aankomt. Staat op beide pagina's, zodat de bediening overal gelijk is.
 */
@Composable
fun StartControls(vm: TimerViewModel, connected: Boolean) {
    val status by vm.status.collectAsStateWithLifecycle()
    val flagState by vm.flags.state.collectAsStateWithLifecycle()
    val settings by vm.flags.settings.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton(
                text = "START",
                color = ZzColors.Go,
                enabled = connected && status != null && flagState.canStart,
                onClick = vm::start,
                modifier = Modifier.weight(1f),
            )
            BigButton(
                text = "STOP / RESET",
                color = ZzColors.Stop,
                enabled = connected && status != null,
                onClick = vm::reset,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartModeOption("10 s aftellen", settings.countdownStart, Modifier.weight(1f)) { vm.flags.setCountdownStart(true) }
            StartModeOption("Direct", !settings.countdownStart, Modifier.weight(1f)) { vm.flags.setCountdownStart(false) }
        }

        if (flagState.resuming) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "● Waarschuwingssein over ${flagState.timeText}",
                    color = ZzColors.Horn,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm.flags::cancelResume, enabled = connected) { Text("Annuleren") }
            }
        }

        if (connected && status != null && status?.ledPanel == false) {
            Text(
                "Het LED-paneel staat uit. Zet het aan om te kunnen starten.",
                style = MaterialTheme.typography.bodySmall,
                color = ZzColors.Error,
            )
        }
    }
}

@Composable
private fun StartModeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .height(44.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                if (selected) "● $label" else label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
            )
        }
    }
}
