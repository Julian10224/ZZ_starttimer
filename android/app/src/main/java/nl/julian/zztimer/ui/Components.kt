package nl.julian.zztimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ===================================================================== kop van een scherm

/**
 * Grote paginatitel links, acties rechts. [scrolled] toont een haarlijn zodra de inhoud
 * onder de kop door schuift.
 */
@Composable
fun ScreenHeader(
    title: String,
    scrolled: Boolean,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Zz.colors
    // Compact bij weinig hoogte (liggend op een telefoon), niet op basis van de oriëntatie
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val lineAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(Motion.FAST), label = "kopLijn")
    Column(Modifier.fillMaxWidth().background(c.background).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 48.dp else 60.dp)
                .padding(start = if (navigation != null) Space.xs else Space.screen, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Text(
                title,
                style = if (compact) ZzType.title else ZzType.largeTitle,
                color = c.label,
                maxLines = 2,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            actions()
        }
        HorizontalDivider(thickness = 0.5.dp, color = c.separator.copy(alpha = lineAlpha))
    }
}

/** Ronde icoonknop in de accentkleur, voor de kop. */
@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Zz.colors.accent)
    }
}

// ===================================================================== groepen en rijen

/**
 * Gegroepeerde lijst zoals in Instellingen van iOS: kop boven, wit vlak met rijen, uitleg
 * eronder. Rijen scheid je met [RowDivider].
 */
@Composable
fun Section(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    footerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zz.colors
    Column(modifier.fillMaxWidth()) {
        if (header != null) SectionHeader(header)
        Surface(
            shape = RoundedCornerShape(Radius.medium),
            color = c.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
        if (footer != null) {
            Footnote(
                footer,
                color = if (footerColor == Color.Unspecified) c.secondaryLabel else footerColor,
                modifier = Modifier.padding(start = Space.l, end = Space.l, top = Space.s),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = ZzType.sectionHeader,
        color = Zz.colors.secondaryLabel,
        modifier = modifier
            .padding(start = Space.l, end = Space.l, bottom = Space.s)
            .semantics { heading() },
    )
}

/** Haarlijn tussen rijen, ingesprongen zoals in iOS. */
@Composable
fun RowDivider(inset: Dp = Space.l) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = Zz.colors.separator)
}

/** Rij in een [Section]: titel, optionele subtitel, iets aan het begin en eind. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Zz.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
                else Modifier.semantics(mergeDescendants = true) {}
            )
            .padding(horizontal = Space.l, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.m))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = ZzType.body, color = if (titleColor == Color.Unspecified) c.label else titleColor)
            if (subtitle != null) Text(subtitle, style = ZzType.footnote, color = c.secondaryLabel)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.s))
            trailing()
        }
    }
}

/** Rij met een waarde rechts, bijv. "Firmware   V7B". Bij grote tekst staat de waarde eronder. */
@Composable
fun ValueRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    if (LocalDensity.current.fontScale >= Scale.LARGE_TEXT) {
        ListRow(label, subtitle = value)
        return
    }
    ListRow(label, trailing = {
        Text(
            value,
            style = ZzType.body,
            color = if (valueColor == Color.Unspecified) Zz.colors.secondaryLabel else valueColor,
            textAlign = TextAlign.End,
        )
    })
}

/** Rij die als knop werkt (tekst in accent- of waarschuwingskleur). */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val color = if (destructive) Zz.colors.destructive else Zz.colors.accent
    ListRow(
        title,
        titleColor = color,
        enabled = enabled,
        onClick = onClick,
        leading = icon?.let { { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(22.dp)) } },
    )
}

@Composable
fun Footnote(text: String, modifier: Modifier = Modifier, color: Color = Zz.colors.secondaryLabel, textAlign: TextAlign? = null) {
    Text(text, style = ZzType.footnote, color = color, modifier = modifier, textAlign = textAlign)
}

// ===================================================================== knoppen

/**
 * Filled: de belangrijkste actie (gevuld, witte tekst). Tinted: actie met een eigen betekenis
 * (stop, toeter). Gray: neutrale bijactie. Plain: alleen tekst.
 */
enum class ButtonStyle { Filled, Tinted, Gray, Plain }

/**
 * Knop met een rustige drukreactie (iets kleiner en lichter). [color] is de kleur van de knop:
 * gevuld met witte tekst, getint (lichte achtergrond, gekleurde tekst) of alleen tekst.
 */
@Composable
fun ZzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Filled,
    color: Color = Zz.colors.accent,
    enabled: Boolean = true,
    large: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = Zz.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = Zz.reduceMotion
    val scale by animateFloatAsState(if (pressed && !reduce) 0.97f else 1f, tween(Motion.FAST), label = "knopSchaal")
    val container by animateColorAsState(
        when {
            !enabled && style != ButtonStyle.Plain -> c.fill
            style == ButtonStyle.Filled -> color
            style == ButtonStyle.Tinted -> color.copy(alpha = if (c.isDark) 0.22f else 0.12f)
            style == ButtonStyle.Gray -> c.fill
            else -> Color.Transparent
        },
        tween(Motion.STANDARD), label = "knopKleur",
    )
    val content = when {
        !enabled -> c.tertiaryLabel
        style == ButtonStyle.Filled -> Color.White
        style == ButtonStyle.Gray -> c.label
        color == c.accent -> c.accentText
        else -> color
    }
    Surface(
        shape = RoundedCornerShape(if (large) 14.dp else Radius.medium),
        color = container,
        contentColor = content,
        modifier = modifier
            .heightIn(min = if (large) 60.dp else 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.85f else 1f
            }
            .clip(RoundedCornerShape(if (large) 14.dp else Radius.medium))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = Space.l, vertical = Space.m),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.s))
            }
            Text(
                text,
                style = if (large) ZzType.headline.copy(fontSize = ZzType.headline.fontSize * 1.06f) else ZzType.bodyEmphasis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ===================================================================== segmentkeuze

/**
 * Segmentkeuze zoals op iOS: een zachte balk met een wit, verschuivend segment voor de
 * gekozen optie.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Zz.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fill)
            .padding(3.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected.coerceAtLeast(0), tween(Motion.STANDARD), label = "segment")
        if (selected >= 0) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = offset)
                        .width(segment)
                        .fillMaxHeight()
                        .shadow(if (c.isDark) 0.dp else 1.5.dp, RoundedCornerShape(8.dp))
                        .background(c.surfaceRaised, RoundedCornerShape(8.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = i == selected, enabled = enabled, role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = if (i == selected) ZzType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else ZzType.callout,
                        color = c.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Space.s, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Keuze-capsule: gevuld in de accentkleur als hij gekozen is. [leading] is bijv. een kleine vlag. */
@Composable
fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val c = Zz.colors
    val bg by animateColorAsState(if (selected) c.accent else c.fill, tween(Motion.FAST), label = "keuze")
    val fg by animateColorAsState(if (selected) c.onAccent else c.label, tween(Motion.FAST), label = "keuzeTekst")
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = ZzType.callout.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal), color = fg)
    }
}

// ===================================================================== status

/** Bolletje met tekst, in een zachte capsule. Kleur is nooit de enige informatiedrager. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(color.copy(alpha = if (Zz.colors.isDark) 0.2f else 0.12f), tween(Motion.STANDARD), label = "pil")
    val fg by animateColorAsState(color, tween(Motion.STANDARD), label = "pilTekst")
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ZzType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = fg)
    }
}

/** Status als tekst met bolletje, rechts in een rij. */
@Composable
fun StatusValue(on: Boolean, onText: String, offText: String, onColor: Color) {
    val c = Zz.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) onColor else c.tertiaryLabel))
        Spacer(Modifier.width(6.dp))
        Text(if (on) onText else offText, style = ZzType.body, color = if (on) onColor else c.secondaryLabel)
    }
}

// ===================================================================== invoer

/** Rij met een schakelaar; de hele rij is aan te tikken. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    ListRow(
        title,
        subtitle = subtitle,
        onClick = { onChange(!checked) },
        role = Role.Switch,
        trailing = { ZzSwitch(checked = checked, onCheckedChange = null) },
    )
}

/** Schakelaar in de accentkleur. Met [onCheckedChange] = null bedient de omringende rij hem. */
@Composable
fun ZzSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val c = Zz.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.fill,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun ZzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val c = Zz.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(Radius.medium),
        textStyle = ZzType.body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.separator,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.secondaryLabel,
            cursorColor = c.accent,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

// ===================================================================== dialogen en meldingen

/**
 * Dialoog met dezelfde opbouw overal: titel, inhoud, rechts de bevestiging in de
 * accentkleur (rood als de actie ingrijpend is).
 */
@Composable
fun ZzDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = "Annuleren",
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zz.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        titleContentColor = c.label,
        textContentColor = c.label,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(Radius.large),
        title = { Text(title, style = ZzType.headline.copy(fontSize = ZzType.title.fontSize * 0.9f)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Space.m), content = content) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirmText,
                    style = ZzType.headline,
                    color = when {
                        !confirmEnabled -> c.tertiaryLabel
                        destructive -> c.destructive
                        else -> c.accent
                    },
                )
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it, style = ZzType.body, color = c.accent) } }
        },
    )
}

/** Meldingen onderin, rustig vormgegeven. */
@Composable
fun ZzSnackbarHost(state: SnackbarHostState) {
    val c = Zz.colors
    SnackbarHost(state) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(Radius.medium),
            containerColor = if (c.isDark) Color(0xFF2C2C2E) else Color(0xFF1C1C1E),
            contentColor = Color(0xFFF5F5F7),
            actionColor = Color(0xFF4C95FF),
            modifier = Modifier.padding(horizontal = Space.screen),
        )
    }
}

/** Opvallende instructie, bijv. "DRUK NU OP START VAN DE KLOK". Bewust fel: moet opvallen. */
@Composable
fun InstructionBanner(text: String, urgent: Boolean, modifier: Modifier = Modifier) {
    val c = Zz.colors
    Surface(
        shape = RoundedCornerShape(Radius.medium),
        color = if (urgent) c.warningFill else c.warning.copy(alpha = if (c.isDark) 0.2f else 0.12f),
        contentColor = if (urgent) c.onWarningFill else c.warning,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = if (urgent) ZzType.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else ZzType.headline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = Space.m),
        )
    }
}

/** Kaart met een gekleurde rand-tint, voor waarschuwingen in de inhoud (bijv. geen verbinding). */
@Composable
fun NoticeCard(title: String, text: String, color: Color, modifier: Modifier = Modifier) {
    val c = Zz.colors
    Surface(
        shape = RoundedCornerShape(Radius.medium),
        color = color.copy(alpha = if (c.isDark) 0.18f else 0.09f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ZzType.headline, color = color)
            Text(text, style = ZzType.footnote, color = c.label)
        }
    }
}

/** Ruimte tussen secties. */
@Composable
fun SectionSpacer() = Spacer(Modifier.size(Space.section))

/** Gebruik voor gelijke binnenmarges in losse inhoud van een [Section]. */
val SectionPadding = PaddingValues(horizontal = Space.l, vertical = Space.m)

/** Tekststijl met cijfers van gelijke breedte. */
fun TextStyle.tabular(): TextStyle = this.merge(ZzType.digits)
