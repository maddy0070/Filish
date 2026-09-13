package com.filish.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Reach
import com.filish.design.Space

/**
 * A binary control.
 *
 * Not a switch with a sliding thumb. A sliding thumb is a metaphor for a
 * physical toggle, and its two states differ mainly by *position*, which is
 * the hardest difference for a glance to read and the one that disappears
 * entirely in greyscale.
 *
 * FILISH uses a filled/unfilled field with a check that resolves in. On is
 * dense and marked; off is hollow. The difference is in weight and in the
 * presence of a symbol, so it survives colour blindness, greyscale and a
 * fast glance down a settings list - which is exactly the context a switch
 * lives in.
 */
@Composable
fun FilishToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = Filish.palette
    val reduce = Filish.a11y.reduceMotion
    val spec = if (reduce) Motion.reduced<Color>() else Motion.quick()

    val fill by animateColorAsState(
        if (checked) palette.signal else Color.Transparent, spec, label = "toggleFill",
    )
    val border by animateColorAsState(
        if (checked) palette.signal else palette.lineStrong,
        if (reduce) Motion.reduced() else Motion.quick(),
        label = "toggleBorder",
    )
    val markAlpha by animateFloatAsState(
        if (checked) 1f else 0f,
        if (reduce) Motion.reduced() else Motion.quick(),
        label = "toggleMark",
    )

    Box(
        modifier
            .size(Reach.touch)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Switch
                this.stateDescription = if (checked) "On" else "Off"
            }
            .pressable(
                onClick = { if (enabled) onCheckedChange(!checked) },
                enabled = enabled,
                role = null,
                shape = RoundedCornerShape(Reach.touch / 2),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(Corner.small + 2.dp))
                .background(if (enabled) fill else fill.copy(alpha = 0.4f))
                .border(
                    1.6.dp,
                    if (enabled) border else border.copy(alpha = 0.4f),
                    RoundedCornerShape(Corner.small + 2.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (markAlpha > 0.01f) {
                Glyph(
                    Glyphs.Check, null, size = 17.dp,
                    tint = palette.inkOn.copy(alpha = markAlpha),
                )
            }
        }
    }
}

/** Emphasis levels for an action. */
enum class ActionWeight { Primary, Secondary, Quiet, Destructive }

/**
 * An action.
 *
 * Flat, squared-off, no shadow: a button is not floating above the sheet it
 * sits on. Weight is carried by fill and ink contrast rather than by
 * elevation, which keeps the depth language honest (see Depth.kt) and keeps
 * the destructive variant unmistakable without making it shout.
 */
@Composable
fun FilishAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: ActionWeight = ActionWeight.Secondary,
    glyph: Glyphs.Glyph? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
) {
    val palette = Filish.palette
    val type = Filish.type

    val (bg, fg, stroke) = when (weight) {
        ActionWeight.Primary -> Triple(palette.signal, palette.inkOn, Color.Transparent)
        ActionWeight.Secondary -> Triple(Color.Transparent, palette.ink0, palette.lineStrong)
        ActionWeight.Quiet -> Triple(Color.Transparent, palette.ink1, Color.Transparent)
        ActionWeight.Destructive -> Triple(palette.danger, palette.inkOn, Color.Transparent)
    }
    val shape = RoundedCornerShape(Corner.token)

    Row(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = Reach.touch)
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.45f))
            .then(
                if (stroke != Color.Transparent) Modifier.border(1.dp, stroke, shape) else Modifier,
            )
            .pressable(onClick = onClick, enabled = enabled, contentDescription = label, shape = shape)
            .padding(horizontal = Space.group + Space.bond, vertical = Space.near + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.near, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, null, size = 18.dp, tint = if (enabled) fg else fg.copy(alpha = 0.45f))
        }
        BasicTextCompat(
            text = label,
            style = type.action.copy(color = if (enabled) fg else fg.copy(alpha = 0.45f)),
            maxLines = 1,
        )
    }
}

/**
 * A chip: a small, toggleable token.
 *
 * Selection is shown by inversion rather than by a tick or a tint, matching
 * how row selection works elsewhere. One selection language, used everywhere.
 */
@Composable
fun FilishChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyphs.Glyph? = null,
    trailingGlyph: Glyphs.Glyph? = null,
    count: Int? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion
    val shape = RoundedCornerShape(Corner.token)

    val bg by animateColorAsState(
        if (selected) palette.selectGround else palette.ground1,
        if (reduce) Motion.reduced() else Motion.quick(), label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) palette.selectInk else palette.ink1,
        if (reduce) Motion.reduced() else Motion.quick(), label = "chipFg",
    )

    Row(
        modifier
            .defaultMinSize(minHeight = 38.dp)
            .clip(shape)
            .background(bg)
            .pressable(
                onClick = onClick, contentDescription = label, shape = shape, role = Role.Tab,
            )
            .padding(horizontal = Space.group, vertical = Space.near),
        horizontalArrangement = Arrangement.spacedBy(Space.near - 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) Glyph(glyph, null, size = 16.dp, tint = fg)
        BasicTextCompat(label, type.action.copy(color = fg), maxLines = 1)
        if (count != null) {
            BasicTextCompat(
                com.filish.core.model.Format.count(count),
                type.metaStrong.copy(color = fg.copy(alpha = 0.65f)),
                maxLines = 1,
            )
        }
        if (trailingGlyph != null) Glyph(trailingGlyph, null, size = 15.dp, tint = fg)
    }
}

/**
 * A single-line input.
 *
 * Underline rather than a boxed field: a box is a container, and a text entry
 * is not a container, it is a place where a line of text is being written.
 * The underline thickens and takes the signal colour on focus, which is the
 * whole of the state change - no floating label animation, because the label
 * never moves in FILISH, it simply sits above.
 */
@Composable
fun FilishField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingGlyph: Glyphs.Glyph? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    focused: Boolean = false,
    textStyle: TextStyle? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    val underline by animateColorAsState(
        if (focused) palette.signal else palette.line,
        if (reduce) Motion.reduced() else Motion.quick(), label = "fieldLine",
    )
    val underlineHeight by animateDpAsState(
        if (focused) 2.dp else 1.dp,
        if (reduce) Motion.reduced() else Motion.quick(), label = "fieldLineH",
    )

    Column(modifier) {
        if (label != null) {
            BasicTextCompat(label.uppercase(), type.eyebrow.copy(color = palette.ink2))
            Box(Modifier.height(Space.near))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingGlyph != null) {
                Glyph(leadingGlyph, null, size = 19.dp, tint = palette.ink2)
                Box(Modifier.width(Space.group - 2.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    BasicTextCompat(
                        placeholder,
                        (textStyle ?: type.body).copy(color = palette.ink2),
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = (textStyle ?: type.body).copy(color = palette.ink0),
                    singleLine = singleLine,
                    cursorBrush = SolidColor(palette.signal),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = imeAction,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onAny = { onImeAction?.invoke() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Box(Modifier.width(Space.near))
                trailing()
            }
        }
        Box(Modifier.height(Space.near))
        Box(Modifier.fillMaxWidth().height(underlineHeight).background(underline))
    }
}

/**
 * Text.
 *
 * A thin wrapper over BasicText so that every string in FILISH goes through
 * one place. Compose's Text lives in Material; FILISH does not depend on
 * Material, and re-implementing the two lines it provides is cheaper than the
 * dependency.
 */
@Composable
fun BasicTextCompat(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Eyebrow label above a group of related rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicTextCompat(
        text.uppercase(),
        Filish.type.eyebrow.copy(color = Filish.palette.ink2),
        modifier,
    )
}

/** Spacer that reads as a relationship rather than a number at the call site. */
@Composable
fun Gap(size: Dp) = Box(Modifier.size(size))
