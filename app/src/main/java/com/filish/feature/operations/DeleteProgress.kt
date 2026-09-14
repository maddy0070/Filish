package com.filish.feature.operations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filish.core.fs.ops.DeleteController
import com.filish.core.model.Format
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.Indeterminate
import com.filish.design.component.Progression
import com.filish.design.component.RaisedSurface

/**
 * A delete that is still happening.
 *
 * This surface exists because of a specific failure: a user deleted several
 * gigabytes of RAW files and the interface never changed, so there was no way
 * to tell a working operation from a broken one. Deleting a hundred files
 * means resolving them in the media library, writing each row, and verifying
 * the result - seconds of real work, and silence during it reads as failure.
 *
 * It names the phase rather than showing an abstract bar, because the phases
 * are genuinely different waits and the user can only judge whether something
 * is wrong if they know what is meant to be happening.
 */
@Composable
fun DeleteProgress(
    stage: DeleteController.Stage.Working,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type

    RaisedSurface(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.group + 2.dp, vertical = Space.group)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${stage.what}. ${stage.done} of ${stage.total}."
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.Trash, null, size = 18.dp, tint = palette.signal)
                Gap(Space.near + 1.dp)
                Column(Modifier.weight(1f)) {
                    BasicTextCompat(stage.what, type.name.copy(color = palette.ink0), maxLines = 1)
                    Gap(Space.bond)
                    BasicTextCompat(
                        if (stage.total > 0) {
                            "${Format.count(stage.done)} of ${Format.count(stage.total)}"
                        } else {
                            "Working"
                        },
                        type.meta.copy(color = palette.ink2),
                        maxLines = 1,
                    )
                }
            }
            Gap(Space.group - 2.dp)
            // Determinate once there is something real to count; the early
            // phases genuinely do not know, and a bar that pretends otherwise
            // is the thing that makes progress untrustworthy.
            if (stage.done > 0 && stage.total > 0) {
                Progression(fraction = stage.fraction, accent = palette.signal)
            } else {
                Indeterminate(active = true)
            }
        }
    }
}
