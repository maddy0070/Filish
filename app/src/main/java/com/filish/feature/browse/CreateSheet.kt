package com.filish.feature.browse

import androidx.compose.runtime.Composable
import com.filish.core.model.Format
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.Gap
import com.filish.design.component.Sheet
import com.filish.design.component.SheetRow

/**
 * "New" - what can arrive in this folder.
 *
 * ---------------------------------------------------------------------------
 * What Add actually means here
 *
 * A filesystem does not let you "create" most things. You cannot create a
 * photograph or a song; they arrive from elsewhere. So a plus button that only
 * makes folders is answering a narrower question than the one the user asked,
 * which is simply: *how do I put something here?*
 *
 * The honest answer has three parts, and this sheet is all three in one place:
 *
 *   MAKE A PLACE - a folder. The common case.
 *   MAKE A DOCUMENT - an empty text file, the one file type that can
 *     genuinely be created from nothing and is useful immediately.
 *   BRING WHAT IS WAITING - files staged for a copy or move. In V1 this lived
 *     in a separate bar at the bottom of the screen with no relationship to
 *     the create action, so a user with files on the clipboard had two
 *     unrelated affordances for the same intention.
 *
 * Deliberately absent: "create archive" and "create from template". The first
 * is not implemented (see platform-constraints) and an option that does
 * nothing is worse than a missing feature. The second is a desktop idea that
 * does not survive contact with a phone.
 */
@Composable
fun CreateSheet(
    visible: Boolean,
    folderName: String,
    clipboard: Clipboard,
    onNewFolder: () -> Unit,
    onNewTextFile: () -> Unit,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(
        visible = visible,
        onDismiss = onDismiss,
        title = if (folderName.isBlank()) "Add to this folder" else "Add to $folderName",
    ) {
        SheetRow(
            label = "New folder",
            glyph = Glyphs.Folder,
            detail = "A place to keep things together.",
            onClick = { onDismiss(); onNewFolder() },
        )
        SheetRow(
            label = "New text file",
            glyph = Glyphs.Text,
            detail = "An empty note you can open and write in.",
            onClick = { onDismiss(); onNewTextFile() },
        )

        if (clipboard.isActive) {
            Gap(Space.group)
            SheetRow(
                label = if (clipboard.move) {
                    "Move ${Format.plural(clipboard.nodes.size, "item", "items")} here"
                } else {
                    "Copy ${Format.plural(clipboard.nodes.size, "item", "items")} here"
                },
                glyph = if (clipboard.move) Glyphs.Move else Glyphs.Copy,
                detail = "Waiting from ${clipboard.originLabel}.",
                onClick = { onDismiss(); onPaste() },
            )
        }
        Gap(Space.group)
    }
}
