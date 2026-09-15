package com.filish.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filish.core.model.Format
import com.filish.design.component.BasicTextCompat
import com.filish.design.glass.Mass
import com.filish.feature.browse.BrowseRow
import com.filish.feature.browse.BrowseState
import com.filish.feature.browse.magnitudeOf

/**
 * The debug QA readout.
 *
 * ===========================================================================
 * Why a readout rather than a log
 * ===========================================================================
 *
 * The device questions this round has to answer are mostly about numbers that
 * are invisible on screen: what the mass scale currently is, whether it is
 * still moving, how many folders have settled, what the widest and narrowest
 * marks actually are. A tester watching the spine cannot tell a stable scale
 * from a lucky one, and logcat does not line up with what they are looking at.
 *
 * So this sits on top of the list, shows the state that drives the spine, and
 * updates with it.
 *
 * ===========================================================================
 * It cannot reach a release build
 * ===========================================================================
 *
 * Every call site is inside `if (BuildConfig.DEBUG)`, and the toggle that
 * enables it is too. Nothing here is styled to the design system on purpose -
 * it is deliberately a plain monospace slab, so it can never be mistaken for
 * part of FILISH or quietly become part of the visual language.
 */
private val mono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    lineHeight = 14.sp,
)

@Composable
fun QaOverlay(state: BrowseState, spineMedia: Boolean, modifier: Modifier = Modifier) {
    val items = state.rows.mapNotNull { (it as? BrowseRow.Item)?.node }
    val folders = items.count { it.isDirectory }
    val settled = items.count { node ->
        node.isDirectory && state.folderFacts[node.path]?.measured?.settled == true
    }
    val measuring = items.count { node ->
        node.isDirectory && state.folderFacts[node.path]?.measured?.settled == false
    }
    val magnitudes = items.map { magnitudeOf(it, state.folderFacts[it.path]) }
    val largest = magnitudes.maxOrNull() ?: 0L
    val widths = magnitudes.map { Mass.markWidth(it, state.massScale).value }
    val atFloor = widths.count { it <= Mass.markMin.value + 0.01f }
    val atCeiling = widths.count { it >= Mass.markMax.value - 0.01f }
    // How many perceivably different widths this directory actually produces.
    val distinct = widths.map { (it * 2).toInt() }.distinct().size

    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xE6101418))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Line("QA", state.path.substringAfterLast('/').ifEmpty { state.path })
        Line(
            "rows",
            "${state.rows.size} (${items.size} items, $folders dirs, " +
                "${state.rawCount} raw, ${state.hiddenCount} hidden)",
        )
        Line(
            "scale",
            "${Format.size(state.massScale)} (${state.massScale} B) · " +
                "largest seen ${Format.size(largest)}",
        )
        Line(
            "marks",
            "$distinct distinct widths · $atFloor at floor · $atCeiling at ceiling · " +
                "range ${Mass.markMin.value.toInt()}-${Mass.markMax.value.toInt()}dp",
        )
        Line("measure", "$settled settled · $measuring in flight · ${folders - settled - measuring} pending")
        Line("select", "${state.selection.count} selected · mode=${state.selectionMode}")
        Line("sort", "${state.sort.key} ${if (state.sort.descending) "desc" else "asc"} · group=${state.group}")
        Line("media", if (spineMedia) "SIGNATURES ON (decode cost active)" else "signatures off (kind tints)")
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        BasicTextCompat(
            label.padEnd(8),
            mono.copy(color = Color(0xFF6BA6C7), fontWeight = FontWeight.Bold),
        )
        BasicTextCompat(value, mono.copy(color = Color(0xFFD8DDE2)))
    }
}
