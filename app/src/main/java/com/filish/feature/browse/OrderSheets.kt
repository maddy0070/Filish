package com.filish.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishAction
import com.filish.design.component.FilishField
import com.filish.design.component.Gap
import com.filish.design.component.Sheet

/**
 * Naming something.
 *
 * On rename, the extension is deselected: the cursor selects the stem only,
 * so a user retyping "IMG_2938" into "Kitchen" does not silently destroy the
 * ".jpg" that makes the file openable. It remains editable - it is simply not
 * what gets replaced by the first keystroke.
 *
 * Validation happens on submit with a specific reason, not as a live red
 * border that scolds you for having typed one character so far.
 */
@Composable
fun NameSheet(
    visible: Boolean,
    title: String,
    initial: String,
    actionLabel: String,
    selectStemOnly: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    var value by remember(visible, initial) {
        val stemLength = if (selectStemOnly) {
            val ext = com.filish.core.model.Kinds.extensionOf(initial)
            if (ext.isEmpty()) initial.length else initial.length - ext.length - 1
        } else {
            initial.length
        }
        mutableStateOf(
            TextFieldValue(initial, TextRange(0, stemLength.coerceIn(0, initial.length))),
        )
    }

    LaunchedEffect(visible) {
        if (visible) runCatching { focus.requestFocus() }
    }

    Sheet(visible = visible, onDismiss = onDismiss, title = title) {
        FilishField(
            value = value,
            onValueChange = { value = it },
            placeholder = "Name",
            focused = focused,
            textStyle = type.name,
            onImeAction = { onSubmit(value.text) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
        )

        if (error != null) {
            Gap(Space.near)
            BasicTextCompat(error, type.meta.copy(color = palette.danger))
        }

        Gap(Space.apart)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            FilishAction("Cancel", onDismiss, Modifier.weight(1f), weight = ActionWeight.Secondary)
            FilishAction(
                actionLabel,
                { onSubmit(value.text) },
                Modifier.weight(1f),
                weight = ActionWeight.Primary,
                enabled = value.text.isNotBlank(),
            )
        }
        Gap(Space.group)
    }
}
