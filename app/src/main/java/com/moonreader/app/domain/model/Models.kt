package com.moonreader.app.domain.model

import kotlinx.serialization.Serializable

enum class MangaStatus(val label: String) {
    UNKNOWN("Tidak Diketahui"),
    ONGOING("Berlanjut"),
    COMPLETED("Tamat"),
    HIATUS("Hiatus"),
    CANCELLED("Dibatalkan")
}

enum class ReaderMode(val label: String) {
    WEBTOON("Webtoon (Vertikal)"),
    RTL("Manga (Kanan -> Kiri)"),
    LTR("Komik (Kiri -> Kanan)")
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Ikuti Sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
    AMOLED("AMOLED Hitam")
}

enum class LibraryDisplayMode(val label: String) {
    COMPACT_GRID("Grid Padat"),
    COMFORTABLE_GRID("Grid Nyaman"),
    LIST("Daftar")
}

enum class CatalogMode(val label: String) {
    POPULAR("Populer"),
    LATEST("Terbaru"),
    SEARCH("Pencarian")
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Serializable
data class Manga(
    val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String = "",
    val artist: String = "",
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val coverUrl: String = "",
    val inLibrary: Boolean = false,
    val categoryId: Long = DEFAULT_CATEGORY_ID,
    val unreadCount: Int = 0,
    val totalChapters: Int = 0,
    val lastUpdatedAt: Long = 0L,
    val addedAt: Long = 0L
) {
    companion object {
        const val DEFAULT_CATEGORY_ID = 1L
    }
}

@Serializable
data class Chapter(
    val id: String,
    val mangaId: String,
    val url: String,
    val name: String,
    val chapterNumber: Float = 0f,
    val scanlator: String = "",
    val uploadedAt: Long = 0L,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val totalPages: Int = 0,
    val downloaded: Boolean = false
)

@Serializable
data class Page(
    val index: Int,
    val imageUrl: String
)

@Serializable
data class Category(
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0
)

data class HistoryEntry(
    val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val sourceId: String,
    val chapterName: String,
    val chapterNumber: Float,
    val lastPageRead: Int,
    val readAt: Long
)

data class UpdateEntry(
    val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val mangaCoverUrl: String,
    val chapterName: String,
    val scanlator: String,
    val uploadedAt: Long,
    val read: Boolean,
    val downloaded: Boolean
)

data class DownloadTask(
    val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val chapterName: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val total: Int = 0,
    val error: String? = null
)

data class SourceInfo(
    val id: String,
    val name: String,
    val lang: String,
    val description: String,
    val isLocal: Boolean = false,
    val supportsSearch: Boolean = true,
    val supportsLatest: Boolean = true,
    val isEnabled: Boolean = true
)
