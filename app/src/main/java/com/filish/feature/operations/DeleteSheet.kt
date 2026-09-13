package com.filish.feature.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.filish.core.fs.ops.DeleteEngine
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishAction
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.Sheet

/**
 * DELETION.
 *
 * ---------------------------------------------------------------------------
 * What this surface has to get right
 *
 * Deleting is the only thing in a file manager that cannot be taken back, and
 * the conventional treatment - a small dialog reading "Delete 4 items?" with
 * OK and Cancel - gives the user almost none of what they need to answer it.
 * It omits how much this is, whether folders are involved, whether it can be
 * undone, and by what mechanism.
 *
 * That last one matters more than it sounds. On this device, deleting a photo
 * and deleting a .zip in a folder you made are *different operations with
 * different outcomes*: the photo goes to the system trash and comes back for
 * weeks; the archive may be gone for good. A dialog that renders both as
 * "Delete?" is not asking a fair question.
 *
 * So this sheet leads with the consequence, in the specific words for the
 * route that will actually be taken, resolved before it is shown (see
 * DeleteEngine.plan). "Recoverable until 12 October" and "Permanent - this
 * cannot be undone" are different decisions and they look different.
 *
 * ---------------------------------------------------------------------------
 * Why the size is prominent
 *
 * Deleting is almost always in service of reclaiming space, so the sheet
 * states what will be reclaimed. It reframes the moment from "are you sure
 * you want to lose this" into "this is what you get back", which is the
 * actual trade the user is making.
 *
 * ---------------------------------------------------------------------------
 * On not double-confirming
 *
 * When the route is the system trash, Android shows its own consent dialog -
 * MediaProvider requires it. FILISH does not stack a second confirmation in
 * front of the platform's, because two dialogs to trash one recoverable photo
 * is a tax rather than a safeguard, and it teaches people to dismiss dialogs
 * without reading them. The extra confirmation is reserved for deletions that
 * are genuinely irreversible, where it is the last thing standing between the
 * user and losing data. This is a preference, because reasonable people
 * disagree.
 */
@Composable
fun DeleteSheet(
    visible: Boolean,
    plan: DeleteEngine.Plan?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    if (plan == null) {
        Sheet(visible = false, onDismiss = onDismiss) { }
        return
    }

    val recoverable = plan.isFullyRecoverable
    val accent = if (recoverable) palette.signal else palette.danger

    Sheet(visible = visible, onDismiss = onDismiss, title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.Trash, null, size = 22.dp, tint = accent)
            Gap(Space.group)
            BasicTextCompat(
                "Delete ${plan.nodes.size} ${if (plan.nodes.size == 1) "item" else "items"}",
                type.heading.copy(color = palette.ink0),
            )
        }

        Gap(Space.apart)

        // What you get back. The reason the user is here.
        Row(verticalAlignment = Alignment.Bottom) {
            val parts = Format.sizeParts(plan.totalBytes)
            BasicTextCompat(parts.value, type.figureHuge.copy(color = palette.ink0))
            Gap(Space.bond)
            Box(Modifier.padding(bottom = 6.dp)) {
                BasicTextCompat(parts.unit, type.heading.copy(color = palette.ink1))
            }
            Gap(Space.near)
            Box(Modifier.padding(bottom = 7.dp)) {
                BasicTextCompat("freed", type.meta.copy(color = palette.ink2))
            }
        }

        Gap(Space.near)
        BasicTextCompat(composition(plan), type.meta.copy(color = palette.ink2))

        Gap(Space.apart)

        // The consequence, stated in the words for the route actually taken.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corner.token))
                .background(if (recoverable) palette.signalWash else palette.dangerWash)
                .padding(Space.group),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.width(3.dp).padding(top = 2.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                        .padding(vertical = 9.dp),
                )
            }
            Gap(Space.group)
            Column {
                BasicTextCompat(
                    plan.consequence,
                    type.metaStrong.copy(color = if (recoverable) palette.ink0 else palette.danger),
                )
                explanation(plan)?.let {
                    Gap(Space.bond + 1.dp)
                    BasicTextCompat(it, type.meta.copy(color = palette.ink1))
                }
            }
        }

        if (plan.folderCount > 0) {
            Gap(Space.group)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.Warning, null, size = 15.dp, tint = palette.warn)
                Gap(Space.near)
                BasicTextCompat(
                    "Folders are deleted with everything inside them.",
                    type.meta.copy(color = palette.ink1),
                )
            }
        }

        Gap(Space.apart)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            FilishAction("Keep", onDismiss, Modifier.weight(1f), weight = ActionWeight.Secondary)
            FilishAction(
                if (recoverable) "Move to trash" else "Delete permanently",
                onConfirm,
                Modifier.weight(1.4f),
                weight = if (recoverable) ActionWeight.Primary else ActionWeight.Destructive,
            )
        }
    }
}

private fun composition(plan: DeleteEngine.Plan): String {
    val parts = ArrayList<String>(2)
    if (plan.fileCount > 0) parts.add(Format.plural(plan.fileCount, "file", "files"))
    if (plan.folderCount > 0) parts.add(Format.plural(plan.folderCount, "folder", "folders"))
    return parts.joinToString(", ")
}

/**
 * The second line: *why* the outcome is what it is.
 *
 * Stating the mechanism rather than only the result is what lets a user learn
 * the shape of the platform they are on, instead of experiencing deletion as
 * arbitrary - sometimes recoverable, sometimes not, for no visible reason.
 */
private fun explanation(plan: DeleteEngine.Plan): String? = when {
    plan.isFullyRecoverable ->
        "These go to your device's trash, where Android keeps them until it " +
            "expires them. Filish will tell you the exact date once they are there."
    plan.isMixed ->
        "Some of these are in your device's media library and can be recovered. " +
            "The rest are not, and will be gone immediately."
    plan.permanentReason == DeleteEngine.PermanentReason.PlatformTooOld ->
        "The system trash was added in Android 11. On this version, deleting is final."
    plan.permanentReason == DeleteEngine.PermanentReason.IsDirectory ->
        "Android's trash holds individual files, not folders, so a folder cannot be " +
            "put in it and taken back out intact."
    plan.permanentReason == DeleteEngine.PermanentReason.OutsideMediaStore ->
        "These are outside the media library Android's trash covers."
    else -> null
}
