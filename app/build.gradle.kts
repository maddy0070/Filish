import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/*
 * ---------------------------------------------------------------------------
 * Clash Display acquisition.
 *
 * Clash Display (Indian Type Foundry, distributed via Fontshare) is licensed
 * under the ITF Free Font License. That licence EXPLICITLY permits embedding
 * the font in a mobile application (FFL 2.0 section 01), but section 02
 * forbids redistributing the font binary through "another ... repository ...
 * or publicly accessible server".
 *
 * Committing ClashDisplay-Variable.ttf into this public git repository would
 * be exactly that kind of redistribution. So the binary is NOT in version
 * control. It is fetched at build time from the official Fontshare endpoint
 * into app/src/main/assets/fonts/ (git-ignored) and embedded into the APK,
 * which is the permitted use.
 *
 * The fetch is best-effort: an offline build still succeeds and the app falls
 * back to a designed system-font stack at runtime (see design/Typography.kt).
 * See docs/platform-constraints.md.
 * ---------------------------------------------------------------------------
 */
val fontAssetDir = layout.projectDirectory.dir("src/main/assets/fonts")

val fetchClashDisplay = tasks.register("fetchClashDisplay") {
    description = "Downloads Clash Display from Fontshare into the asset folder (not committed)."
    val outFile = fontAssetDir.file("ClashDisplay-Variable.ttf").asFile
    outputs.file(outFile)
    outputs.upToDateWhen { outFile.exists() && outFile.length() > 20_000 }
    doLast {
        if (outFile.exists() && outFile.length() > 20_000) {
            logger.lifecycle("FILISH: Clash Display already present (${outFile.length()} bytes).")
            return@doLast
        }
        outFile.parentFile.mkdirs()
        try {
            val bytes = URI("https://api.fontshare.com/v2/fonts/download/clash-display").toURL()
                .openStream().use { it.readBytes() }
            var written = false
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith("ClashDisplay-Variable.ttf")) {
                        val buffer = ByteArrayOutputStream()
                        zip.copyTo(buffer)
                        outFile.writeBytes(buffer.toByteArray())
                        written = true
                        break
                    }
                    entry = zip.nextEntry
                }
            }
            if (written) {
                logger.lifecycle("FILISH: Clash Display embedded (${outFile.length()} bytes).")
            } else {
                logger.warn("FILISH: Clash Display archive had no variable TTF; using fallback type.")
            }
        } catch (t: Throwable) {
            logger.warn("FILISH: could not fetch Clash Display (${t.message}); using fallback type.")
        }
    }
}

/*
 * ---------------------------------------------------------------------------
 * Release signing.
 *
 * An unsigned APK cannot be installed on any device - Android rejects it
 * outright - so a release build that is published for download has to be
 * signed or the download is simply broken.
 *
 * Two paths:
 *
 *   A REAL RELEASE KEY, when the four FILISH_* environment variables are
 *   present. In CI they come from repository secrets; nothing is committed.
 *   This is what a published, upgradable build needs.
 *
 *   THE DEBUG KEY otherwise, so a build from a fresh clone still produces
 *   something installable. The caveat is real and is stated in the release
 *   notes: the debug keystore is generated per machine, so two builds signed
 *   this way have different signatures and Android will refuse to upgrade one
 *   over the other. Uninstall first, or configure the secrets.
 *
 * Never committed: no keystore, no password, no alias.
 * ---------------------------------------------------------------------------
 */
val releaseKeystorePath: String? = System.getenv("FILISH_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("FILISH_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("FILISH_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("FILISH_KEY_PASSWORD")

val hasReleaseKey: Boolean = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    File(releaseKeystorePath).exists()

android {
    namespace = "com.filish"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.filish"
        minSdk = 26
        targetSdk = 35
        // V2: the product pass. Bumped together - a release tagged 1.1.0 that
        // reports itself as 1.0 is a quiet lie, and an unchanged versionCode
        // means Android does not consider it an upgrade at all.
        versionCode = 2
        versionName = "1.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                // Installable, but see the note above: the signature is not
                // stable across machines or CI runs.
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// The font must be on disk before assets are merged into the APK.
tasks.named("preBuild").configure { dependsOn(fetchClashDisplay) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)
    implementation(libs.exifinterface)
    implementation(libs.core.splashscreen)

    testImplementation(libs.junit)
    // Robolectric renders Compose to real bitmaps on the JVM. Without it this
    // project has no way to actually look at its own interface - there is no
    // KVM in the build environment, so a hardware emulator is not available.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
