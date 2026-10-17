package nl.julian.zztimer.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val c = Zz.colors

    Column(verticalArrangement = Arrangement.spacedBy(Space.m), modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = flagState.banner != null,
            enter = fadeIn(tween(Motion.STANDARD)) + expandVertically(tween(Motion.STANDARD)),
            exit = fadeOut(tween(Motion.STANDARD)) + shrinkVertically(tween(Motion.STANDARD)),
        ) {
            flagState.banner?.let { InstructionBanner(it, flagState.bannerUrgent) }
        }

        // Loopt de procedure of komt een start eraan, dan kan er niet gestart worden: dan staat
        // alleen Stop / Reset er, over de volle breedte. Anders Start (hoofdactie) en Stop / Reset.
        val locked = flagState.running || flagState.resuming
        val view = LocalView.current
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            modifier = Modifier.fillMaxWidth().animateContentSize(tween(Motion.STANDARD)),
        ) {
            if (!locked) {
                ZzButton(
                    text = "Start",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        vm.start()
                    },
                    enabled = flagState.canStart,
                    large = true,
                    modifier = Modifier.weight(1f),
                )
            }
            ZzButton(
                text = "Stop / Reset",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    vm.reset()
                },
                style = ButtonStyle.Tinted,
                color = c.destructive,
                large = true,
                modifier = Modifier.weight(1f),
            )
        }

        // De keuze geldt voor de volgende start; tijdens een procedure niet zichtbaar
        if (!locked) {
            SegmentedControl(
                options = listOf("10 s aftellen", "Direct"),
                selected = if (settings.countdownStart) 0 else 1,
                onSelect = { vm.flags.setCountdownStart(it == 0) },
            )
        }

        AnimatedVisibility(
            visible = flagState.resuming,
            enter = fadeIn(tween(Motion.STANDARD)) + expandVertically(tween(Motion.STANDARD)),
            exit = fadeOut(tween(Motion.STANDARD)) + shrinkVertically(tween(Motion.STANDARD)),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.medium),
                color = c.warning.copy(alpha = if (c.isDark) 0.2f else 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = Space.l, end = Space.xs, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Waarschuwingssein over ${flagState.timeText}",
                        style = ZzType.body.tabular().copy(fontWeight = FontWeight.SemiBold),
                        color = c.warningText,
                        modifier = Modifier.weight(1f),
                    )
                    ZzButton("Annuleren", vm.flags::cancelResume, style = ButtonStyle.Plain, color = c.warningText)
                }
            }
        }

        if (flagState.local) {
            Footnote(
                "Klok niet verbonden: START en RESET gelden voor de telefoon. Druk START/RESET van de klok op het aangegeven moment.",
                color = c.destructive,
                modifier = Modifier.padding(horizontal = Space.xs),
            )
        }

        if (connected && status != null && status?.ledPanel == false) {
            Footnote(
                "Het LED-paneel staat uit. Zet het aan om te kunnen starten.",
                color = c.destructive,
                modifier = Modifier.padding(horizontal = Space.xs),
            )
        }
    }
}
