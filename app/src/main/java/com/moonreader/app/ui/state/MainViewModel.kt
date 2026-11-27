package com.moonreader.app.ui.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonreader.app.AppContainer
import com.moonreader.app.data.local.AppPreferences
import com.moonreader.app.data.local.AppReleaseInfo
import com.moonreader.app.data.local.OpdsServerConfig
import com.moonreader.app.data.tracker.AniListTracker
import com.moonreader.app.data.tracker.MyAnimeListTracker
import com.moonreader.app.domain.model.AppThemeMode
import com.moonreader.app.domain.model.CatalogMode
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.DownloadTask
import com.moonreader.app.domain.model.HistoryEntry
import com.moonreader.app.domain.model.LibraryDisplayMode
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.ReaderMode
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackSearchResult
import com.moonreader.app.domain.model.TrackerAccountInfo
import com.moonreader.app.domain.model.UpdateEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseCatalogUiState(
    val sourceId: String = "",
    val sourceName: String = "",
    val mode: CatalogMode = CatalogMode.POPULAR,
    val query: String = "",
    val items: List<Manga> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ReaderUiState(
    val manga: Manga? = null,
    val chapter: Chapter? = null,
    val chapters: List<Chapter> = emptyList(),
    val pages: List<Page> = emptyList(),
    val currentPageIndex: Int = 0,
    val readerMode: ReaderMode = ReaderMode.WEBTOON,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class MigrateSourceSection(
    val sourceId: String,
    val sourceName: String,
    val isLoading: Boolean = false,
    val items: List<Manga> = emptyList(),
    val error: String? = null
)

data class MigrateTargetPreview(
    val targetManga: Manga,
    val chapters: List<Chapter> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val releaseInfo: AppReleaseInfo? = null,
    val error: String? = null
)

data class TrackSearchUiState(
    val trackerId: String = "",
    val trackerName: String = "",
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<TrackSearchResult> = emptyList(),
    val error: String? = null
)

data class TrackSyncFeedback(
    val message: String,
    val isError: Boolean = false
)

data class MigrateUiState(
    val sourceManga: Manga? = null,
    val oldChaptersCount: Int = 0,
    val oldReadCount: Int = 0,
    val query: String = "",
    val sections: List<MigrateSourceSection> = emptyList(),
    val targetPreview: MigrateTargetPreview? = null,
    val isMigrating: Boolean = false
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val prefs = container.preferencesStore
    private val sourceManager = container.sourceManager

    val preferences: StateFlow<AppPreferences> = prefs.state

    val library: StateFlow<List<Manga>> = repo.libraryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repo.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = repo.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val updates: StateFlow<List<UpdateEntry>> = repo.updatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadTasks: StateFlow<List<DownloadTask>> = repo.downloadTasksFlow

    private val _sources = MutableStateFlow(sourceManager.getAllSourceInfos())
    val sources: StateFlow<List<SourceInfo>> = _sources.asStateFlow()

    private val _isRefreshingLibrary = MutableStateFlow(false)
    val isRefreshingLibrary: StateFlow<Boolean> = _isRefreshingLibrary.asStateFlow()

    private val _isRefreshingDetail = MutableStateFlow(false)
    val isRefreshingDetail: StateFlow<Boolean> = _isRefreshingDetail.asStateFlow()

    private val _isRestoringBackup = MutableStateFlow(false)
    val isRestoringBackup: StateFlow<Boolean> = _isRestoringBackup.asStateFlow()

    private val _browseState = MutableStateFlow(BrowseCatalogUiState())
    val browseState: StateFlow<BrowseCatalogUiState> = _browseState.asStateFlow()

    private val _readerState = MutableStateFlow(ReaderUiState())
    val readerState: StateFlow<ReaderUiState> = _readerState.asStateFlow()

    private val _migrateState = MutableStateFlow(MigrateUiState())
    val migrateState: StateFlow<MigrateUiState> = _migrateState.asStateFlow()

    private val _appUpdateState = MutableStateFlow(AppUpdateUiState())
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private val _trackerAccounts = MutableStateFlow(container.trackerManager.getAccountInfos())
    val trackerAccounts: StateFlow<List<TrackerAccountInfo>> = _trackerAccounts.asStateFlow()

    private val _trackSearchState = MutableStateFlow<TrackSearchUiState?>(null)
    val trackSearchState: StateFlow<TrackSearchUiState?> = _trackSearchState.asStateFlow()

    private val _isTrackerLoggingIn = MutableStateFlow(false)
    val isTrackerLoggingIn: StateFlow<Boolean> = _isTrackerLoggingIn.asStateFlow()

    private val _syncingTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingTrackIds: StateFlow<Set<String>> = _syncingTrackIds.asStateFlow()

    private val _trackFeedback = MutableStateFlow<Map<String, TrackSyncFeedback>>(emptyMap())
    val trackFeedback: StateFlow<Map<String, TrackSyncFeedback>> = _trackFeedback.asStateFlow()

    private var migrateSearchJob: kotlinx.coroutines.Job? = null

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.ensureInitialData()
        }
    }

    fun observeManga(mangaId: String): Flow<Manga?> = repo.observeManga(mangaId)

    fun observeChapters(mangaId: String): Flow<List<Chapter>> = repo.observeChapters(mangaId)

    fun refreshLibrary() {
        if (_isRefreshingLibrary.value) return
        viewModelScope.launch {
            _isRefreshingLibrary.value = true
            val result = repo.refreshLibraryUpdates()
            _isRefreshingLibrary.value = false
            result.onSuccess { newCount ->
                _snackbarEvents.emit(
                    if (newCount > 0) "$newCount chapter baru ditemukan"
                    else "Perpustakaan sudah paling baru"
                )
            }.onFailure { err ->
                _snackbarEvents.emit("Gagal memperbarui: ${err.message}")
            }
        }
    }

    fun refreshMangaDetail(mangaId: String) {
        if (_isRefreshingDetail.value) return
        viewModelScope.launch {
            _isRefreshingDetail.value = true
            repo.refreshMangaDetailsAndChapters(mangaId).onFailure { err ->
                _snackbarEvents.emit("Gagal memuat detail: ${err.message}")
            }
            _isRefreshingDetail.value = false
        }
    }

    fun toggleLibrary(mangaId: String, categoryId: Long = Manga.DEFAULT_CATEGORY_ID) {
        viewModelScope.launch {
            repo.toggleLibrary(mangaId, categoryId)
        }
    }

    fun moveMangaToCategory(mangaId: String, categoryId: Long) {
        viewModelScope.launch {
            repo.moveMangaToCategory(mangaId, categoryId)
            _snackbarEvents.emit("Kategori diperbarui")
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            repo.addCategory(name)
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            repo.deleteCategory(categoryId)
        }
    }

    fun markChapterRead(chapterId: String, read: Boolean) {
        viewModelScope.launch {
            repo.markChapterRead(chapterId, read)
        }
    }

    fun markAllChaptersRead(mangaId: String, read: Boolean) {
        viewModelScope.launch {
            repo.markAllChaptersRead(mangaId, read)
        }
    }

    fun toggleBookmark(chapterId: String) {
        viewModelScope.launch {
            repo.toggleChapterBookmark(chapterId)
        }
    }

    fun enqueueDownload(manga: Manga, chapter: Chapter) {
        viewModelScope.launch {
            repo.enqueueChapterDownload(manga, chapter)
            _snackbarEvents.emit("Mengunduh ${chapter.name}")
        }
    }

    fun deleteDownload(mangaId: String, chapterId: String) {
        viewModelScope.launch {
            repo.deleteChapterDownload(mangaId, chapterId)
            _snackbarEvents.emit("Unduhan dihapus")
        }
    }

    fun clearCompletedDownloads() = repo.clearCompletedDownloads()

    fun removeHistoryEntry(mangaId: String) {
        viewModelScope.launch {
            repo.removeHistoryEntry(mangaId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repo.clearAllHistory()
            _snackbarEvents.emit("Riwayat baca dibersihkan")
        }
    }

    fun openSourceCatalog(sourceId: String, mode: CatalogMode = CatalogMode.POPULAR, query: String = "") {
        val info = sourceManager.getSource(sourceId)?.info ?: return
        _browseState.value = BrowseCatalogUiState(
            sourceId = sourceId,
            sourceName = info.name,
            mode = mode,
            query = query,
            items = emptyList(),
            page = 1,
            isLoading = true
        )
        loadCatalogPage(page = 1, append = false)
    }

    fun closeSourceCatalog() {
        _browseState.value = BrowseCatalogUiState()
    }

    fun updateCatalogSearchQuery(query: String) {
        _browseState.update { it.copy(query = query) }
    }

    fun submitCatalogSearch() {
        val state = _browseState.value
        val mode = if (state.query.isBlank()) CatalogMode.POPULAR else CatalogMode.SEARCH
        _browseState.update { it.copy(mode = mode, page = 1, items = emptyList(), isLoading = true, error = null) }
        loadCatalogPage(page = 1, append = false)
    }

    fun switchCatalogMode(mode: CatalogMode) {
        _browseState.update { it.copy(mode = mode, page = 1, items = emptyList(), isLoading = true, error = null) }
        loadCatalogPage(page = 1, append = false)
    }

    fun loadNextCatalogPage() {
        val st = _browseState.value
        if (st.isLoading || !st.hasNextPage) return
        loadCatalogPage(page = st.page + 1, append = true)
    }

    private fun loadCatalogPage(page: Int, append: Boolean) {
        val st = _browseState.value
        viewModelScope.launch {
            _browseState.update { it.copy(isLoading = true, error = null) }
            val result = repo.browseCatalog(
                sourceId = st.sourceId,
                mode = st.mode.name,
                query = st.query,
                page = page
            )
            result.onSuccess { pageRes ->
                _browseState.update { cur ->
                    val merged = if (append) cur.items + pageRes.mangas else pageRes.mangas
                    cur.copy(
                        items = merged.distinctBy { it.id },
                        page = page,
                        hasNextPage = pageRes.hasNextPage,
                        isLoading = false,
                        error = null
                    )
                }
            }.onFailure { err ->
                _browseState.update { cur ->
                    cur.copy(isLoading = false, error = err.message ?: "Gagal memuat katalog")
                }
            }
        }
    }

    fun prepareBrowseManga(manga: Manga, onReady: () -> Unit) {
        viewModelScope.launch {
            repo.cacheBrowseManga(manga)
            onReady()
        }
    }

    fun importLocalCbz(uri: Uri) {
        viewModelScope.launch {
            if (repo.isTachibkOrJsonBackupUri(uri)) {
                restoreBackupFromUri(uri)
                return@launch
            }
            val result = repo.importLocalCbz(uri)
            result.onSuccess { manga ->
                _snackbarEvents.emit("Berhasil mengimpor: ${manga.title}")
                if (_browseState.value.sourceId == "local_cbz") {
                    openSourceCatalog("local_cbz")
                }
            }.onFailure { err ->
                _snackbarEvents.emit("Gagal impor CBZ: ${err.message}")
            }
        }
    }

    fun openReader(mangaId: String, chapterId: String) {
        viewModelScope.launch {
            _readerState.value = ReaderUiState(
                readerMode = prefs.state.value.defaultReaderMode,
                isLoading = true
            )
            val mangaFlowJob = repo.observeManga(mangaId)
            var currentManga: Manga? = null
            mangaFlowJob.collect { m ->
                currentManga = m
                if (m != null) {
                    loadReaderChapter(m, chapterId)
                    return@collect
                }
            }
        }
    }

    fun openReaderDirect(mangaId: String, chapterId: String) {
        viewModelScope.launch {
            _readerState.update {
                it.copy(
                    readerMode = prefs.state.value.defaultReaderMode,
                    isLoading = true,
                    error = null
                )
            }
            val chapters = container.database.chapterDao().getChapters(mangaId).map { it.toDomain() }
            val manga = container.database.mangaDao().getMangaById(mangaId)?.toDomain()
            if (manga == null) {
                _readerState.update { it.copy(isLoading = false, error = "Data komik tidak ditemukan") }
                return@launch
            }
            val chapter = chapters.firstOrNull { it.id == chapterId }
            if (chapter == null) {
                _readerState.update { it.copy(isLoading = false, error = "Chapter tidak ditemukan") }
                return@launch
            }
            loadPagesInternal(manga, chapter, chapters)
        }
    }

    private suspend fun loadReaderChapter(manga: Manga, chapterId: String) {
        val chapters = container.database.chapterDao().getChapters(manga.id).map { it.toDomain() }
        val chapter = chapters.firstOrNull { it.id == chapterId } ?: return
        loadPagesInternal(manga, chapter, chapters)
    }

    private suspend fun loadPagesInternal(manga: Manga, chapter: Chapter, chapters: List<Chapter>) {
        val result = repo.loadPagesForChapter(manga.id, chapter.id)
        result.onSuccess { pages ->
            val startPage = chapter.lastPageRead.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            _readerState.update { cur ->
                cur.copy(
                    manga = manga,
                    chapter = chapter,
                    chapters = chapters,
                    pages = pages,
                    currentPageIndex = startPage,
                    isLoading = false,
                    error = if (pages.isEmpty()) "Chapter ini belum memiliki halaman gambar" else null
                )
            }
            if (pages.isNotEmpty()) {
                repo.saveReadingProgress(manga.id, chapter.id, startPage, pages.size)
            }
        }.onFailure { err ->
            _readerState.update { cur ->
                cur.copy(
                    manga = manga,
                    chapter = chapter,
                    chapters = chapters,
                    isLoading = false,
                    error = err.message ?: "Gagal memuat halaman"
                )
            }
        }
    }

    fun onReaderPageChanged(pageIndex: Int) {
        val st = _readerState.value
        val manga = st.manga ?: return
        val chapter = st.chapter ?: return
        val total = st.pages.size
        if (pageIndex < 0 || pageIndex >= total) return
        _readerState.update { it.copy(currentPageIndex = pageIndex) }
        viewModelScope.launch {
            repo.saveReadingProgress(manga.id, chapter.id, pageIndex, total)
        }
    }

    fun switchReaderMode(mode: ReaderMode) {
        _readerState.update { it.copy(readerMode = mode) }
    }

    fun openAdjacentChapter(next: Boolean) {
        val st = _readerState.value
        val current = st.chapter ?: return
        // Chapters list is sorted DESC by chapterNumber (ch 5, 4, 3, 2, 1)
        val ascending = st.chapters.sortedBy { it.chapterNumber }
        val idx = ascending.indexOfFirst { it.id == current.id }
        if (idx < 0) return
        val target = if (next) ascending.getOrNull(idx + 1) else ascending.getOrNull(idx - 1)
        if (target != null && st.manga != null) {
            openReaderDirect(st.manga.id, target.id)
        } else {
            viewModelScope.launch {
                _snackbarEvents.emit(if (next) "Ini chapter terakhir" else "Ini chapter pertama")
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) = prefs.setThemeMode(mode)
    fun setDefaultReaderMode(mode: ReaderMode) = prefs.setDefaultReaderMode(mode)
    fun setLibraryDisplayMode(mode: LibraryDisplayMode) = prefs.setLibraryDisplayMode(mode)
    fun setGridColumns(cols: Int) = prefs.setGridColumns(cols)
    fun setShowUnreadBadge(show: Boolean) = prefs.setShowUnreadBadge(show)
    fun setKeepScreenOn(keep: Boolean) = prefs.setKeepScreenOn(keep)
    fun setReaderBrightness(value: Float?) = prefs.setReaderBrightness(value)
    fun setPreferIndonesian(prefer: Boolean) = prefs.setPreferIndonesianLanguage(prefer)

    fun toggleSourceEnabled(sourceId: String, enabled: Boolean) {
        prefs.toggleSourceEnabled(sourceId, enabled)
        _sources.value = sourceManager.getAllSourceInfos()
    }

    fun addOpdsServer(name: String, baseUrl: String, username: String, password: String) {
        val cleanName = name.trim().ifBlank { "Server OPDS" }
        val cleanUrl = baseUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            viewModelScope.launch {
                _snackbarEvents.emit("URL OPDS harus diawali http:// atau https://")
            }
            return
        }
        val id = "opds_${java.util.UUID.randomUUID().toString().take(8)}"
        prefs.addOrUpdateOpdsServer(
            OpdsServerConfig(
                id = id,
                name = cleanName,
                baseUrl = cleanUrl,
                username = username.trim(),
                password = password
            )
        )
        _sources.value = sourceManager.getAllSourceInfos()
        viewModelScope.launch {
            _snackbarEvents.emit("Server OPDS \"$cleanName\" ditambahkan")
        }
    }

    fun removeOpdsServer(serverId: String) {
        prefs.removeOpdsServer(serverId)
        _sources.value = sourceManager.getAllSourceInfos()
        viewModelScope.launch {
            _snackbarEvents.emit("Server OPDS dihapus")
        }
    }

    fun setUpdateFeedUrl(url: String) {
        prefs.setUpdateFeedUrl(url)
    }

    fun checkAppUpdate(customUrl: String? = null) {
        val targetUrl = customUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: prefs.state.value.updateFeedUrl
        if (targetUrl.isBlank()) {
            viewModelScope.launch { _snackbarEvents.emit("Isi URL server update terlebih dahulu") }
            return
        }
        prefs.setUpdateFeedUrl(targetUrl)
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateUiState(isChecking = true)
            container.appUpdater.checkForUpdate(targetUrl)
                .onSuccess { info ->
                    _appUpdateState.value = AppUpdateUiState(releaseInfo = info)
                }
                .onFailure { err ->
                    _appUpdateState.value = AppUpdateUiState(error = err.message ?: "Gagal cek pembaruan")
                }
        }
    }

    fun downloadAndInstallAppUpdate() {
        val info = _appUpdateState.value.releaseInfo ?: return
        val updater = container.appUpdater
        if (!updater.canRequestPackageInstalls()) {
            updater.launchInstallPermissionSettings()
            viewModelScope.launch {
                _snackbarEvents.emit("Aktifkan izin \"Install unknown apps\" untuk MoonReader, lalu ketuk Unduh & Pasang lagi")
            }
            return
        }
        viewModelScope.launch {
            _appUpdateState.update { it.copy(isDownloading = true, downloadProgress = 0, error = null) }
            updater.downloadApk(info.apkUrl) { pct ->
                _appUpdateState.update { it.copy(downloadProgress = pct) }
            }.onSuccess { apkFile ->
                _appUpdateState.update { it.copy(isDownloading = false) }
                updater.launchApkInstaller(apkFile)
            }.onFailure { err ->
                _appUpdateState.update {
                    it.copy(isDownloading = false, error = err.message ?: "Gagal mengunduh APK")
                }
            }
        }
    }

    fun clearAppUpdateState() {
        _appUpdateState.value = AppUpdateUiState()
    }

    suspend fun generateBackupJson(): String = repo.exportBackupJson()

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            repo.importBackupJson(rawJson)
                .onSuccess { count -> _snackbarEvents.emit("Restore berhasil ($count komik dipulihkan)") }
                .onFailure { err -> _snackbarEvents.emit("Backup tidak valid: ${err.message}") }
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            _isRestoringBackup.value = true
            repo.importBackupUri(uri)
                .onSuccess { summary -> _snackbarEvents.emit(summary) }
                .onFailure { err -> _snackbarEvents.emit("Gagal restore backup: ${err.message}") }
            _isRestoringBackup.value = false
        }
    }

    fun openMigrationSearch(mangaId: String) {
        viewModelScope.launch {
            val (manga, oldChapters) = repo.getMangaWithChapters(mangaId)
            if (manga == null) {
                _snackbarEvents.emit("Data komik tidak ditemukan")
                return@launch
            }
            _migrateState.value = MigrateUiState(
                sourceManga = manga,
                oldChaptersCount = oldChapters.size,
                oldReadCount = oldChapters.count { it.read },
                query = manga.title,
                sections = emptyList(),
                targetPreview = null
            )
            runMigrationSearch()
        }
    }

    fun updateMigrationQuery(query: String) {
        _migrateState.update { it.copy(query = query) }
    }

    fun runMigrationSearch() {
        val st = _migrateState.value
        val sourceManga = st.sourceManga ?: return
        val q = st.query.trim()
        if (q.isBlank()) return

        migrateSearchJob?.cancel()
        val targetSources = repo.getEnabledSources()
            .filter { it.info.supportsSearch }
            .sortedBy { if (it.info.id == sourceManga.sourceId) 1 else 0 }

        _migrateState.update {
            it.copy(
                sections = targetSources.map { s ->
                    MigrateSourceSection(sourceId = s.info.id, sourceName = s.info.name, isLoading = true)
                }
            )
        }

        migrateSearchJob = viewModelScope.launch {
            targetSources.forEach { src ->
                launch {
                    val res = repo.searchSourceForMigration(src.info.id, q)
                    _migrateState.update { cur ->
                        cur.copy(
                            sections = cur.sections.map { sec ->
                                if (sec.sourceId != src.info.id) sec
                                else res.fold(
                                    onSuccess = { list ->
                                        sec.copy(
                                            isLoading = false,
                                            items = list.filter { it.id != sourceManga.id },
                                            error = null
                                        )
                                    },
                                    onFailure = { err ->
                                        sec.copy(
                                            isLoading = false,
                                            items = emptyList(),
                                            error = err.message ?: "Gagal mencari"
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fun selectMigrationTarget(targetManga: Manga) {
        viewModelScope.launch {
            _migrateState.update {
                it.copy(targetPreview = MigrateTargetPreview(targetManga = targetManga, isLoading = true))
            }
            val result = repo.previewMigrationTarget(targetManga)
            _migrateState.update { cur ->
                result.fold(
                    onSuccess = { (details, chapters) ->
                        cur.copy(
                            targetPreview = MigrateTargetPreview(
                                targetManga = details,
                                chapters = chapters,
                                isLoading = false
                            )
                        )
                    },
                    onFailure = { err ->
                        cur.copy(
                            targetPreview = MigrateTargetPreview(
                                targetManga = targetManga,
                                isLoading = false,
                                error = err.message ?: "Gagal memuat detail target"
                            )
                        )
                    }
                )
            }
        }
    }

    fun dismissMigrationTarget() {
        _migrateState.update { it.copy(targetPreview = null) }
    }

    fun confirmMigration(
        copyReadProgress: Boolean,
        keepCategory: Boolean,
        deleteOldManga: Boolean,
        onMigrated: (newMangaId: String) -> Unit
    ) {
        val st = _migrateState.value
        val oldManga = st.sourceManga ?: return
        val preview = st.targetPreview ?: return

        viewModelScope.launch {
            _migrateState.update { it.copy(isMigrating = true) }
            val result = repo.migrateManga(
                oldMangaId = oldManga.id,
                targetManga = preview.targetManga,
                targetChapters = preview.chapters,
                copyReadProgress = copyReadProgress,
                keepCategory = keepCategory,
                deleteOldManga = deleteOldManga
            )
            _migrateState.update { it.copy(isMigrating = false, targetPreview = null) }
            result.onSuccess { newId ->
                _snackbarEvents.emit("Migrasi ke ${preview.targetManga.sourceId} berhasil")
                onMigrated(newId)
            }.onFailure { err ->
                _snackbarEvents.emit("Migrasi gagal: ${err.message}")
            }
        }
    }

    fun observeTracks(mangaId: String): Flow<List<TrackRecord>> = repo.observeTracksForManga(mangaId)

    fun refreshTrackerAccounts() {
        _trackerAccounts.value = container.trackerManager.getAccountInfos()
    }

    fun loginKitsu(usernameOrEmail: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isTrackerLoggingIn.value = true
            container.trackerManager.kitsu.login(usernameOrEmail, password)
                .onSuccess { name ->
                    refreshTrackerAccounts()
                    _snackbarEvents.emit("Kitsu terhubung sebagai $name")
                    onSuccess()
                }
                .onFailure { err ->
                    _snackbarEvents.emit("Login Kitsu gagal: ${err.message}")
                }
            _isTrackerLoggingIn.value = false
        }
    }

    fun loginAniListWithToken(token: String, clientId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isTrackerLoggingIn.value = true
            container.trackerManager.aniList.loginWithToken(token, clientId)
                .onSuccess { name ->
                    refreshTrackerAccounts()
                    _snackbarEvents.emit("AniList terhubung sebagai $name")
                    onSuccess()
                }
                .onFailure { err ->
                    _snackbarEvents.emit("Login AniList gagal: ${err.message}")
                }
            _isTrackerLoggingIn.value = false
        }
    }

    fun loginMalWithToken(token: String, clientId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isTrackerLoggingIn.value = true
            container.trackerManager.myAnimeList.loginWithToken(token, clientId)
                .onSuccess { name ->
                    refreshTrackerAccounts()
                    _snackbarEvents.emit("MyAnimeList terhubung sebagai $name")
                    onSuccess()
                }
                .onFailure { err ->
                    _snackbarEvents.emit("Login MAL gagal: ${err.message}")
                }
            _isTrackerLoggingIn.value = false
        }
    }

    fun launchTrackerOAuth(context: Context, trackerId: String, clientId: String) {
        val cleanId = clientId.trim()
        if (cleanId.isBlank()) {
            viewModelScope.launch { _snackbarEvents.emit("Masukkan Client ID terlebih dahulu") }
            return
        }
        val authUrl = when (trackerId) {
            AniListTracker.TRACKER_ID -> {
                val existing = prefs.state.value.trackerCredentials[AniListTracker.TRACKER_ID]
                prefs.saveTrackerCredentials(
                    (existing ?: com.moonreader.app.data.local.TrackerCredentials(AniListTracker.TRACKER_ID))
                        .copy(clientId = cleanId)
                )
                refreshTrackerAccounts()
                container.trackerManager.aniList.buildAuthorizeUrl(cleanId)
            }
            MyAnimeListTracker.TRACKER_ID -> {
                val url = container.trackerManager.myAnimeList.prepareAuthorizeUrl(cleanId)
                refreshTrackerAccounts()
                url
            }
            else -> return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure { err ->
            viewModelScope.launch { _snackbarEvents.emit("Gagal membuka browser: ${err.message}") }
        }
    }

    fun handleOAuthRedirect(uri: Uri) {
        when (uri.host) {
            "anilist-auth" -> {
                val fragment = uri.fragment.orEmpty()
                val params = fragment.split("&").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) part.substring(0, idx) to Uri.decode(part.substring(idx + 1)) else null
                }.toMap()
                val token = params["access_token"] ?: uri.getQueryParameter("access_token")
                if (!token.isNullOrBlank()) {
                    loginAniListWithToken(token, "") {}
                } else {
                    viewModelScope.launch { _snackbarEvents.emit("Token AniList tidak ditemukan di callback") }
                }
            }
            "mal-auth" -> {
                val code = uri.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    viewModelScope.launch {
                        _isTrackerLoggingIn.value = true
                        container.trackerManager.myAnimeList.exchangeAuthCode(code)
                            .onSuccess { name ->
                                refreshTrackerAccounts()
                                _snackbarEvents.emit("MyAnimeList terhubung sebagai $name")
                            }
                            .onFailure { err ->
                                _snackbarEvents.emit("OAuth MAL gagal: ${err.message}")
                            }
                        _isTrackerLoggingIn.value = false
                    }
                } else {
                    val errDesc = uri.getQueryParameter("error") ?: "Authorization dibatalkan"
                    viewModelScope.launch { _snackbarEvents.emit("Login MAL gagal: $errDesc") }
                }
            }
        }
    }

    fun logoutTracker(trackerId: String) {
        container.trackerManager.getTracker(trackerId)?.logout()
        refreshTrackerAccounts()
        viewModelScope.launch {
            _snackbarEvents.emit("Akun tracker diputuskan")
        }
    }

    fun openTrackerSearch(trackerId: String, initialQuery: String) {
        val tracker = container.trackerManager.getTracker(trackerId) ?: return
        _trackSearchState.value = TrackSearchUiState(
            trackerId = trackerId,
            trackerName = tracker.name,
            query = initialQuery,
            isLoading = true
        )
        runTrackerSearch()
    }

    fun updateTrackerSearchQuery(query: String) {
        _trackSearchState.update { it?.copy(query = query) }
    }

    fun runTrackerSearch() {
        val st = _trackSearchState.value ?: return
        val q = st.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _trackSearchState.update { it?.copy(isLoading = true, error = null) }
            repo.searchTracker(st.trackerId, q)
                .onSuccess { list ->
                    _trackSearchState.update { it?.copy(isLoading = false, results = list, error = null) }
                }
                .onFailure { err ->
                    _trackSearchState.update {
                        it?.copy(isLoading = false, results = emptyList(), error = err.message ?: "Gagal mencari")
                    }
                }
        }
    }

    fun closeTrackerSearch() {
        _trackSearchState.value = null
    }

    fun bindTrackerCandidate(mangaId: String, candidate: TrackSearchResult) {
        viewModelScope.launch {
            repo.bindTrack(mangaId, candidate)
                .onSuccess { bound ->
                    _trackSearchState.value = null
                    val msg = "Ditautkan ke ${candidate.title}"
                    _trackFeedback.update { it + (bound.id to TrackSyncFeedback(msg, isError = false)) }
                    _snackbarEvents.emit(msg)
                }
                .onFailure { err ->
                    _trackSearchState.update { st ->
                        st?.copy(error = err.message ?: "Gagal menautkan judul")
                    }
                    _snackbarEvents.emit("Gagal menautkan: ${err.message}")
                }
        }
    }

    fun updateTrackRecord(track: TrackRecord) {
        viewModelScope.launch {
            _syncingTrackIds.update { it + track.id }
            _trackFeedback.update { it - track.id }
            repo.updateTrack(track)
                .onSuccess { synced ->
                    val trackerName = container.trackerManager.getTracker(synced.trackerId)?.name ?: synced.trackerId
                    val msg = "Tersinkron ke $trackerName: Ch. ${synced.lastChapterRead.toInt()} • ${synced.status.label}"
                    _trackFeedback.update { it + (track.id to TrackSyncFeedback(msg, isError = false)) }
                    _snackbarEvents.emit(msg)
                }
                .onFailure { err ->
                    val msg = "Gagal sinkron: ${err.message ?: "Kesalahan jaringan"}"
                    _trackFeedback.update { it + (track.id to TrackSyncFeedback(msg, isError = true)) }
                    _snackbarEvents.emit(msg)
                }
            _syncingTrackIds.update { it - track.id }
        }
    }

    fun removeTrackRecord(trackId: String) {
        viewModelScope.launch {
            repo.removeTrack(trackId)
            _trackFeedback.update { it - trackId }
            _snackbarEvents.emit("Tautan tracker dihapus")
        }
    }

    fun clearTrackFeedback() {
        _trackFeedback.value = emptyMap()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
