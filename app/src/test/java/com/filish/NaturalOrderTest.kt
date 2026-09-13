package com.filish

import com.filish.core.model.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Natural ordering.
 *
 * These are not hypotheticals. Numbered sequences - screenshots, camera
 * output, scans, episodes - are most of what is actually in a phone's
 * storage, and lexicographic ordering gets every one of them wrong in the
 * only way users reliably notice.
 */
class NaturalOrderTest {

    private fun sorted(vararg names: String) = names.sortedWith(NaturalOrder)

    @Test
    fun `numbers order by magnitude, not by digit`() {
        assertEquals(
            listOf("file2.txt", "file9.txt", "file10.txt", "file100.txt"),
            sorted("file10.txt", "file100.txt", "file2.txt", "file9.txt"),
        )
    }

    @Test
    fun `leading zeros do not change magnitude`() {
        assertTrue(NaturalOrder.compare("img007.jpg", "img7.jpg") == 0)
        assertTrue(NaturalOrder.compare("img007.jpg", "img8.jpg") < 0)
    }

    @Test
    fun `ordering ignores case`() {
        assertEquals(
            listOf("Apple", "banana", "Cherry"),
            sorted("Cherry", "banana", "Apple"),
        )
    }

    @Test
    fun `multiple number groups each compare by magnitude`() {
        assertEquals(
            listOf("s1e2", "s1e10", "s2e1"),
            sorted("s2e1", "s1e10", "s1e2"),
        )
    }

    @Test
    fun `a prefix sorts before the longer name containing it`() {
        assertTrue(NaturalOrder.compare("report", "report2") < 0)
    }

    @Test
    fun `real camera filenames order correctly`() {
        assertEquals(
            listOf("IMG_9.jpg", "IMG_10.jpg", "IMG_1000.jpg"),
            sorted("IMG_1000.jpg", "IMG_10.jpg", "IMG_9.jpg"),
        )
    }
}
