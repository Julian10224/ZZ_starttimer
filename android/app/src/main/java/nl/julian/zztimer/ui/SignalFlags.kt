package nl.julian.zztimer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.julian.zztimer.FlagKind
import nl.julian.zztimer.FlagLogic
import nl.julian.zztimer.ShownFlag
import nl.julian.zztimer.UpcomingFlag

/**
 * Internationale seinvlaggen, in dezelfde vormen, verhoudingen en kleuren als in Start Timer:
 * vlaggen vierkant, zuivere kleuren; eerste vervangende 4:3, uitstelwimpel 3:1.
 */
private object SignalColors {
    val Blue = Color(0xFF0000FF)
    val Yellow = Color(0xFFFFFF00)
    val Red = Color(0xFFFF0000)
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Edge = Color(0xFF8A9BB0)      // dunne rand, alleen zodat zwart/wit zichtbaar blijft
}

/** Breedte/hoogte van een vlag. */
private fun aspect(kind: FlagKind): Float = when (kind) {
    FlagKind.FIRST_SUBSTITUTE -> 4f / 3f
    FlagKind.AP -> 3f
    else -> 1f
}

/** Tekent één vlag of wimpel op de gegeven hoogte. */
@Composable
fun SignalFlag(flag: ShownFlag, height: Dp, modifier: Modifier = Modifier) {
    val width = height * aspect(flag.kind)
    if (flag.kind == FlagKind.CLASS) {
        ClassFlag(flag, height, modifier)
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
        if (flag.kind != FlagKind.FIRST_SUBSTITUTE && flag.kind != FlagKind.AP) {
            drawRect(SignalColors.Edge, style = Stroke(width = 1.5f))
        }
    }
}

/** Klassevlag: vierkant, gekozen achtergrondkleur met de klassenaam. */
@Composable
private fun ClassFlag(flag: ShownFlag, height: Dp, modifier: Modifier) {
    Box(
        modifier
            .width(height)
            .height(height)
            .background(Color(flag.classColor.argb))
            .border(1.5.dp, SignalColors.Edge),
        contentAlignment = Alignment.Center,
    ) {
        val len = flag.label.length.coerceAtLeast(1)
        val size = when {
            len <= 3 -> 0.36f
            len <= 6 -> 0.24f
            len <= 10 -> 0.17f
            else -> 0.13f
        }
        Text(
            flag.label,
            color = Color(flag.classColor.textArgb),
            fontWeight = FontWeight.Black,
            fontSize = (height.value * size).sp,
            lineHeight = (height.value * size * 1.05f).sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}

// P (Papa): blauw, wit vierkant van 1/3 in het midden
private fun DrawScope.drawP() {
    drawRect(SignalColors.Blue)
    val w = size.width / 3f
    val h = size.height / 3f
    drawRect(SignalColors.White, topLeft = Offset(w, h), size = Size(w, h))
}

// I (India): geel, zwarte bol met straal 1/4 van de breedte
private fun DrawScope.drawI() {
    drawRect(SignalColors.Yellow)
    drawCircle(SignalColors.Black, radius = size.width * 0.25f, center = center)
}

// Z (Zulu): boven geel, broekingzijde zwart, onder rood, vluchtzijde blauw
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

// U (Uniform): rood linksboven en rechtsonder, verder wit
private fun DrawScope.drawU() {
    drawRect(SignalColors.White)
    val hw = size.width / 2f
    val hh = size.height / 2f
    drawRect(SignalColors.Red, topLeft = Offset.Zero, size = Size(hw, hh))
    drawRect(SignalColors.Red, topLeft = Offset(hw, hh), size = Size(hw, hh))
}

// X (X-ray): wit met blauw kruis, balken 1/5 van de breedte
private fun DrawScope.drawX() {
    drawRect(SignalColors.White)
    val b = size.width / 5f
    drawRect(SignalColors.Blue, topLeft = Offset((size.width - b) / 2f, 0f), size = Size(b, size.height))
    drawRect(SignalColors.Blue, topLeft = Offset(0f, (size.height - b) / 2f), size = Size(size.width, b))
}

// Eerste vervangende (4:3): blauwe driehoek, gele driehoek die de broekingzijde raakt
private fun DrawScope.drawFirstSubstitute() {
    val w = size.width
    val h = size.height
    val outer = Path().apply { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h); close() }
    drawPath(outer, SignalColors.Blue)
    val inner = Path().apply {
        moveTo(0f, h * 0.2f)
        lineTo(w * 0.6325f, h / 2f)
        lineTo(0f, h * 0.8f)
        close()
    }
    drawPath(inner, SignalColors.Yellow)
    drawPath(outer, SignalColors.Edge, style = Stroke(width = 1.5f))
}

// Uitstelwimpel AP (3:1): taps, rood met twee witte banen
private fun DrawScope.drawAnswering() {
    val w = size.width
    val h = size.height
    fun yTop(x: Float) = h * 0.4f * (x / w)                 // bovenrand loopt van 0 naar 0,4h
    fun yBot(x: Float) = h - h * 0.4f * (x / w)             // onderrand van h naar 0,6h
    val shape = Path().apply { moveTo(0f, 0f); lineTo(w, h * 0.4f); lineTo(w, h * 0.6f); lineTo(0f, h); close() }
    drawPath(shape, SignalColors.Red)
    for ((x0, x1) in listOf(0.2f to 0.4f, 0.6f to 0.8067f)) {
        val a = w * x0
        val b = w * x1
        drawPath(
            Path().apply { moveTo(a, yTop(a)); lineTo(b, yTop(b)); lineTo(b, yBot(b)); lineTo(a, yBot(a)); close() },
            SignalColors.White,
        )
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
            .width(height)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Geen vlaggen", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Vlag die eraan komt: vlag, "▲ OP" of "▼ NEER" en de tijd tot dat moment. [main] is de
 * grote kaart "VOLGENDE"; de kleinere kaart is "DAARNA". In de laatste 10 s licht de
 * grote kaart op.
 */
@Composable
fun UpcomingCard(item: UpcomingFlag, main: Boolean, modifier: Modifier = Modifier) {
    val soon = main && item.inSeconds <= 10
    val dirColor = if (item.up) ZzColors.Ok else ZzColors.Horn
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (soon) ZzColors.Horn.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (soon) BorderStroke(2.dp, ZzColors.Horn) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                if (main) "VOLGENDE" else "DAARNA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                SignalFlag(item.flag, if (main) 56.dp else 38.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        item.flag.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (main) 16.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (item.up) "▲ OP" else "▼ NEER",
                        color = dirColor,
                        fontWeight = FontWeight.Black,
                        fontSize = if (main) 15.sp else 12.sp,
                    )
                    Text(
                        "over ${FlagLogic.mmss(item.inSeconds)}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (main) FontWeight.Bold else FontWeight.Medium,
                        fontSize = if (main) 20.sp else 14.sp,
                    )
                }
            }
        }
    }
}

/** "VOLGENDE" en "DAARNA" naast elkaar. */
@Composable
fun UpcomingFlags(items: List<UpcomingFlag>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UpcomingCard(items[0], main = true, modifier = Modifier.weight(1.25f))
        if (items.size > 1) UpcomingCard(items[1], main = false, modifier = Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
    }
}

/** Compacte regel voor de Timer-pagina: kleine vlag + "P-vlag op over 0:45". */
@Composable
fun UpcomingLine(item: UpcomingFlag) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("Volgende vlag: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SignalFlag(item.flag, 22.dp)
        Text(
            "  ${item.flag.label} ${if (item.up) "▲ op" else "▼ neer"} over ${FlagLogic.mmss(item.inSeconds)}",
            fontWeight = FontWeight.Bold,
            color = if (item.inSeconds <= 10) ZzColors.Horn else MaterialTheme.colorScheme.onSurface,
        )
    }
}
