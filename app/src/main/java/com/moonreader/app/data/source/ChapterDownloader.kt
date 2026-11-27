package com.moonreader.app.data.source

import android.content.Context
import android.net.Uri
import com.moonreader.app.data.local.ChapterDao
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.DownloadStatus
import com.moonreader.app.domain.model.DownloadTask
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class ChapterDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val sourceManager: SourceManager,
    private val chapterDao: ChapterDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadsRoot: File by lazy { File(context.filesDir, "downloads").apply { mkdirs() } }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun enqueueDownload(manga: Manga, chapter: Chapter) {
        if (_tasks.value.any { it.chapterId == chapter.id && it.status == DownloadStatus.DOWNLOADING }) {
            return
        }
        val task = DownloadTask(
            chapterId = chapter.id,
            mangaId = manga.id,
            mangaTitle = manga.title,
            chapterName = chapter.name,
            status = DownloadStatus.QUEUED
        )
        _tasks.update { current ->
            listOf(task) + current.filterNot { it.chapterId == chapter.id }
        }

        scope.launch {
            runDownload(manga, chapter)
        }
    }

    fun getDownloadedPages(mangaId: String, chapterId: String): List<Page>? {
        val dir = chapterDir(mangaId, chapterId)
        if (!dir.exists()) return null
        val files = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) return null
        return files.mapIndexed { idx, file ->
            Page(index = idx, imageUrl = Uri.fromFile(file).toString())
        }
    }

    fun deleteDownloadedChapter(mangaId: String, chapterId: String) {
        chapterDir(mangaId, chapterId).deleteRecursively()
    }

    fun clearCompletedTasks() {
        _tasks.update { list -> list.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING } }
    }

    private suspend fun runDownload(manga: Manga, chapter: Chapter) {
        try {
            val source = sourceManager.getSource(manga.sourceId)
                ?: error("Source ${manga.sourceId} tidak ditemukan")

            val pages = source.getPageList(chapter)
            if (pages.isEmpty()) error("Tidak ada halaman untuk diunduh")

            updateTask(chapter.id) {
                it.copy(status = DownloadStatus.DOWNLOADING, progress = 0, total = pages.size)
            }

            val dir = chapterDir(manga.id, chapter.id).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            pages.forEachIndexed { idx, page ->
                val outFile = File(dir, "page_%04d.img".format(idx))
                savePageToFile(page.imageUrl, outFile)
                updateTask(chapter.id) {
                    it.copy(progress = idx + 1, total = pages.size)
                }
            }

            chapterDao.updateDownloaded(chapter.id, true)
            chapterDao.updateTotalPages(chapter.id, pages.size)
            updateTask(chapter.id) {
                it.copy(status = DownloadStatus.COMPLETED, progress = pages.size, total = pages.size)
            }
        } catch (e: Exception) {
            chapterDir(manga.id, chapter.id).deleteRecursively()
            updateTask(chapter.id) {
                it.copy(status = DownloadStatus.FAILED, error = e.message ?: "Unduhan gagal")
            }
        }
    }

    private fun savePageToFile(url: String, outFile: File) {
        when {
            url.startsWith("http://") || url.startsWith("https://") -> {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MoonReader/1.0 (Android)")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code} saat mengunduh halaman")
                    resp.body?.byteStream()?.use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    } ?: error("Body kosong")
                }
            }
            url.startsWith("file://") -> {
                val src = File(Uri.parse(url).path ?: error("Path file tidak valid"))
                src.copyTo(outFile, overwrite = true)
            }
            url.startsWith("demo://") -> {
                // Demo pages are generated instantaneously offline; store a tiny marker or rendered PNG
                outFile.writeText(url)
            }
            else -> error("Skema halaman tidak didukung: $url")
        }
    }

    private fun chapterDir(mangaId: String, chapterId: String): File {
        val safeManga = mangaId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeChapter = chapterId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(File(downloadsRoot, safeManga), safeChapter)
    }

    private fun updateTask(chapterId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.chapterId == chapterId) transform(it) else it }
        }
    }
}
