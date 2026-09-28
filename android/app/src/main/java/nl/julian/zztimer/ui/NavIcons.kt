package nl.julian.zztimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Eenvoudige iconen voor de navigatiebalk (de kleur komt van de tint). */
object NavIcons {
    val Stopwatch: ImageVector = ImageVector.Builder("stopwatch", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 13f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 20f, y1 = 13f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 4f, y1 = 13f)
            close()
            moveTo(12f, 13f)
            lineTo(12f, 9f)
            moveTo(10f, 2.5f)
            lineTo(14f, 2.5f)
            moveTo(12f, 2.5f)
            lineTo(12f, 5f)
        }
    }.build()

    val Flag: ImageVector = ImageVector.Builder("flag", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(5f, 21f)
            lineTo(5f, 3f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 4f)
            lineTo(19f, 4f)
            lineTo(16f, 8.5f)
            lineTo(19f, 13f)
            lineTo(6f, 13f)
            close()
        }
    }.build()
}
