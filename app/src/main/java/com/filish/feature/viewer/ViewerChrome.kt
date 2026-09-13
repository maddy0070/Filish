package com.filish.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Motion
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.GlyphButton

/**
 * The chrome over a viewer.
 *
 * Always drawn on a gradient rather than a solid bar, and always in white ink.
 * A viewer's background is the content - a photograph, a video frame - and it
 * can be any colour at all, so chrome that adapts to the theme would be
 * illegible over half the images a user owns. A top-down gradient darkens
 * whatever is behind the controls by exactly as much as they need and no
 * more, which a solid bar cannot do without covering the picture.
 *
 * It hides on tap. Immersive viewing is the point of a viewer, and controls
 * that cannot get out of the way mean the user never sees the whole image.
 */
@Composable
fun BoxScope.ViewerChrome(
    visible: Boolean,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    actions: @Composable RowScopeAlias.() -> Unit = {},
    bottom: @Composable (() -> Unit)? = null,
) {
    val reduce = Filish.a11y.reduceMotion
    val type = Filish.type
    val spec = if (reduce) Motion.reduced<Float>() else Motion.base()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spec),
        exit = fadeOut(spec),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                    ),
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = Space.near, top = Space.near, bottom = Space.apart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(
                    Glyphs.ChevronLeft, "Close", onClose,
                    glyphSize = 22.dp, tint = Color.White,
                )
                Gap(Space.bond)
                Column(Modifier.weight(1f)) {
                    BasicTextCompat(
                        title,
                        type.name.copy(color = Color.White),
                        maxLines = 1,
                    )
                    Gap(Space.bond)
                    BasicTextCompat(
                        subtitle,
                        type.meta.copy(color = Color.White.copy(alpha = 0.7f)),
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowScopeAlias.actions()
                }
            }
        }
    }

    if (bottom != null) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(spec),
            exit = fadeOut(spec),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = Space.gutter, end = Space.gutter,
                            top = Space.apart, bottom = Space.group,
                        ),
                ) { bottom() }
            }
        }
    }
}

/** Marker so chrome actions can be written as a plain list of buttons. */
object RowScopeAlias
