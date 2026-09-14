package com.filish.lab

import com.filish.render.ScreenshotTest
import org.junit.Test

/** Renders each direction as a specimen sheet for side-by-side critique. */
class DirectionsRenderTest : ScreenshotTest() {

    @Test fun a() = shoot("lab-A-vapour", heightDp = 470) { DirectionVapour() }
    @Test fun b() = shoot("lab-B-contact", heightDp = 470) { DirectionContact() }
    @Test fun c() = shoot("lab-C-deep", heightDp = 470) { DirectionDeep() }
    @Test fun d() = shoot("lab-D-crystalline", heightDp = 470) { DirectionCrystalline() }
    @Test fun e() = shoot("lab-E-strata", heightDp = 470) { DirectionStrata() }
}
