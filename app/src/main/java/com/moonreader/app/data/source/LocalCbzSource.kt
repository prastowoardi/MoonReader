package com.moonreader.app.data.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.source.MangaPageResult
import com.moonreader.app.domain.source.MangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

@Serializable
data class LocalComicRecord(
    val id: String,
    val title: String,
    val archivePath: String,
    val coverPath: String,
    val pageCount: Int,
    val importedAt: Long
)

/**
 * Natural numeric comparator so "page_2.jpg" comes before "page_10.jpg".
 */
internal object NaturalOrderComparator : Comparator<String> {
    private val chunkRegex = Regex("(\\d+|\\D+)")

    override fun compare(a: String, b: String): Int {
        val chunksA = chunkRegex.findAll(a.lowercase()).map { it.value }.toList()
        val chunksB = chunkRegex.findAll(b.lowercase()).map { it.value }.toList()
        val limit = minOf(chunksA.size, chunksB.size)

        for (i in 0 until limit) {
            val partA = chunksA[i]
            val partB = chunksB[i]
            val numA = partA.toLongOrNull()
            val numB = partB.toLongOrNull()
            val cmp = if (numA != null && numB != null) {
                numA.compareTo(numB)
            } else {
                partA.compareTo(partB)
            }
            if (cmp != 0) return cmp
        }
        return chunksA.size.compareTo(chunksB.size)
    }
}

/**
 * Local CBZ/ZIP comic source supporting Storage Access Framework imports.
 */
class LocalCbzSource(private val context: Context) : MangaSource {

    companion object {
        const val SOURCE_ID = "local_cbz"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val baseDir: File by lazy { File(context.filesDir, "local_comics").apply { mkdirs() } }
    private val coversDir: File by lazy { File(baseDir, "covers").apply { mkdirs() } }
    private val archivesDir: File by lazy { File(baseDir, "archives").apply { mkdirs() } }
    private val pagesCacheDir: File by lazy { File(context.cacheDir, "cbz_pages").apply { mkdirs() } }
    private val indexFile: File by lazy { File(baseDir, "index.json") }

    override val info = SourceInfo(
        id = SOURCE_ID,
        name = "Komik Lokal (CBZ / ZIP)",
        lang = "Lokal",
        description = "Baca arsip .cbz atau .zip langsung dari penyimpanan perangkat",
        isLocal = true,
        supportsSearch = true,
        supportsLatest = false
    )

    suspend fun importCbzFromUri(uri: Uri): Result<Manga> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "Komik_Lokal_${System.currentTimeMillis()}.cbz"
            val title = displayName.substringBeforeLast('.').ifBlank { displayName }
            val recordId = UUID.randomUUID().toString().take(12)
            val destFile = File(archivesDir, "${recordId}.cbz")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Gagal membuka file komik dari penyimpanan")

            if (!hasZipSignature(destFile)) {
                destFile.delete()
                error("File bukan arsip CBZ/ZIP yang valid (header PK tidak ditemukan)")
            }

            val imageEntries = try {
                listImageEntries(destFile)
            } catch (e: Exception) {
                destFile.delete()
                error("Gagal membaca arsip CBZ/ZIP: ${e.message ?: "format tidak didukung"}")
            }
            if (imageEntries.isEmpty()) {
                destFile.delete()
                error("File CBZ/ZIP tidak berisi gambar yang didukung (JPG/PNG/WEBP)")
            }

            val coverFile = File(coversDir, "${recordId}_cover.img")
            ZipFile(destFile).use { zip ->
                val firstEntry = zip.getEntry(imageEntries.first())
                zip.getInputStream(firstEntry).use { zin ->
                    FileOutputStream(coverFile).use { out -> zin.copyTo(out) }
                }
            }

            val record = LocalComicRecord(
                id = recordId,
                title = title,
                archivePath = destFile.absolutePath,
                coverPath = coverFile.absolutePath,
                pageCount = imageEntries.size,
                importedAt = System.currentTimeMillis()
            )
            val all = loadRecords().toMutableList()
            all.add(0, record)
            saveRecords(all)
            recordToManga(record)
        }
    }

    suspend fun deleteLocalComic(mangaId: String) = withContext(Dispatchers.IO) {
        val rawId = mangaId.removePrefix("$SOURCE_ID:")
        val all = loadRecords().toMutableList()
        val target = all.firstOrNull { it.id == rawId }
        if (target != null) {
            File(target.archivePath).delete()
            File(target.coverPath).delete()
            File(pagesCacheDir, rawId).deleteRecursively()
            all.removeAll { it.id == rawId }
            saveRecords(all)
        }
    }

    override suspend fun getPopularManga(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext MangaPageResult(emptyList(), false)
        val list = loadRecords().map(::recordToManga)
        MangaPageResult(list, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangaPageResult = getPopularManga(page)

    override suspend fun searchManga(page: Int, query: String): MangaPageResult = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext MangaPageResult(emptyList(), false)
        val q = query.trim().lowercase()
        val list = loadRecords()
            .filter { q.isBlank() || it.title.lowercase().contains(q) }
            .map(::recordToManga)
        MangaPageResult(list, false)
    }

    override suspend fun getMangaDetails(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val rawId = manga.url
        val record = loadRecords().firstOrNull { it.id == rawId } ?: return@withContext manga
        recordToManga(record).copy(
            inLibrary = manga.inLibrary,
            categoryId = manga.categoryId,
            addedAt = manga.addedAt
        )
    }

    override suspend fun getChapterList(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        val rawId = manga.url
        val record = loadRecords().firstOrNull { it.id == rawId } ?: return@withContext emptyList()
        listOf(
            Chapter(
                id = "$SOURCE_ID:${record.id}:ch1",
                mangaId = manga.id,
                url = record.id,
                name = "Chapter 1 (${record.pageCount} halaman)",
                chapterNumber = 1f,
                scanlator = "Arsip Lokal",
                uploadedAt = record.importedAt,
                totalPages = record.pageCount
            )
        )
    }

    override suspend fun getPageList(chapter: Chapter): List<Page> = withContext(Dispatchers.IO) {
        val rawId = chapter.url
        val record = loadRecords().firstOrNull { it.id == rawId } ?: return@withContext emptyList()
        val archive = File(record.archivePath)
        if (!archive.exists()) return@withContext emptyList()

        val extractDir = File(pagesCacheDir, rawId).apply { mkdirs() }
        val entries = listImageEntries(archive)

        ZipFile(archive).use { zip ->
            entries.mapIndexed { index, entryName ->
                val ext = entryName.substringAfterLast('.', "jpg")
                val outFile = File(extractDir, "page_%04d.%s".format(index, ext))
                if (!outFile.exists() || outFile.length() == 0L) {
                    val entry = zip.getEntry(entryName)
                    zip.getInputStream(entry).use { zin ->
                        FileOutputStream(outFile).use { fout -> zin.copyTo(fout) }
                    }
                }
                Page(index = index, imageUrl = Uri.fromFile(outFile).toString())
            }
        }
    }

    private fun hasZipSignature(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            file.inputStream().use { ins ->
                val b0 = ins.read()
                val b1 = ins.read()
                b0 == 0x50 && b1 == 0x4B // 'P', 'K'
            }
        }.getOrDefault(false)
    }

    private fun listImageEntries(archive: File): List<String> {
        return ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { name ->
                    val clean = name.substringAfterLast('/')
                    !clean.startsWith(".") &&
                        !name.contains("__MACOSX") &&
                        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
                }
                .sortedWith(NaturalOrderComparator)
                .toList()
        }
    }

    private fun recordToManga(record: LocalComicRecord): Manga {
        return Manga(
            id = "$SOURCE_ID:${record.id}",
            sourceId = SOURCE_ID,
            url = record.id,
            title = record.title,
            author = "Penyimpanan Perangkat",
            artist = "CBZ / ZIP",
            description = "Komik lokal yang diimpor ke perangkat (${record.pageCount} halaman).",
            genres = listOf("Lokal", "CBZ"),
            status = MangaStatus.COMPLETED,
            coverUrl = Uri.fromFile(File(record.coverPath)).toString(),
            totalChapters = 1,
            lastUpdatedAt = record.importedAt
        )
    }

    private fun loadRecords(): List<LocalComicRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<LocalComicRecord>>(indexFile.readText())
        }.getOrDefault(emptyList())
    }

    private fun saveRecords(records: List<LocalComicRecord>) {
        indexFile.writeText(json.encodeToString(records))
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
