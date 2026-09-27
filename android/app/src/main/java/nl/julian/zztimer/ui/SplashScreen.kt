package nl.julian.zztimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SplashScreen() {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(400)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(ZzColors.Navy)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StopwatchLogo(Modifier.size(104.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                "ZZ Wedstrijd Timer",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Made by Julian",
            color = ZzColors.Muted,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .alpha(fade.value),
        )
    }
}

@Composable
fun StopwatchLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.07f
        val radius = size.minDimension * 0.36f
        val center = Offset(size.width / 2f, size.height * 0.57f)
        val top = center.y - radius
        drawLine(
            ZzColors.Sea,
            Offset(center.x - radius * 0.3f, top - stroke * 2.4f),
            Offset(center.x + radius * 0.3f, top - stroke * 2.4f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(ZzColors.Sea, Offset(center.x, top - stroke * 2.2f), Offset(center.x, top), strokeWidth = stroke * 0.8f)
        drawCircle(ZzColors.Sea, radius, center, style = Stroke(width = stroke))
        drawLine(
            Color.White,
            center,
            Offset(center.x + radius * 0.5f, center.y - radius * 0.5f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White, stroke * 0.7f, center)
    }
}
