package com.filish.feature.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.filish.design.Filish
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.FilishAction
import kotlinx.coroutines.launch

/**
 * Buttons that build the device-QA directories.
 *
 * Debug builds only - the call site is inside `if (BuildConfig.DEBUG)`. Each
 * scenario writes into the app's own external files directory, so it needs no
 * permission and uninstalling removes it.
 *
 * The path is shown after building because the tester has to navigate there in
 * FILISH itself, and "somewhere under Android/data" is not a location.
 */
@Composable
fun QaDatasetControls(modifier: Modifier = Modifier) {
    val palette = Filish.palette
    val type = Filish.type
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        BasicTextCompat(
            "Each builds a directory of sparse files - they report a real size but " +
                "occupy almost no space. Browse to " +
                "Android/data/com.filish/files/${QaDataset.ROOT} to use them.",
            type.body.copy(color = palette.ink2),
        )
        Gap(Space.group)

        QaDataset.scenarios.forEach { scenario ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicTextCompat(scenario.title, type.name.copy(color = palette.ink0))
                    BasicTextCompat(scenario.purpose, type.meta.copy(color = palette.ink2))
                }
                Gap(Space.group)
                FilishAction(
                    label = if (busy) "…" else "Build",
                    onClick = {
                        if (busy) return@FilishAction
                        busy = true
                        status = "Building ${scenario.title}…"
                        scope.launch {
                            val dir = QaDataset.build(context, scenario.id)
                            busy = false
                            status = if (dir == null) {
                                "Unknown scenario ${scenario.id}"
                            } else {
                                "${scenario.title}: ${dir.list()?.size ?: 0} entries at ${dir.absolutePath}"
                            }
                        }
                    },
                )
            }
            Gap(Space.group)
        }

        FilishAction(
            label = "Delete all QA datasets",
            onClick = {
                busy = true
                scope.launch {
                    val ok = QaDataset.clear(context)
                    busy = false
                    status = if (ok) "QA datasets removed" else "Nothing to remove"
                }
            },
        )
        status?.let {
            Gap(Space.near)
            BasicTextCompat(it, type.meta.copy(color = palette.signal))
        }
    }
}
