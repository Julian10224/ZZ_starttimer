package nl.julian.zztimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.julian.zztimer.FlagKind
import nl.julian.zztimer.ShownFlag

/** Kleuren van de internationale seinvlaggen. */
private object SignalColors {
    val Blue = Color(0xFF1B4FA0)
    val Yellow = Color(0xFFF7C600)
    val Red = Color(0xFFD32F2F)
    val Black = Color(0xFF111111)
    val White = Color(0xFFFFFFFF)
    val Edge = Color(0xFF8A9BB0)
}

/** Tekent één vlag of wimpel. Vlaggen 4:3, wimpels even hoog maar langer. */
@Composable
fun SignalFlag(flag: ShownFlag, height: Dp, modifier: Modifier = Modifier) {
    val pennant = flag.kind == FlagKind.FIRST_SUBSTITUTE || flag.kind == FlagKind.AP
    val width = if (pennant) height * 1.6f else height * 4f / 3f
    if (flag.kind == FlagKind.CLASS) {
        ClassFlag(flag.label, height, width, modifier)
        return
    }
    Canvas(modifier.width(width).height(height)) {
        when (flag.kind) {
            FlagKind.P -> drawP()
            FlagKind.I -> drawI()
            FlagKind.Z -> drawZ()
            FlagKind.U -> drawU()
            FlagKind.BLACK -> drawRect(SignalColors.Black)
            FlagKind.X -> drawX()
            FlagKind.FIRST_SUBSTITUTE -> drawFirstSubstitute()
            FlagKind.AP -> drawAnswering()
            FlagKind.CLASS -> Unit
        }
        if (!pennant) drawRect(SignalColors.Edge, style = Stroke(width = 1.5f))
    }
}

/** Klassevlag: wit met de klassenaam. */
@Composable
private fun ClassFlag(name: String, height: Dp, width: Dp, modifier: Modifier) {
    Box(
        modifier
            .width(width)
            .height(height)
            .background(SignalColors.White)
            .border(1.5.dp, SignalColors.Edge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            color = SignalColors.Blue,
            fontWeight = FontWeight.Black,
            fontSize = (height.value * 0.26f).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(6.dp),
        )
    }
}

// P (Papa): blauw met een wit vlak in het midden
private fun DrawScope.drawP() {
    drawRect(SignalColors.Blue)
    val w = size.width / 3f
    val h = size.height / 3f
    drawRect(SignalColors.White, topLeft = Offset(w, h), size = Size(w, h))
}

// I (India): geel met een zwarte bol
private fun DrawScope.drawI() {
    drawRect(SignalColors.Yellow)
    drawCircle(SignalColors.Black, radius = size.minDimension * 0.25f, center = center)
}

// Z (Zulu): vier driehoeken – boven geel, broekingzijde (links) zwart, onder rood, rechts blauw
private fun DrawScope.drawZ() {
    val w = size.width
    val h = size.height
    val c = center
    fun tri(a: Offset, b: Offset, color: Color) {
        drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
    }
    tri(Offset(0f, 0f), Offset(w, 0f), SignalColors.Yellow)
    tri(Offset(0f, 0f), Offset(0f, h), SignalColors.Black)
    tri(Offset(0f, h), Offset(w, h), SignalColors.Red)
    tri(Offset(w, 0f), Offset(w, h), SignalColors.Blue)
}

// U (Uniform): kwartieren rood en wit, rood linksboven en rechtsonder
private fun DrawScope.drawU() {
    drawRect(SignalColors.White)
    val hw = size.width / 2f
    val hh = size.height / 2f
    drawRect(SignalColors.Red, topLeft = Offset.Zero, size = Size(hw, hh))
    drawRect(SignalColors.Red, topLeft = Offset(hw, hh), size = Size(hw, hh))
}

// X (X-ray): wit met een blauw kruis
private fun DrawScope.drawX() {
    drawRect(SignalColors.White)
    val bw = size.width / 5f
    val bh = size.height / 5f
    drawRect(SignalColors.Blue, topLeft = Offset((size.width - bw) / 2f, 0f), size = Size(bw, size.height))
    drawRect(SignalColors.Blue, topLeft = Offset(0f, (size.height - bh) / 2f), size = Size(size.width, bh))
}

// Eerste vervangende: driehoekige wimpel, geel met een blauwe rand die de broekingzijde niet raakt
private fun DrawScope.drawFirstSubstitute() {
    val w = size.width
    val h = size.height
    val outer = Path().apply { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h); close() }
    drawPath(outer, SignalColors.Blue)
    val b = h * 0.17f
    val inner = Path().apply {
        moveTo(0f, b)
        lineTo(w - b * 3.2f, h / 2f)
        lineTo(0f, h - b)
        close()
    }
    drawPath(inner, SignalColors.Yellow)
    drawPath(outer, SignalColors.Edge, style = Stroke(width = 1.5f))
}

// Uitstelwimpel (antwoordwimpel, AP): taps toelopend, vijf verticale banen rood en wit
private fun DrawScope.drawAnswering() {
    val w = size.width
    val h = size.height
    val shape = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, h * 0.3f)
        lineTo(w, h * 0.7f)
        lineTo(0f, h)
        close()
    }
    clipPath(shape) {
        val stripe = w / 5f
        for (i in 0 until 5) {
            drawRect(
                if (i % 2 == 0) SignalColors.Red else SignalColors.White,
                topLeft = Offset(i * stripe, 0f),
                size = Size(stripe + 1f, h),
            )
        }
    }
    drawPath(shape, SignalColors.Edge, style = Stroke(width = 1.5f))
}

/** Vlag met naam eronder, voor de vlaggenpagina. */
@Composable
fun FlagCard(flag: ShownFlag, height: Dp, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SignalFlag(flag, height)
        Text(
            flag.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text("● OP", color = ZzColors.Ok, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Lege plek als er geen vlag staat. */
@Composable
fun NoFlags(height: Dp) {
    Box(
        Modifier
            .height(height)
            .aspectRatio(4f / 3f)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Geen vlaggen", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
