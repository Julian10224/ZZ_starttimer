package nl.julian.zztimer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ZzColors {
    val Navy = Color(0xFF0B1B2B)
    val NavySurface = Color(0xFF13283D)
    val NavyRaised = Color(0xFF1B3550)
    val Sea = Color(0xFF4FC3F7)
    val Go = Color(0xFF1E9E5A)
    val Stop = Color(0xFFC62828)
    val Horn = Color(0xFFFF8F00)
    val Ok = Color(0xFF43A047)
    val Error = Color(0xFFE65100)
    val Muted = Color(0xFF8A9BB0)
    val Inactive = Color(0xFF9E9E9E)
}

private val DarkScheme = darkColorScheme(
    primary = ZzColors.Sea,
    onPrimary = Color(0xFF00243A),
    background = ZzColors.Navy,
    onBackground = Color(0xFFE6EEF6),
    surface = ZzColors.NavySurface,
    onSurface = Color(0xFFE6EEF6),
    surfaceVariant = ZzColors.NavyRaised,
    onSurfaceVariant = Color(0xFFB4C3D3),
    surfaceContainer = ZzColors.NavySurface,
    surfaceContainerHigh = ZzColors.NavyRaised,
    outline = Color(0xFF3A5673),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF01579B),
    onPrimary = Color.White,
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF0B1B2B),
    surface = Color.White,
    onSurface = Color(0xFF0B1B2B),
    surfaceVariant = Color(0xFFE3EAF2),
    onSurfaceVariant = Color(0xFF3D4E60),
    outline = Color(0xFF9AAABB),
)

@Composable
fun ZzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
