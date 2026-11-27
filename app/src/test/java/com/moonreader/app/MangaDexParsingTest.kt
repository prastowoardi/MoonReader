package com.moonreader.app

import com.moonreader.app.data.source.MangaDexParser
import com.moonreader.app.domain.model.MangaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MangaDexParsingTest {

    @Test
    fun `parseMangaList extracts title, author, coverUrl, tags and status`() {
        val sampleJson = """
            {
              "result": "ok",
              "total": 1,
              "offset": 0,
              "limit": 20,
              "data": [
                {
                  "id": "abc-123",
                  "type": "manga",
                  "attributes": {
                    "title": {"en": "Moonlit Blade"},
                    "description": {"en": "A short synopsis"},
                    "status": "ongoing",
                    "tags": [
                      {"attributes": {"name": {"en": "Action"}}},
                      {"attributes": {"name": {"en": "Fantasy"}}}
                    ]
                  },
                  "relationships": [
                    {"type": "author", "attributes": {"name": "A. Author"}},
                    {"type": "cover_art", "attributes": {"fileName": "cover99.jpg"}}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = MangaDexParser.parseMangaList(sampleJson, "mangadex")
        assertFalse(result.hasNextPage)
        assertEquals(1, result.mangas.size)
        val manga = result.mangas.first()
        assertEquals("mangadex:abc-123", manga.id)
        assertEquals("Moonlit Blade", manga.title)
        assertEquals("A. Author", manga.author)
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertEquals("https://uploads.mangadex.org/covers/abc-123/cover99.jpg.512.jpg", manga.coverUrl)
    }

    @Test
    fun `parseChapterFeed filters zero-page external chapters and sorts descending`() {
        val feedJson = """
            {
              "result": "ok",
              "data": [
                {
                  "id": "ch-1",
                  "attributes": {
                    "chapter": "1",
                    "title": "Beginning",
                    "translatedLanguage": "id",
                    "pages": 18,
                    "publishAt": "2026-01-01T00:00:00+00:00"
                  },
                  "relationships": [
                    {"type": "scanlation_group", "attributes": {"name": "Garuda Scans"}}
                  ]
                },
                {
                  "id": "ch-ext",
                  "attributes": {
                    "chapter": "2",
                    "title": "External",
                    "translatedLanguage": "en",
                    "externalUrl": "https://example.com/read",
                    "pages": 0,
                    "publishAt": "2026-01-02T00:00:00+00:00"
                  },
                  "relationships": []
                },
                {
                  "id": "ch-3",
                  "attributes": {
                    "chapter": "3",
                    "title": "",
                    "translatedLanguage": "en",
                    "pages": 22,
                    "publishAt": "2026-01-03T00:00:00+00:00"
                  },
                  "relationships": []
                }
              ]
            }
        """.trimIndent()

        val chapters = MangaDexParser.parseChapterFeed(feedJson, "mangadex:abc-123")
        assertEquals(2, chapters.size)
        assertEquals(3f, chapters[0].chapterNumber)
        assertEquals(1f, chapters[1].chapterNumber)
        assertEquals("ID • Garuda Scans", chapters[1].scanlator)
    }

    @Test
    fun `parseAtHomePages builds valid image URLs`() {
        val atHomeJson = """
            {
              "result": "ok",
              "baseUrl": "https://uploads.mangadex.org",
              "chapter": {
                "hash": "hash123",
                "data": ["p1.jpg", "p2.png"]
              }
            }
        """.trimIndent()

        val pages = MangaDexParser.parseAtHomePages(atHomeJson)
        assertEquals(2, pages.size)
        assertEquals("https://uploads.mangadex.org/data/hash123/p1.jpg", pages[0].imageUrl)
        assertEquals("https://uploads.mangadex.org/data/hash123/p2.png", pages[1].imageUrl)
    }

    @Test
    fun `parseMangaList handles JsonNull and empty array fields safely`() {
        val trickyJson = """
            {
              "result": "ok",
              "total": 1,
              "offset": 0,
              "limit": 20,
              "data": [
                {
                  "id": "xyz-999",
                  "type": "manga",
                  "attributes": {
                    "title": {"ja-ro": "Safe Title"},
                    "description": [],
                    "status": null,
                    "tags": []
                  },
                  "relationships": [
                    {"type": "artist", "attributes": null},
                    {"type": "cover_art", "attributes": {"fileName": "c1.jpg"}}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = MangaDexParser.parseMangaList(trickyJson, "mangadex")
        assertEquals(1, result.mangas.size)
        assertEquals("Safe Title", result.mangas.first().title)
        assertEquals("", result.mangas.first().description)
    }
}
