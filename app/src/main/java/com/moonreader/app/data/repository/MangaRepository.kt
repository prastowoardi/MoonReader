package com.moonreader.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.moonreader.app.data.local.BackupManager
import com.moonreader.app.data.local.CategoryEntity
import com.moonreader.app.data.local.ChapterEntity
import com.moonreader.app.data.local.HistoryEntity
import com.moonreader.app.data.local.MangaEntity
import com.moonreader.app.data.local.MoonDatabase
import com.moonreader.app.data.local.TachibkParser
import com.moonreader.app.data.local.TrackRecordEntity
import com.moonreader.app.data.source.ChapterDownloader
import com.moonreader.app.data.source.DemoCatalogSource
import com.moonreader.app.data.source.SourceManager
import com.moonreader.app.data.tracker.TrackerManager
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.DownloadTask
import com.moonreader.app.domain.model.HistoryEntry
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackSearchResult
import com.moonreader.app.domain.model.UpdateEntry
import com.moonreader.app.domain.source.MangaPageResult
import com.moonreader.app.domain.source.MangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MangaRepository(
    private val context: Context,
    private val database: MoonDatabase,
    private val sourceManager: SourceManager,
    private val downloader: ChapterDownloader,
    private val trackerManager: TrackerManager
) {
    private val mangaDao = database.mangaDao()
    private val chapterDao = database.chapterDao()
    private val categoryDao = database.categoryDao()
    private val historyDao = database.historyDao()
    private val trackDao = database.trackDao()

    val libraryFlow: Flow<List<Manga>> = mangaDao.observeLibrary().map { rows ->
        rows.map { it.toDomain() }
    }

    val categoriesFlow: Flow<List<Category>> = categoryDao.observeCategories().map { list ->
        list.map { it.toDomain() }
    }

    val historyFlow: Flow<List<HistoryEntry>> = historyDao.observeHistory().map { rows ->
        rows.map { it.toDomain() }
    }

    val updatesFlow: Flow<List<UpdateEntry>> = chapterDao.observeRecentUpdates().map { rows ->
        rows.map { it.toDomain() }
    }

    val downloadTasksFlow: StateFlow<List<DownloadTask>> = downloader.tasks

    suspend fun ensureInitialData() = withContext(Dispatchers.IO) {
        val existingCats = categoryDao.getCategories()
        if (existingCats.isEmpty()) {
            categoryDao.insertCategory(CategoryEntity(id = 1L, name = "Umum", sortOrder = 0))
        }

        val prefs = context.getSharedPreferences("moonreader_seed", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("cleaned_demo_v2", false)) {
            database.withTransaction {
                historyDao.deleteByMangaPrefix("${DemoCatalogSource.SOURCE_ID}:%")
                chapterDao.deleteByMangaPrefix("${DemoCatalogSource.SOURCE_ID}:%")
                mangaDao.deleteBySource(DemoCatalogSource.SOURCE_ID)
            }
            prefs.edit()
                .putBoolean("seeded_v1", true)
                .putBoolean("cleaned_demo_v2", true)
                .apply()
        }
    }

    fun observeManga(mangaId: String): Flow<Manga?> = mangaDao.observeMangaById(mangaId).map { entity ->
        entity?.toDomain()
    }

    fun observeChapters(mangaId: String): Flow<List<Chapter>> = chapterDao.observeChapters(mangaId).map { list ->
        list.map { it.toDomain() }
    }

    suspend fun cacheBrowseManga(manga: Manga) = withContext(Dispatchers.IO) {
        val existing = mangaDao.getMangaById(manga.id)
        if (existing == null) {
            mangaDao.insertIgnore(MangaEntity.fromDomain(manga))
        }
    }

    suspend fun refreshMangaDetailsAndChapters(mangaId: String): Result<Manga> = withContext(Dispatchers.IO) {
        runCatching {
            val currentEntity = mangaDao.getMangaById(mangaId)
                ?: error("Komik tidak ditemukan di cache")
            val current = currentEntity.toDomain()
            val source = sourceManager.getSource(current.sourceId)
                ?: error("Source ${current.sourceId} tidak tersedia")

            val freshDetails = source.getMangaDetails(current).copy(
                inLibrary = current.inLibrary,
                categoryId = current.categoryId,
                addedAt = current.addedAt
            )
            mangaDao.upsertManga(MangaEntity.fromDomain(freshDetails))

            val remoteChapters = source.getChapterList(freshDetails)
            val existingChapters = chapterDao.getChapters(mangaId).associateBy { it.id }

            val merged = remoteChapters.map { remote ->
                val old = existingChapters[remote.id]
                if (old != null) {
                    remote.copy(
                        read = old.read,
                        bookmark = old.bookmark,
                        lastPageRead = old.lastPageRead,
                        downloaded = old.downloaded,
                        totalPages = if (remote.totalPages > 0) remote.totalPages else old.totalPages
                    )
                } else {
                    remote
                }
            }
            if (merged.isNotEmpty()) {
                chapterDao.upsertChapters(merged.map(ChapterEntity::fromDomain))
            }
            freshDetails
        }
    }

    suspend fun refreshLibraryUpdates(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val library = mangaDao.getLibraryMangas()
            var updatedCount = 0
            for (entity in library) {
                val manga = entity.toDomain()
                val source = sourceManager.getSource(manga.sourceId) ?: continue
                val remoteChapters = runCatching { source.getChapterList(manga) }.getOrNull() ?: continue
                val existing = chapterDao.getChapters(manga.id).associateBy { it.id }
                val newCount = remoteChapters.count { it.id !in existing }
                if (newCount > 0) updatedCount += newCount
                val merged = remoteChapters.map { ch ->
                    val old = existing[ch.id]
                    if (old != null) {
                        ch.copy(
                            read = old.read,
                            bookmark = old.bookmark,
                            lastPageRead = old.lastPageRead,
                            downloaded = old.downloaded
                        )
                    } else ch
                }
                chapterDao.upsertChapters(merged.map(ChapterEntity::fromDomain))
            }
            updatedCount
        }
    }

    suspend fun toggleLibrary(mangaId: String, categoryId: Long = Manga.DEFAULT_CATEGORY_ID) = withContext(Dispatchers.IO) {
        val current = mangaDao.getMangaById(mangaId) ?: return@withContext
        val nextInLib = !current.inLibrary
        mangaDao.updateLibraryStatus(
            id = mangaId,
            inLibrary = nextInLib,
            categoryId = if (nextInLib) categoryId else Manga.DEFAULT_CATEGORY_ID,
            addedAt = if (nextInLib) System.currentTimeMillis() else 0L
        )
    }

    suspend fun moveMangaToCategory(mangaId: String, categoryId: Long) = withContext(Dispatchers.IO) {
        val current = mangaDao.getMangaById(mangaId) ?: return@withContext
        mangaDao.updateLibraryStatus(
            id = mangaId,
            inLibrary = true,
            categoryId = categoryId,
            addedAt = if (current.addedAt > 0L) current.addedAt else System.currentTimeMillis()
        )
    }

    suspend fun addCategory(name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext
        val existing = categoryDao.getCategories()
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 1
        categoryDao.insertCategory(CategoryEntity(name = trimmed, sortOrder = nextOrder))
    }

    suspend fun deleteCategory(categoryId: Long) = withContext(Dispatchers.IO) {
        if (categoryId == Manga.DEFAULT_CATEGORY_ID) return@withContext
        mangaDao.reassignCategory(categoryId, Manga.DEFAULT_CATEGORY_ID)
        categoryDao.deleteCategory(categoryId)
    }

    suspend fun loadPagesForChapter(mangaId: String, chapterId: String): Result<List<Page>> = withContext(Dispatchers.IO) {
        runCatching {
            val chapter = chapterDao.getChapterById(chapterId)?.toDomain()
                ?: error("Chapter tidak ditemukan")
            val manga = mangaDao.getMangaById(mangaId)?.toDomain()
                ?: error("Komik tidak ditemukan")

            // Use offline downloaded pages first if available and not demo marker
            if (chapter.downloaded && manga.sourceId != DemoCatalogSource.SOURCE_ID) {
                val cached = downloader.getDownloadedPages(mangaId, chapterId)
                if (!cached.isNullOrEmpty()) return@runCatching cached
            }

            val source = sourceManager.getSource(manga.sourceId)
                ?: error("Source ${manga.sourceId} tidak aktif")
            val pages = source.getPageList(chapter)
            if (pages.isNotEmpty()) {
                chapterDao.updateTotalPages(chapterId, pages.size)
            }
            pages
        }
    }

    suspend fun saveReadingProgress(
        mangaId: String,
        chapterId: String,
        pageIndex: Int,
        totalPages: Int
    ) = withContext(Dispatchers.IO) {
        val existing = chapterDao.getChapterById(chapterId)
        val becameRead = (existing?.read != true) && (totalPages > 0 && pageIndex >= totalPages - 1)
        val isRead = (existing?.read == true) || becameRead
        chapterDao.updateProgress(chapterId, read = isRead, lastPageRead = pageIndex)
        historyDao.upsertHistory(
            HistoryEntity(
                mangaId = mangaId,
                chapterId = chapterId,
                lastPageRead = pageIndex,
                readAt = System.currentTimeMillis()
            )
        )
        if (becameRead) {
            syncTrackedChapterProgress(mangaId)
        }
    }

    suspend fun markChapterRead(chapterId: String, read: Boolean) = withContext(Dispatchers.IO) {
        val current = chapterDao.getChapterById(chapterId) ?: return@withContext
        chapterDao.updateProgress(
            chapterId = chapterId,
            read = read,
            lastPageRead = if (read) current.lastPageRead else 0
        )
        if (read) {
            syncTrackedChapterProgress(current.mangaId)
        }
    }

    suspend fun markAllChaptersRead(mangaId: String, read: Boolean) = withContext(Dispatchers.IO) {
        val ids = chapterDao.getChapters(mangaId).map { it.id }
        if (ids.isNotEmpty()) {
            chapterDao.markAllRead(ids, read)
            if (read) {
                syncTrackedChapterProgress(mangaId)
            }
        }
    }

    suspend fun toggleChapterBookmark(chapterId: String) = withContext(Dispatchers.IO) {
        val current = chapterDao.getChapterById(chapterId) ?: return@withContext
        chapterDao.updateBookmark(chapterId, !current.bookmark)
    }

    suspend fun enqueueChapterDownload(manga: Manga, chapter: Chapter) = withContext(Dispatchers.IO) {
        downloader.enqueueDownload(manga, chapter)
    }

    suspend fun deleteChapterDownload(mangaId: String, chapterId: String) = withContext(Dispatchers.IO) {
        downloader.deleteDownloadedChapter(mangaId, chapterId)
        chapterDao.updateDownloaded(chapterId, false)
    }

    fun clearCompletedDownloads() = downloader.clearCompletedTasks()

    suspend fun removeHistoryEntry(mangaId: String) = withContext(Dispatchers.IO) {
        historyDao.deleteByManga(mangaId)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        historyDao.clearAll()
    }

    suspend fun browseCatalog(
        sourceId: String,
        mode: String,
        query: String,
        page: Int
    ): Result<MangaPageResult> = withContext(Dispatchers.IO) {
        runCatching {
            val source = sourceManager.getSource(sourceId)
                ?: error("Source tidak ditemukan")
            when (mode) {
                "LATEST" -> source.getLatestUpdates(page)
                "SEARCH" -> source.searchManga(page, query)
                else -> source.getPopularManga(page)
            }
        }
    }

    suspend fun isTachibkOrJsonBackupUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.toString().endsWith(".tachibk", ignoreCase = true) ||
            uri.toString().endsWith(".json", ignoreCase = true)
        ) {
            return@withContext true
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val header = ByteArray(2)
                val read = ins.read(header)
                read == 2 && (TachibkParser.isGzipStream(header) || header[0].toInt().toChar() == '{')
            } ?: false
        }.getOrDefault(false)
    }

    suspend fun importLocalCbz(uri: Uri): Result<Manga> = withContext(Dispatchers.IO) {
        val result = sourceManager.localCbzSource.importCbzFromUri(uri)
        result.onSuccess { manga ->
            val inLibManga = manga.copy(inLibrary = true, addedAt = System.currentTimeMillis())
            mangaDao.upsertManga(MangaEntity.fromDomain(inLibManga))
            val chapters = sourceManager.localCbzSource.getChapterList(inLibManga)
            chapterDao.upsertChapters(chapters.map(ChapterEntity::fromDomain))
        }
        result
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val categories = categoryDao.getCategories().map { it.toDomain() }
        val mangas = mangaDao.getLibraryMangas().map { it.toDomain() }
        val chapters = mangas.flatMap { m ->
            chapterDao.getChapters(m.id).map { it.toDomain() }
        }
        BackupManager.serialize(categories, mangas, chapters)
    }

    suspend fun importBackupJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = BackupManager.deserialize(rawJson)
            database.withTransaction {
                payload.categories.forEach { cat ->
                    categoryDao.insertCategory(CategoryEntity(id = cat.id, name = cat.name, sortOrder = cat.sortOrder))
                }
                payload.mangas.forEach { manga ->
                    mangaDao.upsertManga(MangaEntity.fromDomain(manga))
                }
                if (payload.chapters.isNotEmpty()) {
                    chapterDao.upsertChapters(payload.chapters.map(ChapterEntity::fromDomain))
                }
            }
            payload.mangas.size
        }
    }

    suspend fun importBackupUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("File backup tidak dapat dibaca")

            if (TachibkParser.isGzipStream(bytes) || uri.toString().endsWith(".tachibk", ignoreCase = true)) {
                val parsed = TachibkParser.parse(bytes.inputStream())
                database.withTransaction {
                    parsed.categories.forEach { cat ->
                        categoryDao.insertCategory(
                            CategoryEntity(id = cat.id, name = cat.name, sortOrder = cat.sortOrder)
                        )
                    }
                    parsed.mangas.forEach { manga ->
                        mangaDao.upsertManga(MangaEntity.fromDomain(manga))
                    }
                    // Insert chapters in batches so SQLite bind limits are never exceeded
                    parsed.chapters.chunked(500).forEach { batch ->
                        chapterDao.upsertChapters(batch.map(ChapterEntity::fromDomain))
                    }
                    parsed.history.forEach { h ->
                        historyDao.upsertHistory(
                            HistoryEntity(
                                mangaId = h.mangaId,
                                chapterId = h.chapterId,
                                lastPageRead = 0,
                                readAt = h.readAt
                            )
                        )
                    }
                }
                "Restore .tachibk berhasil: ${parsed.mangas.size} komik, ${parsed.chapters.size} chapter, ${parsed.categories.size} kategori"
            } else {
                val count = importBackupJson(bytes.toString(Charsets.UTF_8)).getOrThrow()
                "Restore JSON berhasil ($count komik dipulihkan)"
            }
        }
    }

    fun getEnabledSources(): List<MangaSource> = sourceManager.getEnabledSources()

    suspend fun getMangaWithChapters(mangaId: String): Pair<Manga?, List<Chapter>> = withContext(Dispatchers.IO) {
        val manga = mangaDao.getMangaById(mangaId)?.toDomain()
        val chapters = chapterDao.getChapters(mangaId).map { it.toDomain() }
        manga to chapters
    }

    suspend fun searchSourceForMigration(sourceId: String, query: String): Result<List<Manga>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = sourceManager.getSource(sourceId)
                    ?: error("Source $sourceId tidak ditemukan")
                source.searchManga(1, query).mangas
            }
        }

    suspend fun previewMigrationTarget(targetManga: Manga): Result<Pair<Manga, List<Chapter>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = sourceManager.getSource(targetManga.sourceId)
                    ?: error("Source ${targetManga.sourceId} tidak tersedia")
                val details = runCatching { source.getMangaDetails(targetManga) }.getOrDefault(targetManga)
                val chapters = source.getChapterList(details)
                details to chapters
            }
        }

    suspend fun migrateManga(
        oldMangaId: String,
        targetManga: Manga,
        targetChapters: List<Chapter>,
        copyReadProgress: Boolean,
        keepCategory: Boolean,
        deleteOldManga: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val oldManga = mangaDao.getMangaById(oldMangaId)?.toDomain()
                ?: error("Komik lama tidak ditemukan")
            val oldChapters = chapterDao.getChapters(oldMangaId).map { it.toDomain() }

            val finalChapters = if (copyReadProgress) {
                MigrationHelper.transferChapterProgress(oldChapters, targetChapters)
            } else {
                targetChapters
            }

            val finalTargetManga = targetManga.copy(
                inLibrary = true,
                categoryId = if (keepCategory) oldManga.categoryId else Manga.DEFAULT_CATEGORY_ID,
                addedAt = if (keepCategory && oldManga.addedAt > 0L) oldManga.addedAt else System.currentTimeMillis()
            )

            database.withTransaction {
                mangaDao.upsertManga(MangaEntity.fromDomain(finalTargetManga))
                finalChapters.chunked(500).forEach { batch ->
                    chapterDao.upsertChapters(batch.map(ChapterEntity::fromDomain))
                }
                if (oldMangaId != finalTargetManga.id) {
                    if (deleteOldManga) {
                        historyDao.deleteByManga(oldMangaId)
                        chapterDao.deleteByMangaId(oldMangaId)
                        mangaDao.deleteById(oldMangaId)
                    } else {
                        mangaDao.updateLibraryStatus(
                            id = oldMangaId,
                            inLibrary = false,
                            categoryId = Manga.DEFAULT_CATEGORY_ID,
                            addedAt = 0L
                        )
                    }
                }
            }
            finalTargetManga.id
        }
    }

    fun observeTracksForManga(mangaId: String): Flow<List<TrackRecord>> =
        trackDao.observeTracksForManga(mangaId).map { list -> list.map { it.toDomain() } }

    suspend fun searchTracker(trackerId: String, query: String): Result<List<TrackSearchResult>> {
        val service = trackerManager.getTracker(trackerId)
            ?: return Result.failure(IllegalArgumentException("Tracker $trackerId tidak ditemukan"))
        return service.search(query)
    }

    suspend fun bindTrack(mangaId: String, candidate: TrackSearchResult): Result<TrackRecord> =
        withContext(Dispatchers.IO) {
            val service = trackerManager.getTracker(candidate.trackerId)
                ?: return@withContext Result.failure(IllegalArgumentException("Tracker tidak ditemukan"))
            service.bind(mangaId, candidate).map { bound ->
                val highestRead = chapterDao.getChapters(mangaId)
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber } ?: 0f
                val adjusted = if (highestRead > bound.lastChapterRead) {
                    bound.copy(lastChapterRead = highestRead)
                } else bound
                trackDao.upsertTrack(TrackRecordEntity.fromDomain(adjusted))
                adjusted
            }
        }

    suspend fun updateTrack(track: TrackRecord): Result<TrackRecord> = withContext(Dispatchers.IO) {
        val service = trackerManager.getTracker(track.trackerId)
            ?: return@withContext Result.failure(IllegalArgumentException("Tracker tidak ditemukan"))
        service.update(track).map { synced ->
            trackDao.upsertTrack(TrackRecordEntity.fromDomain(synced))
            synced
        }
    }

    suspend fun removeTrack(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.deleteTrackById(trackId)
    }

    private suspend fun syncTrackedChapterProgress(mangaId: String) {
        val tracks = trackDao.getTracksForManga(mangaId).map { it.toDomain() }
        if (tracks.isEmpty()) return
        val maxRead = chapterDao.getChapters(mangaId)
            .filter { it.read }
            .maxOfOrNull { it.chapterNumber } ?: return

        for (track in tracks) {
            if (maxRead <= track.lastChapterRead) continue
            val service = trackerManager.getTracker(track.trackerId) ?: continue
            if (!service.getAccountInfo().isLoggedIn) continue
            val updated = track.copy(lastChapterRead = maxRead, updatedAt = System.currentTimeMillis())
            service.update(updated).onSuccess { synced ->
                trackDao.upsertTrack(TrackRecordEntity.fromDomain(synced))
            }
        }
    }
}

object MigrationHelper {
    fun transferChapterProgress(
        oldChapters: List<Chapter>,
        newChapters: List<Chapter>
    ): List<Chapter> {
        if (oldChapters.isEmpty() || newChapters.isEmpty()) return newChapters

        val maxReadNumber = oldChapters
            .filter { it.read }
            .maxOfOrNull { it.chapterNumber }

        val bookmarkedNumbers = oldChapters
            .filter { it.bookmark }
            .map { it.chapterNumber }
            .toSet()

        val oldByNumber = oldChapters.associateBy { it.chapterNumber }

        return newChapters.map { newCh ->
            val exactOld = oldByNumber[newCh.chapterNumber]
            val shouldMarkRead = (maxReadNumber != null && newCh.chapterNumber <= maxReadNumber) ||
                (exactOld?.read == true)
            val shouldBookmark = newCh.bookmark || (newCh.chapterNumber in bookmarkedNumbers)
            val preservedPage = if (!shouldMarkRead && exactOld != null && exactOld.lastPageRead > newCh.lastPageRead) {
                exactOld.lastPageRead
            } else {
                newCh.lastPageRead
            }

            newCh.copy(
                read = shouldMarkRead,
                bookmark = shouldBookmark,
                lastPageRead = preservedPage
            )
        }
    }
}
