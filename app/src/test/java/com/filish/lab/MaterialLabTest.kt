package com.filish.lab

import com.filish.render.ScreenshotTest
import org.junit.Test

/**
 * Renders the production glass tokens.
 *
 * These are the pictures the V3 material is signed off against. They import
 * from com.filish.design.glass rather than from the lab's exploration files,
 * so they fail visibly the moment a token stops meaning what it says.
 */
class MaterialLabTest : ScreenshotTest() {

    @Test
    fun tiersDay() = shoot("v3-tiers-day", heightDp = 780) {
        TierLadder(Day, DayPalette)
    }

    @Test
    fun tiersNight() = shoot("v3-tiers-night", dark = true, heightDp = 780) {
        TierLadder(Night, NightPalette)
    }

    @Test
    fun statesDay() = shoot("v3-states-day", heightDp = 800) {
        StateBoard(Day, DayPalette)
    }

    @Test
    fun statesNight() = shoot("v3-states-night", dark = true, heightDp = 800) {
        StateBoard(Night, NightPalette)
    }

    @Test
    fun volumeDay() = shoot("v3-volume-day", heightDp = 240) {
        VolumeBoard(Day, DayPalette)
    }

    @Test
    fun volumeNight() = shoot("v3-volume-night", dark = true, heightDp = 240) {
        VolumeBoard(Night, NightPalette)
    }

    @Test
    fun densityDay() = shoot("v3-density-day", heightDp = 560) {
        DensityBoard(Day, DayPalette)
    }

    @Test
    fun densityNight() = shoot("v3-density-night", dark = true, heightDp = 560) {
        DensityBoard(Night, NightPalette)
    }

    @Test
    fun stripDay() = shoot("v3-strip-day", heightDp = 400) {
        StripTest(Day, DayPalette)
    }

    @Test
    fun stripNight() = shoot("v3-strip-night", dark = true, heightDp = 400) {
        StripTest(Night, NightPalette)
    }
}
