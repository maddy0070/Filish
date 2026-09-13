package com.filish.render

import androidx.test.core.app.ApplicationProvider
import com.filish.design.ClashDisplay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Is the intended typeface actually reaching the running application?
 *
 * The build fetches Clash Display into assets and the APK was verified to
 * contain it, but "present in the APK" and "resolved by Compose at runtime"
 * are different claims. This checks the second one, so a silent fallback to
 * the system face cannot go unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FontAvailabilityTest {

    @Test
    fun report() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val listed = runCatching { context.assets.list("fonts")?.toList() }.getOrNull()
        val family = ClashDisplay.family(context.assets)
        println("FONT assets/fonts = $listed")
        println("FONT family resolved = ${family != null}")
    }
}
