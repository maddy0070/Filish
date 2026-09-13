package com.filish.feature.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filish.design.Filish
import com.filish.design.Glyphs
import com.filish.design.Space
import com.filish.design.component.ActionWeight
import com.filish.design.component.BasicTextCompat
import com.filish.design.component.FilishAction
import com.filish.design.component.Gap
import com.filish.design.component.Glyph

/**
 * Asking for storage access.
 *
 * ---------------------------------------------------------------------------
 * The honesty problem
 *
 * All-files access is the broadest storage permission Android grants, and a
 * file manager genuinely needs it - without it, whole regions of the user's
 * own storage are simply invisible, and a file manager that cannot see files
 * is not one. But "genuinely needs it" is what every app that wants it says.
 *
 * So this screen does three things that permission screens usually do not:
 *
 *   It says what FILISH does with the access, in terms of what the user gets,
 *   not in terms of what the app wants.
 *
 *   It says what FILISH cannot do, and why that is structural rather than a
 *   promise: there is no INTERNET permission in the manifest, so the process
 *   cannot open a network socket. Nothing can be sent anywhere because
 *   nothing *can* be sent anywhere. That is a checkable claim, and checkable
 *   claims are the only kind worth making on a permission screen.
 *
 *   It offers the lesser permission as a real option rather than as a
 *   punishment. Media-only access genuinely works for photos, video and
 *   audio, and a user who only wants that should be able to have it and still
 *   use the application.
 * ---------------------------------------------------------------------------
 */
@Composable
fun AccessGate(
    level: AccessLevel,
    allFilesAvailable: Boolean,
    onRequestAllFiles: () -> Unit,
    onRequestMedia: () -> Unit,
    onContinueLimited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Filish.palette
    val type = Filish.type

    Box(
        modifier.fillMaxSize().background(palette.ground0).padding(Space.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Glyph(Glyphs.Storage, null, size = 32.dp, tint = palette.signal)
            Gap(Space.apart)

            BasicTextCompat(
                if (level == AccessLevel.None) {
                    "Filish needs to see your storage"
                } else {
                    "Filish can only see part of your storage"
                },
                type.title.copy(color = palette.ink0),
            )
            Gap(Space.group)
            BasicTextCompat(
                if (level == AccessLevel.None) {
                    "To browse, organise and analyse your files, Filish needs permission to " +
                        "read them. Without it there is nothing to show you."
                } else {
                    "Photos, video and audio are visible. Documents, downloads, archives and " +
                        "anything in a folder you made yourself are not - Android hides those " +
                        "unless an app has all-files access."
                },
                type.body.copy(color = palette.ink1),
            )

            Gap(Space.apart)
            Assurance(
                "Nothing leaves your device",
                "Filish does not declare the internet permission. The application cannot open " +
                    "a network connection at all - not for analytics, not for crash reports, " +
                    "not for anything. This is enforced by Android, not promised by us.",
            )
            Gap(Space.group + 2.dp)
            Assurance(
                "Nothing is uploaded or indexed elsewhere",
                "File names, folder structure and storage analysis stay on this device and are " +
                    "computed here, every time.",
            )

            Gap(Space.zone - 8.dp)

            if (allFilesAvailable) {
                FilishAction(
                    "Allow access to all files",
                    onRequestAllFiles,
                    weight = ActionWeight.Primary,
                    fillWidth = true,
                )
                Gap(Space.near)
            }
            if (level == AccessLevel.None) {
                FilishAction(
                    "Allow photos, video and audio only",
                    onRequestMedia,
                    weight = ActionWeight.Secondary,
                    fillWidth = true,
                )
            } else {
                FilishAction(
                    "Continue with what I have allowed",
                    onContinueLimited,
                    weight = ActionWeight.Secondary,
                    fillWidth = true,
                )
            }
        }
    }
}

@Composable
private fun Assurance(headline: String, detail: String) {
    val palette = Filish.palette
    val type = Filish.type
    Row(horizontalArrangement = Arrangement.spacedBy(Space.group)) {
        Box(Modifier.padding(top = 3.dp)) {
            Glyph(Glyphs.Check, null, size = 16.dp, tint = palette.ok)
        }
        Column {
            BasicTextCompat(headline, type.metaStrong.copy(color = palette.ink0))
            Gap(Space.bond)
            BasicTextCompat(detail, type.meta.copy(color = palette.ink2))
        }
    }
}
