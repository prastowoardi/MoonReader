package com.moonreader.app

import com.moonreader.app.data.tracker.AniListParser
import com.moonreader.app.data.tracker.KitsuParser
import com.moonreader.app.data.tracker.MalParser
import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackerParsingTest {

    @Test
    fun anilist_parsesSearchViewerAndMediaList() {
        val searchJson = """
            {
              "data": {
                "Page": {
                  "media": [
                    {
                      "id": 30002,
                      "title": { "userPreferred": "Berserk", "english": "Berserk", "romaji": "Berserk" },
                      "coverImage": { "large": "https://img.example/berserk.jpg" },
                      "chapters": 376,
                      "status": "RELEASING",
                      "siteUrl": "https://anilist.co/manga/30002"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val results = AniListParser.parseSearchResults(searchJson)
        assertEquals(1, results.size)
        assertEquals(30002L, results[0].remoteId)
        assertEquals("Berserk", results[0].title)
        assertEquals(376, results[0].totalChapters)

        val viewerJson = """{ "data": { "Viewer": { "id": 77, "name": "moonuser" } } }"""
        assertEquals("77" to "moonuser", AniListParser.parseViewer(viewerJson))

        val entryJson = """
            {
              "data": {
                "SaveMediaListEntry": {
                  "id": 9901,
                  "status": "PAUSED",
                  "progress": 120,
                  "score": 9.5
                }
              }
            }
        """.trimIndent()
        val fallback = TrackRecord(
            id = "m1:anilist",
            mangaId = "m1",
            trackerId = "anilist",
            remoteId = 30002L,
            title = "Berserk"
        )
        val updated = AniListParser.parseMediaListEntry(entryJson, "SaveMediaListEntry", fallback)
        assertEquals("9901", updated.libraryEntryId)
        assertEquals(TrackStatus.ON_HOLD, updated.status)
        assertEquals(120f, updated.lastChapterRead, 0.01f)
        assertEquals(9.5f, updated.score, 0.01f)
        assertEquals("PLANNING", AniListParser.toAniListStatus(TrackStatus.PLAN_TO_READ))
        assertEquals(
            "Invalid token",
            AniListParser.parseGraphQlError("""{"errors":[{"message":"Invalid token"}]}""")
        )
        assertEquals(null, AniListParser.parseGraphQlError(entryJson))
    }

    @Test
    fun mal_parsesSearchAndMyListStatus() {
        val searchJson = """
            {
              "data": [
                {
                  "node": {
                    "id": 13,
                    "title": "One Piece",
                    "main_picture": { "large": "https://cdn.myanimelist.net/op.jpg" },
                    "num_chapters": 1100,
                    "status": "currently_publishing"
                  }
                }
              ]
            }
        """.trimIndent()

        val results = MalParser.parseSearchResults(searchJson)
        assertEquals(1, results.size)
        assertEquals(13L, results[0].remoteId)
        assertEquals("One Piece", results[0].title)
        assertEquals(1100, results[0].totalChapters)

        val nestedJson = """
            {
              "id": 13,
              "my_list_status": {
                "status": "completed",
                "num_chapters_read": 1100,
                "score": 10
              }
            }
        """.trimIndent()
        val fallback = TrackRecord(
            id = "m2:mal",
            mangaId = "m2",
            trackerId = "mal",
            remoteId = 13L,
            title = "One Piece"
        )
        val updated = MalParser.parseMyListStatus(nestedJson, fallback, nestedInManga = true)
        assertEquals(TrackStatus.COMPLETED, updated.status)
        assertEquals(1100f, updated.lastChapterRead, 0.01f)
        assertEquals(10f, updated.score, 0.01f)
        assertEquals("on_hold", MalParser.toMalStatus(TrackStatus.ON_HOLD))
    }

    @Test
    fun kitsu_parsesSearchAndLibraryEntry() {
        val searchJson = """
            {
              "data": [
                {
                  "id": "14916",
                  "attributes": {
                    "canonicalTitle": "Solo Leveling",
                    "slug": "solo-leveling",
                    "chapterCount": 200,
                    "status": "finished",
                    "posterImage": { "medium": "https://media.kitsu.io/solo.jpg" }
                  }
                }
              ]
            }
        """.trimIndent()

        val results = KitsuParser.parseSearchResults(searchJson)
        assertEquals(1, results.size)
        assertEquals(14916L, results[0].remoteId)
        assertEquals("Solo Leveling", results[0].title)
        assertEquals("https://kitsu.io/manga/solo-leveling", results[0].remoteUrl)

        val collectionJson = """
            {
              "data": [
                {
                  "id": "551234",
                  "attributes": {
                    "status": "current",
                    "progress": 42,
                    "ratingTwenty": 17
                  }
                }
              ]
            }
        """.trimIndent()
        val fallback = TrackRecord(
            id = "m3:kitsu",
            mangaId = "m3",
            trackerId = "kitsu",
            remoteId = 14916L,
            title = "Solo Leveling"
        )
        val updated = KitsuParser.parseLibraryEntry(collectionJson, isCollection = true, fallback = fallback)
        assertEquals("551234", updated.libraryEntryId)
        assertEquals(TrackStatus.READING, updated.status)
        assertEquals(42f, updated.lastChapterRead, 0.01f)
        assertEquals(8.5f, updated.score, 0.01f)
        assertEquals("planned", KitsuParser.toKitsuStatus(TrackStatus.PLAN_TO_READ))
    }
}
