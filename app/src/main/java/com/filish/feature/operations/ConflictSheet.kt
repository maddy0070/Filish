package com.filish.feature.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.filish.core.fs.ops.ConflictPolicy
import com.filish.core.fs.ops.OperationEngine
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishToggle
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.Sheet
import com.filish.design.component.SheetRow

/**
 * A name collision.
 *
 * ---------------------------------------------------------------------------
 * Why this shows both files
 *
 * The standard prompt is "A file with this name already exists. Replace?" -
 * which asks the user to make an irreversible choice about data they cannot
 * see. The only way to answer it correctly is to already know which copy is
 * which, and if they knew that they would not be in this dialog.
 *
 * So FILISH puts the two side by side with the facts that actually
 * distinguish them - size and date - and marks which is newer and which is
 * larger. The decision becomes readable instead of a guess.
 *
 * "Keep both" is offered first and phrased as what it does. It is the only
 * option here that destroys nothing, and in most real collisions it is what
 * the user wants; making it the easy choice is not a nudge, it is putting the
 * safe option where the finger already is.
 *
 * "Apply to the rest" exists because being asked eleven times in a row is its
 * own failure mode - it is how people end up tapping Replace without reading.
 */
@Composable
fun ConflictSheet(
    pending: OperationEngine.PendingConflict?,
    onAnswer: (ConflictPolicy, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    var applyToRest by remember(pending) { mutableStateOf(false) }

    if (pending == null) {
        Sheet(visible = false, onDismiss = onCancel) { }
        return
    }

    val c = pending.conflict

    Sheet(visible = true, onDismiss = onCancel, title = "Already there") {
        BasicTextCompat(
            "${c.source.name} already exists in this folder.",
            type.body.copy(color = palette.ink1),
        )

        Gap(Space.apart)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            Candidate(
                label = "Coming in",
                size = c.source.size,
                modified = c.source.lastModified,
                isNewer = c.sourceIsNewer,
                isLarger = c.source.size > c.existingSize,
                modifier = Modifier.weight(1f),
            )
            Candidate(
                label = "Already here",
                size = c.existingSize,
                modified = c.existingModified,
                isNewer = !c.sourceIsNewer,
                isLarger = c.existingSize > c.source.size,
                modifier = Modifier.weight(1f),
            )
        }

        Gap(Space.apart)

        SheetRow(
            label = "Keep both",
            detail = "The incoming file is renamed. Nothing is lost.",
            glyph = Glyphs.Copy,
            onClick = { onAnswer(ConflictPolicy.KeepBoth, applyToRest) },
        )
        SheetRow(
            label = "Replace",
            detail = "The file already here is overwritten and cannot be recovered.",
            glyph = Glyphs.Move,
            destructive = true,
            onClick = { onAnswer(ConflictPolicy.Overwrite, applyToRest) },
        )
        SheetRow(
            label = "Replace only if newer",
            detail = "Useful when merging a folder into another.",
            glyph = Glyphs.Clock,
            onClick = { onAnswer(ConflictPolicy.OverwriteIfNewer, applyToRest) },
        )
        SheetRow(
            label = "Skip",
            detail = "Leave this one where it is and move on.",
            glyph = Glyphs.Close,
            onClick = { onAnswer(ConflictPolicy.Skip, applyToRest) },
        )

        if (pending.remaining > 1) {
            Gap(Space.group)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilishToggle(
                    checked = applyToRest,
                    onCheckedChange = { applyToRest = it },
                    contentDescription = "Apply this choice to the remaining items",
                )
                Gap(Space.near)
                BasicTextCompat(
                    "Do this for the other ${pending.remaining - 1} items too",
                    type.body.copy(color = palette.ink1),
                )
            }
        }
    }
}

@Composable
private fun Candidate(
    label: String,
    size: Long,
    modified: Long,
    isNewer: Boolean,
    isLarger: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    Column(
        modifier
            .clip(RoundedCornerShape(Corner.token))
            .background(palette.ground1)
            .padding(Space.group),
    ) {
        BasicTextCompat(label.uppercase(), type.eyebrow.copy(color = palette.ink2))
        Gap(Space.near)
        Row(verticalAlignment = Alignment.Bottom) {
            val parts = Format.sizeParts(size)
            BasicTextCompat(parts.value, type.figureLarge.copy(color = palette.ink0))
            Gap(Space.bond)
            Box(Modifier.padding(bottom = 3.dp)) {
                BasicTextCompat(parts.unit, type.meta.copy(color = palette.ink2))
            }
        }
        Gap(Space.bond)
        BasicTextCompat(Format.absoluteDateTime(modified), type.meta.copy(color = palette.ink2))

        // The two facts that settle most real collisions, marked rather than
        // left for the user to derive by comparing two timestamps.
        if (isNewer || isLarger) {
            Gap(Space.near)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
                if (isNewer) Tag("Newer")
                if (isLarger) Tag("Larger")
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    val palette = Filish.palette
    Box(
        Modifier
            .clip(RoundedCornerShape(Corner.small))
            .background(palette.signalWash)
            .padding(horizontal = Space.near, vertical = 2.dp),
    ) {
        BasicTextCompat(
            text,
            Filish.type.eyebrow.copy(color = if (palette.isDark) palette.signal else palette.ink0),
        )
    }
}
