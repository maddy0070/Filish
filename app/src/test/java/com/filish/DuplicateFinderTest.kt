package com.filish

import com.filish.core.intel.DuplicateFinder
import com.filish.core.intel.DuplicateProgress
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random

/**
 * Duplicate detection, against real files on a real filesystem.
 *
 * This runs the whole tiered strategy - group by size, fingerprint the ends,
 * then full digest - over actual bytes, because the interesting failures are
 * exactly the cases a mocked test would not produce: files of identical
 * length that differ only in the middle, files that differ only in their last
 * byte, and files small enough to be shorter than the fingerprint samples.
 */
class DuplicateFinderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val finder = DuplicateFinder()

    private fun write(path: String, bytes: ByteArray): File {
        val f = File(temp.root, path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        return f
    }

    /** Deterministic content of a given size. */
    private fun content(size: Int, seed: Long): ByteArray {
        val rng = Random(seed)
        val b = ByteArray(size)
        rng.nextBytes(b)
        return b
    }

    private fun scan(): DuplicateProgress = runBlocking {
        finder.scan(temp.root.absolutePath, includeHidden = false).last()
    }

    @Test
    fun `identical content in different folders is one group`() {
        val body = content(64 * 1024, seed = 1)
        write("a/photo.jpg", body)
        write("b/copy of photo.jpg", body)
        write("c/unrelated.jpg", content(64 * 1024, seed = 2))

        val result = scan()
        assertTrue(result.complete)
        assertEquals(1, result.groups.size)
        assertEquals(2, result.groups.first().files.size)
        assertEquals(64 * 1024L, result.groups.first().reclaimable)
    }

    @Test
    fun `same size but different content is not a duplicate`() {
        // The property that matters most: a size collision must never be
        // reported as a duplicate. This is the whole reason the full digest
        // step exists.
        write("x.bin", content(128 * 1024, seed = 10))
        write("y.bin", content(128 * 1024, seed = 11))

        val result = scan()
        assertTrue("size collision wrongly reported", result.groups.isEmpty())
    }

    @Test
    fun `files differing only in the middle are not duplicates`() {
        // The cheap fingerprint reads only the head and tail, so these two
        // survive it and must be separated by the full hash.
        val a = content(512 * 1024, seed = 20)
        val b = a.copyOf()
        b[256 * 1024] = (b[256 * 1024] + 1).toByte()
        write("mid-a.bin", a)
        write("mid-b.bin", b)

        val result = scan()
        assertTrue("middle-byte difference missed", result.groups.isEmpty())
    }

    @Test
    fun `files differing only in the last byte are not duplicates`() {
        val a = content(256 * 1024, seed = 30)
        val b = a.copyOf()
        b[b.lastIndex] = (b[b.lastIndex] + 1).toByte()
        write("tail-a.bin", a)
        write("tail-b.bin", b)

        assertTrue(scan().groups.isEmpty())
    }

    @Test
    fun `three copies form one group and reclaim two copies worth`() {
        val body = content(100 * 1024, seed = 40)
        write("one.dat", body)
        write("nested/two.dat", body)
        write("nested/deeper/three.dat", body)

        val result = scan()
        assertEquals(1, result.groups.size)
        assertEquals(3, result.groups.first().files.size)
        assertEquals(200 * 1024L, result.groups.first().reclaimable)
        assertEquals(2, result.totalRedundant)
    }

    @Test
    fun `tiny files are skipped rather than reported`() {
        // Thousands of small identical files exist on every device and
        // recovering them is worth nothing against the cost of the read.
        val small = content(1024, seed = 50)
        write("t1.txt", small)
        write("t2.txt", small)

        assertTrue(scan().groups.isEmpty())
    }

    @Test
    fun `the suggested keep is the oldest copy`() {
        val body = content(64 * 1024, seed = 60)
        val original = write("original.jpg", body)
        val duplicate = write("downloads/original (1).jpg", body)
        original.setLastModified(1_600_000_000_000L)
        duplicate.setLastModified(1_700_000_000_000L)

        val group = scan().groups.single()
        assertEquals(original.absolutePath, group.suggestedKeep.path)
        assertEquals(1, group.redundant.size)
        assertEquals(duplicate.absolutePath, group.redundant.single().path)
    }

    @Test
    fun `an empty tree completes cleanly`() {
        val result = scan()
        assertTrue(result.complete)
        assertTrue(result.groups.isEmpty())
        assertEquals(0L, result.totalReclaimable)
    }

    @Test
    fun `hidden files are excluded unless asked for`() {
        val body = content(64 * 1024, seed = 70)
        write(".hidden/a.bin", body)
        write(".hidden/b.bin", body)

        assertTrue(scan().groups.isEmpty())

        val withHidden = runBlocking {
            finder.scan(temp.root.absolutePath, includeHidden = true).last()
        }
        assertEquals(1, withHidden.groups.size)
    }

    @Test
    fun `the scan reads far less than it walks`() {
        // The point of the tiered strategy. Ten distinct large files share no
        // size with each other, so nothing should be hashed at all.
        for (i in 0 until 10) {
            write("f$i.bin", content(200 * 1024 + i, seed = i.toLong()))
        }
        val result = scan()
        assertEquals(10, result.filesScanned)
        assertTrue(result.groups.isEmpty())
        assertEquals("hashed bytes despite no size collisions", 0L, result.bytesHashed)
    }

    @Test
    fun `a symlinked copy is not reported as a duplicate`() {
        // A link and its target are the same bytes on disk; reporting them
        // would invite the user to reclaim space that does not exist.
        val body = content(64 * 1024, seed = 80)
        val real = write("real.bin", body)
        val link = File(temp.root, "link.bin")
        val made = runCatching {
            java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath())
        }.isSuccess
        org.junit.Assume.assumeTrue("filesystem does not support symlinks", made)

        assertFalse(scan().groups.any { g -> g.files.any { it.path == link.absolutePath } })
    }
}
