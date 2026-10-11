package nl.julian.zztimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.julian.zztimer.R

/** Kort openingsscherm: het app-icoon als tegel met de naam eronder. */
@Composable
fun SplashScreen() {
    val c = Zz.colors
    val reduce = Zz.reduceMotion
    val fade = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(360)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(fade.value)
                .graphicsLayer {
                    val s = 0.96f + 0.04f * fade.value
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .shadow(if (c.isDark) 0.dp else 10.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF0B1B2B)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(168.dp),
                )
            }
            Spacer(Modifier.height(Space.xl))
            Text("ZZ Wedstrijd Timer", style = ZzType.title, color = c.label)
        }
        Text(
            "Made by Julian",
            style = ZzType.footnote,
            color = c.secondaryLabel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Space.xxl)
                .alpha(fade.value),
        )
    }
}
