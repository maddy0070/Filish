package com.filish.lab

import com.filish.render.ScreenshotTest
import org.junit.Test

class SynthesisRenderTest : ScreenshotTest() {

    /** Where the liquid actually belongs: storage as the subject. */
    @Test
    fun volumeDay() = shoot("lab-G-volume-day", heightDp = 260) {
        VolumeSpecimen(DaySkin)
    }

    @Test
    fun volumeNight() = shoot("lab-G-volume-night", heightDp = 260) {
        VolumeSpecimen(NightSkin)
    }

    @Test
    fun day() = shoot("lab-F-substrate-day", heightDp = 470) {
        SynthesisSpecimen(
            DaySkin,
            "F · Substrate & Lens — day",
            "Contact physics + refraction band + a ground with a waterline. " +
                "No blur. The band is kind, selection and signature at once.",
        )
    }

    @Test
    fun night() = shoot("lab-F-substrate-night", heightDp = 470) {
        SynthesisSpecimen(
            NightSkin,
            "F · Substrate & Lens — night",
            "The same material. Air darkens, the volume glows faintly, the " +
                "meniscus becomes the brightest line on screen.",
        )
    }
}
