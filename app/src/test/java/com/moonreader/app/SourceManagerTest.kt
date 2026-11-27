package com.moonreader.app

import com.moonreader.app.data.source.DemoCatalogSource
import com.moonreader.app.data.source.NaturalOrderComparator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagerTest {

    @Test
    fun `natural order comparator sorts page numbers numerically`() {
        val files = listOf("page_10.jpg", "page_2.jpg", "page_1.jpg", "page_20.jpg")
        val sorted = files.sortedWith(NaturalOrderComparator)
        assertEquals(listOf("page_1.jpg", "page_2.jpg", "page_10.jpg", "page_20.jpg"), sorted)
    }

    @Test
    fun `demo catalog returns chapters and pages offline`() = runTest {
        val demo = DemoCatalogSource()
        val popular = demo.getPopularManga(1)
        assertTrue(popular.mangas.isNotEmpty())

        val first = popular.mangas.first()
        val chapters = demo.getChapterList(first)
        assertTrue(chapters.isNotEmpty())

        val pages = demo.getPageList(chapters.first())
        assertEquals(6, pages.size)
        assertTrue(pages.first().imageUrl.startsWith("demo://page/"))
    }
}
