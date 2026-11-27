package com.moonreader.app

import com.moonreader.app.data.local.BackupManager
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun `serialize and deserialize roundtrip preserves library data`() {
        val categories = listOf(
            Category(id = 1L, name = "Umum", sortOrder = 0),
            Category(id = 2L, name = "Favorit", sortOrder = 1)
        )
        val mangas = listOf(
            Manga(
                id = "moon_demo:cyber_moon",
                sourceId = "moon_demo",
                url = "cyber_moon",
                title = "Cyber Moon Chronicles",
                author = "Raka",
                genres = listOf("Sci-Fi", "Action"),
                status = MangaStatus.ONGOING,
                inLibrary = true,
                categoryId = 2L
            )
        )
        val chapters = listOf(
            Chapter(
                id = "moon_demo:cyber_moon:ch1",
                mangaId = "moon_demo:cyber_moon",
                url = "cyber_moon/1",
                name = "Chapter 1",
                chapterNumber = 1f,
                read = true,
                lastPageRead = 5
            )
        )

        val json = BackupManager.serialize(categories, mangas, chapters, exportedAt = 123456789L)
        val restored = BackupManager.deserialize(json)

        assertEquals(1, restored.version)
        assertEquals(123456789L, restored.exportedAt)
        assertEquals(2, restored.categories.size)
        assertEquals("Cyber Moon Chronicles", restored.mangas.single().title)
        assertTrue(restored.mangas.single().inLibrary)
        assertEquals(5, restored.chapters.single().lastPageRead)
    }
}
