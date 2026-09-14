package com.filish.render

import com.filish.core.model.FileKind
import com.filish.feature.storage.DuplicatesScreen
import com.filish.feature.storage.InvestigationScreen
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

/**
 * The screens that make storage findings actionable, rendered against a real
 * temporary tree so the duplicate scan actually runs.
 */
class StorageRenderTest : ScreenshotTest() {

    @get:Rule
    val temp = TemporaryFolder()

    private fun content(size: Int, seed: Long): ByteArray {
        val b = ByteArray(size)
        Random(seed).nextBytes(b)
        return b
    }

    private fun write(path: String, bytes: ByteArray): File {
        val f = File(temp.root, path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun duplicates() {
        val holiday = content(2_400_000, seed = 1)
        write("DCIM/Camera/IMG_20240612_181402.jpg", holiday)
            .setLastModified(System.currentTimeMillis() - 86_400_000L * 120)
        write("Download/IMG_20240612_181402 (1).jpg", holiday)
        write("WhatsApp/Media/IMG-20240612-WA0007.jpg", holiday)

        val invoice = content(840_000, seed = 2)
        write("Documents/invoice-april.pdf", invoice)
            .setLastModified(System.currentTimeMillis() - 86_400_000L * 300)
        write("Download/invoice-april.pdf", invoice)

        val clip = content(6_200_000, seed = 3)
        write("Movies/holiday-clip.mp4", clip)
            .setLastModified(System.currentTimeMillis() - 86_400_000L * 40)
        write("Download/holiday-clip.mp4", clip)

        // A size collision that is not a duplicate - it must not appear.
        write("a/decoy-one.bin", content(1_000_000, seed = 10))
        write("b/decoy-two.bin", content(1_000_000, seed = 11))

        shoot("40-duplicates") {
            DuplicatesScreen(
                volumePath = temp.root.absolutePath,
                includeHidden = false,
                onOpenFile = {}, onDelete = {}, onBack = {},
            )
        }
    }

    @Test
    fun investigation() {
        // A RAW-heavy folder, which is the finding this screen most often
        // opens for.
        for (i in 1..6) {
            write("DCIM/Raw/DSC0${1840 + i}.ARW", content(1_200_000 + i * 1000, seed = i.toLong()))
        }
        write("DCIM/Camera/IMG_0001.jpg", content(400_000, seed = 99))

        shoot("41-investigation") {
            InvestigationScreen(
                title = "RAW photos are taking up real space",
                roots = listOf(temp.root.absolutePath),
                kinds = setOf(FileKind.RawImage),
                minSize = null,
                olderThan = null,
                includeHidden = false,
                onOpenFile = {}, onReveal = {}, onDelete = {}, onShare = {}, onBack = {},
            )
        }
    }
}
