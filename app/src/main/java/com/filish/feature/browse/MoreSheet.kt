package com.filish.feature.browse

import androidx.compose.runtime.Composable
import com.filish.core.model.FileNode
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.Gap
import com.filish.design.component.Sheet
import com.filish.design.component.SheetRow

/**
 * Actions that did not earn a place in the ledger.
 *
 * The ledger carries the four things done constantly - copy, move, share,
 * delete. Everything else lives here, and the contents change with the
 * selection: rename and properties are single-item actions and simply are not
 * offered for a multi-selection, rather than being offered and then failing.
 * An action that is present but cannot work is worse than one that is absent.
 */
@Composable
fun MoreSheet(
    visible: Boolean,
    nodes: List<FileNode>,
    pinned: Set<String>,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val single = nodes.singleOrNull()

    Sheet(visible = visible, onDismiss = onDismiss, title = single?.name ?: "${nodes.size} items") {
        if (single != null) {
            SheetRow(
                label = "Rename",
                glyph = Glyphs.Rename,
                detail = "The extension is kept unless you change it.",
                onClick = { onDismiss(); onRename() },
            )
            SheetRow(
                label = "Details",
                glyph = Glyphs.Info,
                detail = "Size, dates, metadata and more.",
                onClick = { onDismiss(); onProperties() },
            )
            if (single.isDirectory) {
                val isPinned = single.path in pinned
                SheetRow(
                    label = if (isPinned) "Unpin from Places" else "Pin to Places",
                    glyph = Glyphs.Pin,
                    detail = if (isPinned) null else "Keep this folder one tap away.",
                    onClick = { onPin(single.path); onDismiss() },
                )
            }
        } else {
            SheetRow(
                label = "Details",
                glyph = Glyphs.Info,
                detail = "Available for one item at a time.",
                enabled = false,
                onClick = { },
            )
        }
        Gap(Space.group)
    }
}
