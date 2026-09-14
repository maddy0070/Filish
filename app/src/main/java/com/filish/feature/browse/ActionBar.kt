package com.filish.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.filish.core.model.Format
import com.filish.design.Corner
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Reach
import com.filish.design.Space
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.Gap
import com.filish.design.component.Glyph
import com.filish.design.component.pressable

/**
 * THE ACTION BAR.
 *
 * ---------------------------------------------------------------------------
 * What was wrong
 *
 * V1 put four 20dp icons in a row - search, filter, layout, plus - all the
 * same size, same weight, none labelled. A beginner could not tell which was
 * which without pressing them, and the most consequential one (create) was the
 * least legible, because a bare "+" does not say what it makes.
 *
 * That is the generic Android overflow row, and it is generic precisely
 * because it treats four unrelated things as interchangeable.
 *
 * ---------------------------------------------------------------------------
 * What this does instead
 *
 * Three concepts, not four icons, and every one of them carries a word.
 *
 *   ARRANGE is the left token and it is the only one that is stateful, so it
 *   shows its state: "14 items · largest first". It is drawn as a bordered
 *   token because the V1 version was plain text that happened to be tappable,
 *   which nobody discovers. Tapping it opens one sheet holding layout, order,
 *   grouping and filtering - one question, one door.
 *
 *   FIND and NEW are labelled actions. The label is the whole point: "New"
 *   tells a beginner something can be made here, which a "+" does not.
 *
 *   SELECT is here because selection was previously undiscoverable. Long press
 *   works and always did, but nothing in the interface said so, and a gesture
 *   nobody knows about is a feature nobody has. Naming it costs one word.
 *
 * Labels are dropped before targets shrink when width runs out: a glyph with
 * no label is still a 48dp target, whereas a cramped label is neither legible
 * nor hittable.
 */
@Composable
fun ActionBar(
    itemCount: Int,
    arrangement: String,
    filterActive: Boolean,
    selectionActive: Boolean,
    compact: Boolean,
    onArrange: () -> Unit,
    onFind: () -> Unit,
    onSelect: () -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type
    val shape = RoundedCornerShape(Corner.token)

    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Arrange: stateful, so it reads as a value you can change rather than
        // as a button that does something.
        Row(
            Modifier
                .weight(1f, fill = false)
                .clip(shape)
                .background(if (filterActive) palette.signalWash else Color.Transparent)
                .border(
                    1.dp,
                    if (filterActive) palette.signal else palette.line,
                    shape,
                )
                .pressable(
                    onClick = onArrange,
                    contentDescription = "Arrange. Currently $itemCount items, $arrangement." +
                        if (filterActive) " A filter is active." else "",
                    shape = shape,
                )
                .padding(start = Space.near + 2.dp, end = Space.near, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextCompat(
                Format.plural(itemCount, "item", "items"),
                type.metaStrong.copy(color = palette.ink0),
                maxLines = 1,
            )
            BasicTextCompat(
                "  ·  $arrangement",
                type.meta.copy(color = palette.ink1),
                Modifier.weight(1f, fill = false),
                maxLines = 1,
            )
            Gap(Space.bond)
            Glyph(Glyphs.ChevronDown, null, size = 13.dp, tint = palette.ink2)
        }

        Gap(Space.near)

        BarAction(Glyphs.Search, "Find", compact, onFind)
        BarAction(
            Glyphs.Select,
            "Select",
            compact,
            onSelect,
            tint = if (selectionActive) palette.signal else palette.ink1,
        )
        BarAction(Glyphs.Plus, "New", compact, onNew, emphasised = true)
    }
}

/**
 * One labelled action.
 *
 * The label sits beside the glyph rather than beneath it, because a
 * two-line control in a single-line bar forces the bar taller for no gain,
 * and beneath-glyph labels are habitually set too small to read.
 */
@Composable
private fun BarAction(
    glyph: Glyphs.Glyph,
    label: String,
    compact: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
    emphasised: Boolean = false,
) {
    val palette = Filish.palette
    val type = Filish.type
    val shape = RoundedCornerShape(Corner.token)
    // Create is the action V1 hid. It gets the accent so that the one thing a
    // beginner most needs to find is the one thing that stands out.
    val ink = tint ?: if (emphasised) palette.signal else palette.ink1

    Row(
        Modifier
            .defaultMinSize(minHeight = Reach.touch, minWidth = if (compact) Reach.touch else 0.dp)
            .clip(shape)
            .pressable(onClick = onClick, contentDescription = label, shape = shape)
            .padding(horizontal = if (compact) 0.dp else Space.near + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Glyph(glyph, null, size = 19.dp, tint = ink)
        if (!compact) {
            Gap(Space.bond + 2.dp)
            BasicTextCompat(label, type.action.copy(color = ink), maxLines = 1)
        }
    }
}
