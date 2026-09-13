package com.filish.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FILISH depth.
 *
 * Shadow is a claim about physical space, and most interfaces make that claim
 * constantly and meaninglessly - a list row is not hovering above anything.
 * FILISH recognises exactly three planes, and a component must earn its way
 * off the ground.
 *
 * GROUND (0)  - the content itself. Never casts a shadow. Lists, headers,
 *               the browser, the storage column. Separation here is done with
 *               tone and space.
 * RAISED (1)  - transient surfaces that genuinely occlude content and will go
 *               away again: sheets, the transfer overlay, the selection
 *               ledger. Wide, soft, low-opacity - light from far away.
 * CARRIED (2) - something the user is physically moving right now. Tighter
 *               and darker, because it is close to the finger and far from
 *               the page.
 *
 * On a dark ground a shadow is nearly invisible, so the same three planes are
 * expressed tonally instead - ground1, ground2, and a hairline. The plane
 * hierarchy is identical; only the medium changes. That is why depth is a
 * named plane in this codebase rather than a dp value passed around.
 */
@Immutable
enum class Plane {
    Ground, Raised, Carried;

    /** Shadow radius. Zero on dark, where tone does this job instead. */
    fun elevation(isDark: Boolean): Dp = when {
        isDark -> 0.dp
        this == Ground -> 0.dp
        this == Raised -> 18.dp
        else -> 8.dp
    }

    /** The ground colour a surface on this plane should paint itself. */
    fun surface(palette: Palette): androidx.compose.ui.graphics.Color = when (this) {
        Ground -> palette.ground0
        Raised -> palette.raised
        Carried -> if (palette.isDark) palette.ground2 else palette.raised
    }

    /** Whether this plane needs a hairline to separate it from what is behind.
     *  On dark, always: tone alone is too subtle at these contrast levels. */
    fun needsOutline(isDark: Boolean): Boolean = this != Ground && isDark
}
