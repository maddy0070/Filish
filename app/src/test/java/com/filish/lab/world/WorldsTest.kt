package com.filish.lab.world

import com.filish.render.ScreenshotTest
import org.junit.Test

/** Four art-direction hypotheses, rendered so they can be compared. */
class WorldsTest : ScreenshotTest() {

    @Test
    fun h() = shoot("v31-H-deep-field", dark = true, heightDp = 780) { DeepField() }

    @Test
    fun i() = shoot("v31-I-core-sample", dark = true, heightDp = 780) { CoreSample() }

    @Test
    fun j() = shoot("v31-J-optical-bench", dark = true, heightDp = 780) { OpticalBench() }

    @Test
    fun k() = shoot("v31-K-atmosphere", dark = true, heightDp = 780) { Atmosphere() }

    // ---- The synthesis, and the two questions it has to answer -------------

    /** Does a variable-width mark read as a profile of the folder? */
    @Test
    fun m() = shoot("v31-M-mass-and-light", dark = true, heightDp = 1080) {
        MassAndLight()
    }

    /**
     * Control: the same screen with the magnitude mark at a fixed width.
     * If the profile is not legible here as a difference, it is decoration.
     */
    @Test
    fun mFlat() = shoot("v31-M-control-no-mass", dark = true, heightDp = 1080) {
        MassAndLight(showMark = false, label = "control · no magnitude mark")
    }

    /**
     * Re-testing a V3 rejection with the cause removed: a storage horizon in
     * the environment, with no hard line and the transition spread over 200dp.
     */
    @Test
    fun mHorizon() = shoot("v31-M-horizon", dark = true, heightDp = 1080) {
        MassAndLight(horizon = 0.62f, label = "M · with a storage horizon")
    }

    /** Remove 30%: no environment gradient, no body gradient, no cut hairline. */
    @Test
    fun mStripped() = shoot("v31-M-stripped", dark = true, heightDp = 1080) {
        MassAndLight(flat = true, label = "M · −30%")
    }
}
