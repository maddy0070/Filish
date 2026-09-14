package com.filish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.filish.app.FilishRoot
import com.filish.core.settings.FilishSettings
import com.filish.design.Accessibility
import com.filish.design.DarkPalette
import com.filish.design.Filish
import com.filish.design.FilishTheme
import com.filish.design.LightPalette
import com.filish.design.ThemeChoice

/**
 * The application's single window.
 *
 * Edge to edge, with the system bars left transparent and FILISH painting
 * underneath them. A file list that stops short of the top of the screen
 * wastes the most valuable rows on the display, and the status bar reads
 * perfectly well over the ground colour.
 *
 * Configuration changes are handled rather than triggering a recreate (see
 * the manifest). Rotating the phone mid-scroll through a hundred thousand
 * files should not throw away the listing and read it again.
 */
class MainActivity : ComponentActivity() {

    /** Flipped once storage has been enumerated and the first frame is real. */
    private var appReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        /*
         * The system splash is held until FILISH actually has something to
         * show, then handed straight to the Compose opening.
         *
         * Without this, Android 12+ dismisses its splash as soon as the first
         * frame is drawn - which is an empty list - and the app's own opening
         * then plays on top of content that is still arriving. Holding it
         * means the strata the system draws are the same strata the opening
         * picks up, and the two read as one move.
         *
         * The hold is bounded. A device with slow or unreadable storage must
         * not be stuck looking at a splash screen, so after a short ceiling
         * FILISH shows its interface and lets the content fill in.
         */
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !appReady }
        lifecycleScope.launch {
            val warm = launch { runCatching { applicationContext.filish.volumes.volumes() } }
            withTimeoutOrNull(1_200) { warm.join() }
            appReady = true
        }

        // Must be called before setContent so the first frame is already
        // edge to edge - otherwise the opening animation is framed by system
        // bar backgrounds for one frame, which is exactly the kind of flicker
        // a launch is judged on.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                LightPalette.ground0.toArgb(), DarkPalette.ground0.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.auto(
                LightPalette.ground0.toArgb(), DarkPalette.ground0.toArgb(),
            ),
        )
        super.onCreate(savedInstanceState)

        val store = applicationContext.filish.settings

        setContent {
            // Settings arrive asynchronously. Starting from the defaults means
            // the first frame renders immediately rather than waiting on disk,
            // and the stored values fold in a frame or two later.
            val settings by store.settings.collectAsState(initial = FilishSettings())

            FilishTheme(
                themeChoice = settings.theme,
                density = settings.density,
                accessibility = Accessibility(
                    reduceMotion = settings.reduceMotion,
                    highContrast = settings.highContrast,
                ),
            ) {
                Box(Modifier.fillMaxSize().background(Filish.palette.ground0)) {
                    FilishRoot(settings = settings)
                }
            }
        }
    }
}
