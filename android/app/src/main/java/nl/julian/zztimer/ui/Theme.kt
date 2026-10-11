package nl.julian.zztimer.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Designsysteem van de ZZ Wedstrijd Timer.
 *
 * Rustig en licht: gegroepeerde, witte vlakken op een lichtgrijze achtergrond, één accentkleur
 * (zeeblauw) voor acties en actieve standen. Groen, oranje en rood worden alleen gebruikt waar
 * ze iets betekenen (verbonden / toeter en waarschuwing / stop en fouten). Donker thema volgt
 * de instelling van de telefoon.
 */

/** Kleuren van de app. Altijd via [Zz.colors] gebruiken, zodat licht en donker kloppen. */
@Immutable
data class ZzPalette(
    val background: Color,        // achtergrond achter de gegroepeerde vlakken
    val surface: Color,           // kaarten, lijsten, dialogen
    val surfaceRaised: Color,     // vlak op een vlak (geselecteerd segment, tegels)
    val fill: Color,              // zachte vulling: segmentbalk, uitgeschakelde knop
    val separator: Color,         // haarlijnen
    val label: Color,             // hoofdtekst
    val secondaryLabel: Color,    // ondersteunende tekst
    val tertiaryLabel: Color,     // uitgeschakeld, placeholders
    val accent: Color,
    val onAccent: Color,
    val positive: Color,          // verbonden, vlag op
    val warning: Color,           // toeter, laatste seconden, aandacht
    val warningFill: Color,       // vlak voor dringende instructies
    val onWarningFill: Color,
    val destructive: Color,       // stop, fouten, niet verbonden
    val isDark: Boolean,
)

private val LightPalette = ZzPalette(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    fill = Color(0xFFE6E6EB),
    separator = Color(0xFFD8D8DD),
    label = Color(0xFF111114),
    secondaryLabel = Color(0xFF66666B),
    tertiaryLabel = Color(0xFFA3A3A8),
    accent = Color(0xFF0B63CE),
    onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF1B7F46),
    warning = Color(0xFFB45500),
    warningFill = Color(0xFFFF9F0A),
    onWarningFill = Color(0xFF1A1000),
    destructive = Color(0xFFD12F26),
    isDark = false,
)

private val DarkPalette = ZzPalette(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF3A3A3C),
    fill = Color(0xFF2C2C2E),
    separator = Color(0xFF38383A),
    label = Color(0xFFF5F5F7),
    secondaryLabel = Color(0xFFA9A9AE),
    tertiaryLabel = Color(0xFF6C6C70),
    accent = Color(0xFF4C95FF),
    onAccent = Color(0xFFFFFFFF),
    positive = Color(0xFF34C46A),
    warning = Color(0xFFFF9F0A),
    warningFill = Color(0xFFFF9F0A),
    onWarningFill = Color(0xFF1A1000),
    destructive = Color(0xFFFF5A4F),
    isDark = true,
)

/** Afstanden. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 16.dp            // marge links en rechts van elk scherm
    val section = 28.dp           // tussen groepen
}

/** Hoekafrondingen: weinig verschillende maten, overal dezelfde. */
object Radius {
    val small = 8.dp              // vlaggen, tegels
    val medium = 12.dp            // groepen, knoppen, velden
    val large = 22.dp             // dialogen
}

/** Animatieduur in ms. Kort, zodat de bediening nooit wacht op een animatie. */
object Motion {
    const val FAST = 120
    const val STANDARD = 220
}

/** Typografie, in de geest van iOS maar met de systeemletter van Android. */
object ZzType {
    private val tight = (-0.3).sp
    val largeTitle = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = tight)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.1).sp)
    val bodyEmphasis = body.copy(fontWeight = FontWeight.Medium)
    val callout = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val sectionHeader = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)

    /** Cijfers die niet verspringen (gelijke breedte), voor tijden. */
    val digits = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
}

private val LocalPalette = staticCompositionLocalOf { LightPalette }
private val LocalReduceMotion = staticCompositionLocalOf { false }

object Zz {
    val colors: ZzPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current

    /** true als "Animaties verwijderen" aan staat in de toegankelijkheidsinstellingen. */
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

private fun materialTypography() = Typography(
    headlineSmall = ZzType.title.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = ZzType.headline.copy(fontSize = 20.sp),
    titleMedium = ZzType.headline,
    titleSmall = ZzType.bodyEmphasis,
    bodyLarge = ZzType.body,
    bodyMedium = ZzType.callout,
    bodySmall = ZzType.footnote,
    labelLarge = ZzType.bodyEmphasis,
    labelMedium = ZzType.caption,
    labelSmall = ZzType.caption.copy(fontSize = 11.sp),
)

@Composable
fun ZzTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val scheme = if (p.isDark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.accent.copy(alpha = 0.22f), onPrimaryContainer = p.label,
        secondary = p.accent, onSecondary = p.onAccent,
        secondaryContainer = p.fill, onSecondaryContainer = p.label,
        background = p.background, onBackground = p.label,
        surface = p.surface, onSurface = p.label,
        surfaceVariant = p.fill, onSurfaceVariant = p.secondaryLabel,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.fill,
        inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF111114), inversePrimary = Color(0xFF0B63CE),
        outline = p.tertiaryLabel, outlineVariant = p.separator,
        error = p.destructive, onError = Color.White,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent,
        primaryContainer = p.accent.copy(alpha = 0.12f), onPrimaryContainer = p.label,
        secondary = p.accent, onSecondary = p.onAccent,
        secondaryContainer = p.fill, onSecondaryContainer = p.label,
        background = p.background, onBackground = p.label,
        surface = p.surface, onSurface = p.label,
        surfaceVariant = p.fill, onSurfaceVariant = p.secondaryLabel,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.fill,
        inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color(0xFFF5F5F7), inversePrimary = Color(0xFF4C95FF),
        outline = p.tertiaryLabel, outlineVariant = p.separator,
        error = p.destructive, onError = Color.White,
    )
    CompositionLocalProvider(LocalPalette provides p, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = materialTypography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(Radius.small),
                small = RoundedCornerShape(Radius.small),
                medium = RoundedCornerShape(Radius.medium),
                large = RoundedCornerShape(Radius.medium),
                extraLarge = RoundedCornerShape(Radius.large),
            ),
            content = content,
        )
    }
}
