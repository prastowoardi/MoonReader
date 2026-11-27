package com.moonreader.app

import com.moonreader.app.data.repository.MigrationHelper
import com.moonreader.app.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {

    @Test
    fun `transferChapterProgress copies read threshold, bookmarks and partial page`() {
        val oldChapters = listOf(
            Chapter(id = "o1", mangaId = "old", url = "1", name = "Ch 1", chapterNumber = 1f, read = true),
            Chapter(id = "o2", mangaId = "old", url = "2", name = "Ch 2", chapterNumber = 2f, read = true, bookmark = true),
            Chapter(id = "o3", mangaId = "old", url = "3", name = "Ch 3", chapterNumber = 3f, read = false, lastPageRead = 8)
        )
        val newChapters = listOf(
            Chapter(id = "n1", mangaId = "new", url = "n1", name = "Ch. 1", chapterNumber = 1f),
            Chapter(id = "n2", mangaId = "new", url = "n2", name = "Ch. 2", chapterNumber = 2f),
            Chapter(id = "n3", mangaId = "new", url = "n3", name = "Ch. 3", chapterNumber = 3f),
            Chapter(id = "n4", mangaId = "new", url = "n4", name = "Ch. 4", chapterNumber = 4f)
        )

        val migrated = MigrationHelper.transferChapterProgress(oldChapters, newChapters)
        assertTrue(migrated[0].read)
        assertTrue(migrated[1].read)
        assertTrue(migrated[1].bookmark)
        assertFalse(migrated[2].read)
        assertEquals(8, migrated[2].lastPageRead)
        assertFalse(migrated[3].read)
    }
}
