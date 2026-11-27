package com.moonreader.app.domain.source

import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.SourceInfo

data class MangaPageResult(
    val mangas: List<Manga>,
    val hasNextPage: Boolean
)

/**
 * Modular contract modeled after Tachiyomi/Mihon's Source architecture.
 * Any catalog connector (REST API, OPDS feed, local CBZ/ZIP, or custom extension)
 * implements this interface so the UI and repository layers remain source-agnostic.
 */
interface MangaSource {
    val info: SourceInfo

    suspend fun getPopularManga(page: Int = 1): MangaPageResult

    suspend fun getLatestUpdates(page: Int = 1): MangaPageResult

    suspend fun searchManga(page: Int = 1, query: String): MangaPageResult

    suspend fun getMangaDetails(manga: Manga): Manga

    suspend fun getChapterList(manga: Manga): List<Chapter>

    suspend fun getPageList(chapter: Chapter): List<Page>
}
