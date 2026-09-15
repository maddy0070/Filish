package com.filish.lab.art

import com.filish.render.ScreenshotTest
import org.junit.Test

/** Three art directions for Mass & Light, same twenty-one objects. */
class VariantsTest : ScreenshotTest() {

    @Test
    fun spine() = shoot("v32-P-spine", dark = true, heightDp = 1320) { SpineVariant() }

    /** The same composition with flat category tints instead of media slivers. */
    @Test
    fun spineNoMedia() = shoot("v32-P-spine-no-media", dark = true, heightDp = 1320) {
        SpineVariant(withMedia = false)
    }

    @Test
    fun plate() = shoot("v32-Q-plate", dark = true, heightDp = 1080) { PlateVariant() }

    @Test
    fun atlas() = shoot("v32-R-atlas", dark = true, heightDp = 1080) { AtlasVariant() }

    // ---- The converged direction and its tests ----------------------------

    @Test
    fun spineConverged() = shoot("v32-S-spine", dark = true, heightDp = 1400) {
        Spine(selectedIndex = 4)
    }

    /**
     * Remove 30%: no media slivers, no gaps between marks, flat environment.
     * Three of the eight treatments in the converged direction.
     */
    @Test
    fun spineStripped() = shoot("v32-S-stripped", dark = true, heightDp = 1400) {
        Spine(
            selectedIndex = 4, media = false, gaps = false,
            gradientEnv = false, breathe = false, label = "S · −30%",
        )
    }

    @Test
    fun selection() = shoot("v32-S-selection", dark = true, heightDp = 900) { SpineSelection() }

    @Test
    fun root() = shoot("v32-S-root", dark = true, heightDp = 780) { Root() }
}
