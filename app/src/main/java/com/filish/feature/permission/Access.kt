package com.filish.feature.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * What FILISH is currently allowed to see.
 *
 * Android's storage permissions are three overlapping regimes depending on
 * the release, and a file manager sits awkwardly across all of them. This
 * class exists so the rest of the application never has to reason about that,
 * and - more importantly - so the user can be told the truth about what they
 * are granting and why.
 */
enum class AccessLevel {
    /** Nothing. */
    None,

    /** Per-type media access (API 33+) or legacy read (API 32 and below).
     *  Photos, video and audio are visible; arbitrary files are not. */
    Media,

    /** All-files access. The whole of shared storage. */
    Full,
    ;

    val canBrowseFreely: Boolean get() = this == Full
}

object Access {

    fun level(context: Context): AccessLevel = when {
        hasAllFiles() -> AccessLevel.Full
        hasMedia(context) -> AccessLevel.Media
        else -> AccessLevel.None
    }

    fun hasAllFiles(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun hasMedia(context: Context): Boolean {
        val needed = mediaPermissions()
        return needed.isNotEmpty() && needed.all {
            ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * The system screen where all-files access is granted.
     *
     * Deep-linked to FILISH's own entry where the platform allows it, because
     * the generic list is long and the user has to find us in it. Falls back
     * to the general screen on devices that reject the targeted intent, which
     * some vendor builds do.
     */
    fun allFilesIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val targeted = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        return if (targeted.resolveActivity(context.packageManager) != null) {
            targeted
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    fun appSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * Whether all-files access is even offerable.
     *
     * Below Android 11 the concept does not exist and the legacy permission
     * already grants broad access, so the request would be a dead end.
     */
    fun allFilesAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}
