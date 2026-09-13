package com.filish

import com.filish.core.model.FileKind
import com.filish.core.model.Kinds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * File classification.
 *
 * The dotfile case is the one that matters most: getting it wrong makes every
 * hidden file in a directory claim to be a different type, which is both
 * wrong and visually noisy.
 */
class KindsTest {

    @Test
    fun `a leading dot means hidden, not an extension`() {
        // ".bashrc" is a file named ".bashrc" with no extension - not a file
        // with the extension "bashrc".
        assertEquals("", Kinds.extensionOf(".bashrc"))
        assertEquals("", Kinds.extensionOf(".gitignore"))
        assertEquals(FileKind.Other, Kinds.of(".bashrc", false))
    }

    @Test
    fun `a hidden file with a real extension keeps it`() {
        assertEquals("json", Kinds.extensionOf(".eslintrc.json"))
    }

    @Test
    fun `compound archive extensions are treated as one thing`() {
        // The user thinks of "tar.gz" as the format, not "gz".
        assertEquals("tar.gz", Kinds.extensionOf("backup.tar.gz"))
        assertEquals(FileKind.Archive, Kinds.of("backup.tar.gz", false))
    }

    @Test
    fun `a trailing dot is not an extension`() {
        assertEquals("", Kinds.extensionOf("weird."))
    }

    @Test
    fun `extensions are matched case-insensitively`() {
        assertEquals(FileKind.Image, Kinds.of("PHOTO.JPG", false))
        assertEquals(FileKind.Video, Kinds.of("Clip.MP4", false))
    }

    @Test
    fun `raw images are separated from ordinary images`() {
        // The distinction exists because RAW files are the single largest
        // avoidable storage consumer for anyone who photographs.
        assertEquals(FileKind.RawImage, Kinds.of("DSC01234.ARW", false))
        assertEquals(FileKind.RawImage, Kinds.of("IMG_0001.dng", false))
        assertEquals(FileKind.RawImage, Kinds.of("shot.cr3", false))
        assertEquals(FileKind.Image, Kinds.of("IMG_0001.jpg", false))
    }

    @Test
    fun `a directory is a folder whatever it is called`() {
        assertEquals(FileKind.Folder, Kinds.of("photos.zip", true))
        assertEquals("", Kinds.extensionOf("noextension"))
    }

    @Test
    fun `every kind knows whether Filish can open it`() {
        assertEquals(true, FileKind.Image.hasBuiltInViewer)
        assertEquals(true, FileKind.Pdf.hasBuiltInViewer)
        // Office formats are deliberately NOT claimed - rendering them wrongly
        // is worse than handing them to an app built for them.
        assertEquals(false, FileKind.Document.hasBuiltInViewer)
        assertEquals(false, FileKind.Archive.hasBuiltInViewer)
    }
}
