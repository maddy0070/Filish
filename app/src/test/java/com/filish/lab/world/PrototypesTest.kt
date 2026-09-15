package com.filish.lab.world

import com.filish.render.ScreenshotTest
import org.junit.Test

/** The V3.1 prototypes, rendered from the production tokens. */
class PrototypesTest : ScreenshotTest() {

    @Test
    fun objectsNight() = shoot("v31-A-objects-night", dark = true, heightDp = 700) {
        BoardObjects(Night)
    }

    @Test
    fun objectsDay() = shoot("v31-A-objects-day", heightDp = 700) {
        BoardObjects(Day)
    }

    @Test
    fun storageNight() = shoot("v31-B-storage-night", dark = true, heightDp = 860) {
        BoardStorage(Night)
    }

    @Test
    fun storageDay() = shoot("v31-B-storage-day", heightDp = 860) {
        BoardStorage(Day)
    }

    @Test
    fun operationsNight() = shoot("v31-C-operations-night", dark = true, heightDp = 900) {
        BoardOperations(Night)
    }

    @Test
    fun resolutionNight() = shoot("v31-D-resolution-night", dark = true, heightDp = 760) {
        BoardResolution(Night)
    }

    @Test
    fun resolutionDay() = shoot("v31-D-resolution-day", heightDp = 760) {
        BoardResolution(Day)
    }
}
