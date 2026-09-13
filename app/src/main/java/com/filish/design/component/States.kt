package com.filish.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space

/**
 * Nothing here, and why.
 *
 * FILISH does not draw an illustration of an empty box. An illustration
 * occupies the space where the explanation should be, takes a moment to
 * decode, and then says only "empty", which the absence of rows had already
 * established.
 *
 * An empty state has one job: say why this is empty and what to do about it.
 * "This folder is empty" and "nothing here matches your filter" look
 * identical on screen and need completely different responses, so the two are
 * never allowed to render the same words.
 */
@Composable
fun EmptyState(
    headline: String,
    explanation: String,
    modifier: Modifier = Modifier,
    glyph: Glyphs.Glyph? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type

    Box(modifier.fillMaxSize().padding(Space.zone), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 330.dp),
        ) {
            if (glyph != null) {
                Glyph(glyph, null, size = 30.dp, tint = palette.ink2.copy(alpha = 0.6f))
                Gap(Space.group + 2.dp)
            }
            BasicTextCompat(
                headline,
                type.heading.copy(color = palette.ink1, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth(),
            )
            Gap(Space.near)
            BasicTextCompat(
                explanation,
                type.body.copy(color = palette.ink2, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth(),
            )
            if (action != null) {
                Gap(Space.apart)
                action()
            }
        }
    }
}

/**
 * Something went wrong, said properly.
 *
 * Three things, always, in this order: what happened, why it happened when
 * that is knowable, and what the user can do next. "Something went wrong" is
 * banned from this codebase; it is an apology in place of information, and it
 * leaves the user with no move to make.
 */
@Composable
fun ProblemState(
    what: String,
    why: String?,
    whatNext: String?,
    modifier: Modifier = Modifier,
    severity: Severity = Severity.Problem,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val accent = when (severity) {
        Severity.Problem -> palette.danger
        Severity.Caution -> palette.warn
        Severity.Locked -> palette.signal
    }

    Box(modifier.fillMaxSize().padding(Space.zone), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 350.dp),
        ) {
            Glyph(
                when (severity) {
                    Severity.Locked -> Glyphs.Lock
                    else -> Glyphs.Warning
                },
                null, size = 30.dp, tint = accent,
            )
            Gap(Space.group + 2.dp)
            BasicTextCompat(
                what,
                type.heading.copy(color = palette.ink0, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth(),
            )
            if (why != null) {
                Gap(Space.near)
                BasicTextCompat(
                    why,
                    type.body.copy(color = palette.ink1, textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth(),
                )
            }
            if (whatNext != null) {
                Gap(Space.group)
                BasicTextCompat(
                    whatNext,
                    type.meta.copy(color = palette.ink2, textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth(),
                )
            }
            if (action != null) {
                Gap(Space.apart)
                action()
            }
        }
    }
}

enum class Severity { Problem, Caution, Locked }

/**
 * A transient report of what just happened.
 *
 * Appears after an operation and says specifically what was done, with an
 * undo where one genuinely exists. It is never used to say "Done" - an
 * operation that succeeded exactly as asked needs no announcement, and a
 * toast that fires on every success trains people to ignore the one that
 * matters.
 */
@Composable
fun Report(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    severity: Severity? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    val accent = when (severity) {
        Severity.Problem -> palette.danger
        Severity.Caution -> palette.warn
        else -> palette.signal
    }

    RaisedSurface(modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(
                horizontal = Space.group + 2.dp, vertical = Space.group,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.group),
        ) {
            Box(Modifier.width(3.dp).height(30.dp)) {
                androidx.compose.foundation.Canvas(
                    Modifier.fillMaxSize(),
                ) {
                    drawRoundRect(
                        accent,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width, size.width),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                BasicTextCompat(message, type.name.copy(color = palette.ink0), maxLines = 2)
                if (detail != null) {
                    Gap(Space.bond)
                    BasicTextCompat(detail, type.meta.copy(color = palette.ink2), maxLines = 3)
                }
            }
            if (actionLabel != null && onAction != null) {
                FilishAction(actionLabel, onAction, weight = ActionWeight.Quiet)
            }
        }
    }
}
