package com.filish.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Motion
import com.filish.design.Plane
import com.filish.design.Space

/**
 * A surface that has genuinely left the ground.
 *
 * The only component in FILISH permitted to cast a shadow, and only because
 * it really is in front of the content rather than part of it. Everything
 * else separates itself with tone and space. See Depth.kt for why.
 *
 * On a dark ground the shadow is replaced by a tonal lift and a hairline,
 * because a shadow on near-black is invisible and the plane hierarchy still
 * has to read.
 */
@Composable
fun RaisedSurface(
    modifier: Modifier = Modifier,
    plane: Plane = Plane.Raised,
    corner: androidx.compose.ui.unit.Dp = Corner.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = Filish.palette
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .then(
                if (!palette.isDark) {
                    Modifier.shadow(
                        elevation = plane.elevation(false),
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.5f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(plane.surface(palette))
            .then(
                if (plane.needsOutline(palette.isDark)) {
                    Modifier.border(1.dp, palette.line, shape)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/**
 * A sheet that rises from the bottom edge.
 *
 * Enters by rising because it comes from beyond the bottom of the screen -
 * the motion states where the thing came from, which is the whole job of a
 * transition. It leaves the same way. Under reduced motion both become a
 * cross-fade, since the spatial claim is the part that has to go.
 *
 * The scrim is tappable to dismiss and carries its own semantics, so the
 * gesture has a stated, discoverable equivalent for assistive technology
 * rather than being an invisible affordance.
 */
@Composable
fun Sheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissLabel: String = "Close",
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val reduce = Filish.a11y.reduceMotion

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduce) Motion.reduced() else Motion.base(),
        label = "scrim",
    )

    if (scrimAlpha > 0.01f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.scrim.copy(alpha = palette.scrim.alpha * scrimAlpha))
                .semantics { contentDescription = dismissLabel }
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        )
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reduce) {
                fadeIn(Motion.reduced())
            } else {
                slideInVertically(Motion.base()) { it } + fadeIn(Motion.quick())
            },
            exit = if (reduce) {
                fadeOut(Motion.reduced())
            } else {
                slideOutVertically(Motion.leaving()) { it } + fadeOut(Motion.leaving())
            },
        ) {
            RaisedSurface(
                modifier
                    .fillMaxWidth()
                    // Consume the keyboard inset so a sheet containing a field
                    // is not hidden behind the keyboard it summoned.
                    .windowInsetsPadding(WindowInsets.ime),
                corner = Corner.surface,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = Space.gutter,
                            end = Space.gutter,
                            top = Space.group,
                            bottom = Space.apart,
                        ),
                ) {
                    // A grip: the affordance that says this surface can be
                    // pushed back down. Decorative to a screen reader, which
                    // has the scrim's dismiss action instead.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.lineStrong),
                        )
                    }
                    if (title != null) {
                        Gap(Space.group + Space.bond)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicTextCompat(title, type.heading.copy(color = palette.ink0))
                            GlyphButton(
                                com.filish.design.Glyphs.Close, dismissLabel, onDismiss,
                                glyphSize = 19.dp, touchSize = 40.dp, tint = palette.ink2,
                            )
                        }
                    }
                    Gap(Space.group)
                    content()
                }
            }
        }
    }
}

/**
 * A row inside a sheet or a settings group.
 *
 * Note the absence of a divider between rows. Grouping is done by the gap
 * between groups being larger than the gap within one, which is enough -
 * and which leaves the surface quiet instead of striped.
 */
@Composable
fun SheetRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: com.filish.design.Glyphs.Glyph? = null,
    detail: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val ink = when {
        !enabled -> palette.ink2
        destructive -> palette.danger
        else -> palette.ink0
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.token))
            .pressable(
                onClick = onClick,
                enabled = enabled,
                contentDescription = if (detail != null) "$label. $detail" else label,
                shape = RoundedCornerShape(Corner.token),
            )
            .padding(vertical = Space.group - 2.dp, horizontal = Space.near),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, null, size = 21.dp, tint = ink)
            Gap(Space.group + 2.dp)
        }
        Column(Modifier.weight(1f)) {
            BasicTextCompat(label, type.name.copy(color = ink), maxLines = 1)
            if (detail != null) {
                Gap(Space.bond)
                BasicTextCompat(detail, type.meta.copy(color = palette.ink2), maxLines = 2)
            }
        }
        if (trailing != null) {
            Gap(Space.group)
            trailing()
        }
    }
}
