package com.filish.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filish.core.settings.FilishSettings
import com.filish.core.settings.SettingsStore
import com.filish.design.Density
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.ThemeChoice
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishChip
import com.filish.design.component.FilishToggle
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.GlyphButton
import com.filish.design.component.SectionLabel
import kotlinx.coroutines.launch

/**
 * SETTINGS.
 *
 * ---------------------------------------------------------------------------
 * Architecture, not a list of switches
 *
 * Settings screens rot in a specific way: every feature adds one toggle, and
 * after two years there are ninety of them in the order they were written.
 * The defence is not "fewer settings" - a power tool earns its controls - it
 * is that every control has to belong to a group that answers a question the
 * user might actually arrive with:
 *
 *   HOW IT LOOKS      - theme, density, thumbnails
 *   WHAT I SEE        - hidden files, extensions, folder sizes and counts
 *   WHEN THINGS MOVE  - confirmation, conflicts
 *   GETTING AROUND    - reduced motion, contrast
 *   WHAT FILISH DOES  - the privacy position, stated and checkable
 *
 * A setting that fits none of these is usually a setting that should have
 * been a decision.
 *
 * ---------------------------------------------------------------------------
 * Some things are deliberately absent
 *
 * There is no thumbnail-quality slider: quality is derived from row size and
 * available heap, and asking the user to predict the cost of "medium" is
 * handing them our problem. There is no animation master switch: reduced
 * motion already covers the real need, and a second nearly-identical control
 * makes both harder to find. There is no telemetry toggle, because there is
 * no telemetry - the manifest declares no INTERNET permission, so the process
 * cannot send anything anywhere.
 *
 * Every switch here says what it does *for the user*, not what it configures.
 */
@Composable
fun SettingsScreen(
    settings: FilishSettings,
    store: SettingsStore,
    displayFaceAvailable: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier.fillMaxSize().background(palette.ground0),
        contentPadding = PaddingValues(bottom = Space.zone),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Space.gutter - 12.dp, end = Space.gutter, top = Space.group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphButton(Glyphs.ChevronLeft, "Back", onBack, glyphSize = 21.dp)
                Gap(Space.bond)
                BasicTextCompat("Settings", type.title.copy(color = palette.ink0))
            }
            Gap(Space.apart)
        }

        group("How it looks") {
            Choice(
                label = "Theme",
                options = ThemeChoice.entries.map { it.name },
                selected = settings.theme.name,
                onSelect = { scope.launch { store.setTheme(ThemeChoice.valueOf(it)) } },
            )
            Gap(Space.group)
            Choice(
                label = "Density",
                detail = "How much air the file list has.",
                options = Density.entries.map { it.label },
                selected = settings.density.label,
                onSelect = { label ->
                    Density.entries.firstOrNull { it.label == label }?.let {
                        scope.launch { store.setDensity(it) }
                    }
                },
            )
            Gap(Space.group)
            Switch(
                label = "Show thumbnails",
                detail = "Previews for photos and video. Turning this off makes very large " +
                    "folders scroll faster.",
                checked = settings.showThumbnails,
                onChange = { scope.launch { store.setShowThumbnails(it) } },
            )
        }

        group("What I see") {
            Switch(
                label = "Hidden files",
                detail = "Files whose names begin with a dot. Usually created by apps for " +
                    "their own use.",
                checked = settings.showHidden,
                onChange = { scope.launch { store.setShowHidden(it) } },
            )
            Switch(
                label = "File extensions",
                detail = "Show .jpg, .pdf and so on at the end of file names.",
                checked = settings.showExtensions,
                onChange = { scope.launch { store.setShowExtensions(it) } },
            )
            Switch(
                label = "Measure folder sizes",
                detail = "Filish walks each folder in the background so you can see how much " +
                    "space it really takes. Turn this off if you would rather it only " +
                    "measured when you ask.",
                checked = settings.autoMeasureFolders,
                onChange = { scope.launch { store.setAutoMeasure(it) } },
            )
            Switch(
                label = "Count items in folders",
                detail = "Show how many things are inside each folder.",
                checked = settings.showFolderItemCounts,
                onChange = { scope.launch { store.setFolderCounts(it) } },
            )
        }

        group("When things move") {
            Switch(
                label = "Confirm before deleting",
                detail = "Ask first, and say whether the files can be recovered.",
                checked = settings.confirmDelete,
                onChange = { scope.launch { store.setConfirmDelete(it) } },
            )
            Switch(
                label = "Skip the extra step when recovery is possible",
                detail = "When files are going to your device's trash, Android already asks " +
                    "for confirmation. This avoids asking you twice.",
                checked = settings.skipConfirmWhenRecoverable,
                onChange = { scope.launch { store.setSkipConfirmRecoverable(it) } },
            )
            Switch(
                label = "Ask about name clashes",
                detail = "When a file of the same name is already there, stop and show you " +
                    "both. Turning this off keeps both copies automatically.",
                checked = settings.defaultConflictAsk,
                onChange = { scope.launch { store.setConflictAsk(it) } },
            )
        }

        group("Getting around") {
            Switch(
                label = "Reduce motion",
                detail = "Replaces sliding and scaling with quick fades throughout. Nothing " +
                    "is lost - progress and state are still shown.",
                checked = settings.reduceMotion,
                onChange = { scope.launch { store.setReduceMotion(it) } },
            )
            Switch(
                label = "Stronger contrast",
                detail = "Darkens secondary text and strengthens dividing lines.",
                checked = settings.highContrast,
                onChange = { scope.launch { store.setHighContrast(it) } },
            )
        }

        group("Analysis") {
            Switch(
                label = "Include hidden files in storage analysis",
                detail = "Hidden files can account for a surprising amount of space, but " +
                    "they mostly belong to other apps.",
                checked = settings.analysisIncludesHidden,
                onChange = { scope.launch { store.setAnalysisHidden(it) } },
            )
        }


        /*
         * DEVICE QA — debug builds only.
         *
         * Guarded by BuildConfig.DEBUG so a release build cannot reach it at
         * all, and placed last so it never competes with anything a real user
         * came here for. The dataset buttons write only inside the app's own
         * external files directory, so no storage permission is involved and
         * uninstalling removes everything.
         */
        if (com.filish.BuildConfig.DEBUG) {
            group("Device QA (debug build)") {
                Switch(
                    label = "QA readout",
                    detail = "Shows the mass scale, mark spread and measurement state over " +
                        "the browse list. The spine's numbers are invisible on screen, and " +
                        "a tester cannot tell a stable scale from a lucky one.",
                    checked = settings.qaOverlay,
                    onChange = { scope.launch { store.setQaOverlay(it) } },
                )
                Gap(Space.group)
                Switch(
                    label = "Media signatures in the spine",
                    detail = "Tints each photograph's mark with tones sampled from the " +
                        "photograph. The only part of the browse list that costs a decode. " +
                        "OFF until measured on hardware - the kind-tint fallback is a " +
                        "finished design, not a degraded one.",
                    checked = settings.spineMedia,
                    onChange = { scope.launch { store.setSpineMedia(it) } },
                )
            }
            item {
                Gap(Space.group)
                SectionLabel("QA datasets", Modifier.padding(horizontal = Space.gutter))
                Gap(Space.near)
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    com.filish.feature.debug.QaDatasetControls()
                }
            }
        }

        item {
            Gap(Space.zone - 10.dp)
            SectionLabel("What Filish does", Modifier.padding(horizontal = Space.gutter))
            Gap(Space.group)
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Fact(
                    "No network access at all",
                    "Filish does not declare Android's internet permission. The application " +
                        "cannot open a network connection, so nothing about your files can " +
                        "leave this device. This is enforced by the system rather than " +
                        "promised by us - you can verify it in Android's app info screen.",
                )
                Gap(Space.group + 2.dp)
                Fact(
                    "No index of your files is kept",
                    "Filish does not build a database of your storage. Searching and analysis " +
                        "read the filesystem when you ask, every time. Nothing about your " +
                        "files is stored anywhere but the files themselves.",
                )
                Gap(Space.group + 2.dp)
                Fact(
                    "Deleting uses your device's own trash",
                    "Where Android allows it, deleted files go to the system trash and can be " +
                        "recovered until Android removes them. Filish tells you which files " +
                        "those are before you confirm, and reports the exact date your device " +
                        "set. Where it is not possible, it says so instead.",
                )
                Gap(Space.group + 2.dp)
                Fact(
                    "Typeface",
                    if (displayFaceAvailable) {
                        "Set in Clash Display by Indian Type Foundry, embedded in this build " +
                            "under the ITF Free Font License."
                    } else {
                        "Clash Display was not bundled in this build, so Filish is using your " +
                            "device's system typeface. Everything still works; it simply looks " +
                            "less like itself."
                    },
                )
            }
            Gap(Space.zone)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String,
    content: @Composable () -> Unit,
) {
    item {
        Gap(Space.zone - 12.dp)
        SectionLabel(title, Modifier.padding(horizontal = Space.gutter))
        Gap(Space.group)
        Column(Modifier.padding(horizontal = Space.gutter)) { content() }
    }
}

@Composable
private fun Switch(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = Filish.palette
    val type = Filish.type
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.near),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            BasicTextCompat(label, type.name.copy(color = palette.ink0))
            Gap(Space.bond)
            BasicTextCompat(detail, type.meta.copy(color = palette.ink2))
        }
        Gap(Space.near)
        FilishToggle(
            checked = checked,
            onCheckedChange = onChange,
            contentDescription = label,
        )
    }
}

@Composable
private fun Choice(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    detail: String? = null,
) {
    val palette = Filish.palette
    val type = Filish.type
    Column(Modifier.fillMaxWidth()) {
        BasicTextCompat(label, type.name.copy(color = palette.ink0))
        if (detail != null) {
            Gap(Space.bond)
            BasicTextCompat(detail, type.meta.copy(color = palette.ink2))
        }
        Gap(Space.near + 1.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.near)) {
            options.forEach { option ->
                FilishChip(
                    label = option,
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun Fact(headline: String, body: String) {
    val palette = Filish.palette
    val type = Filish.type
    Row(horizontalArrangement = Arrangement.spacedBy(Space.group)) {
        Box(Modifier.padding(top = 3.dp)) {
            Glyph(Glyphs.Check, null, size = 15.dp, tint = palette.ok)
        }
        Column {
            BasicTextCompat(headline, type.metaStrong.copy(color = palette.ink0))
            Gap(Space.bond)
            BasicTextCompat(body, type.meta.copy(color = palette.ink2))
        }
    }
}
