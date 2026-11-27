package com.moonreader.app.data.local

import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

data class TachibkHistoryItem(
    val mangaId: String,
    val chapterId: String,
    val readAt: Long
)

data class TachibkImportResult(
    val categories: List<Category>,
    val mangas: List<Manga>,
    val chapters: List<Chapter>,
    val history: List<TachibkHistoryItem>,
    val sourceNames: Map<Long, String>
)

/**
 * Decodes Tachiyomi / Mihon `.tachibk` backup archives (GZIP-compressed Protobuf)
 * without requiring generated protobuf runtime classes.
 */
object TachibkParser {

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_BYTES = 2
    private const val WIRE_FIXED32 = 5

    fun isGzipStream(header: ByteArray): Boolean {
        return header.size >= 2 &&
            (header[0].toInt() and 0xFF) == 0x1F &&
            (header[1].toInt() and 0xFF) == 0x8B
    }

    fun parse(input: InputStream): TachibkImportResult {
        val raw = input.readBytes()
        val protoBytes = if (isGzipStream(raw)) {
            GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
        } else {
            raw
        }
        return parseProtobuf(protoBytes)
    }

    fun parseProtobuf(protoBytes: ByteArray): TachibkImportResult {
        val rawMangas = mutableListOf<RawBackupManga>()
        val rawCategories = mutableListOf<RawBackupCategory>()
        val sourceNames = mutableMapOf<Long, String>()

        val reader = ProtoReader(protoBytes)
        while (!reader.eof()) {
            val tag = reader.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == WIRE_BYTES) rawMangas += parseManga(reader.readBytes()) else reader.skip(wire)
                2 -> if (wire == WIRE_BYTES) rawCategories += parseCategory(reader.readBytes()) else reader.skip(wire)
                101, 100 -> if (wire == WIRE_BYTES) {
                    val src = parseSource(reader.readBytes())
                    if (src.sourceId != 0L && src.name.isNotBlank()) {
                        sourceNames[src.sourceId] = src.name
                    }
                } else reader.skip(wire)
                else -> reader.skip(wire)
            }
        }

        val indexedSorted = rawCategories.withIndex().sortedBy { it.value.order }
        val categories = indexedSorted.mapIndexed { sortedIdx, (_, c) ->
            Category(
                id = 100L + sortedIdx,
                name = c.name.ifBlank { "Kategori ${sortedIdx + 1}" },
                sortOrder = sortedIdx + 10
            )
        }
        val orderToCategoryId = buildMap {
            indexedSorted.forEachIndexed { sortedIdx, (origIdx, c) ->
                put(origIdx, 100L + sortedIdx)
            }
            indexedSorted.forEachIndexed { sortedIdx, (_, c) ->
                put(c.order, 100L + sortedIdx)
            }
        }

        val mangas = mutableListOf<Manga>()
        val chapters = mutableListOf<Chapter>()
        val history = mutableListOf<TachibkHistoryItem>()

        for (rm in rawMangas) {
            val sourceLabel = sourceNames[rm.source] ?: "tachiyomi:${rm.source}"
            val mangaId = "tachiyomi:${shortHash("${rm.source}:${rm.url}")}"

            val firstCatIdx = rm.categories.firstOrNull()?.toInt()
            val mappedCategoryId = if (firstCatIdx != null && orderToCategoryId.containsKey(firstCatIdx)) {
                orderToCategoryId.getValue(firstCatIdx)
            } else {
                Manga.DEFAULT_CATEGORY_ID
            }

            val mappedStatus = when (rm.status) {
                1 -> MangaStatus.ONGOING
                2, 4 -> MangaStatus.COMPLETED
                5 -> MangaStatus.CANCELLED
                6 -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            }

            mangas += Manga(
                id = mangaId,
                sourceId = sourceLabel,
                url = rm.url,
                title = rm.title.ifBlank { "Tanpa Judul" },
                author = rm.author,
                artist = rm.artist.ifBlank { rm.author },
                description = rm.description,
                genres = rm.genres,
                status = mappedStatus,
                coverUrl = rm.thumbnailUrl,
                inLibrary = true,
                categoryId = mappedCategoryId,
                totalChapters = rm.chapters.size,
                addedAt = if (rm.dateAdded > 0L) rm.dateAdded else System.currentTimeMillis()
            )

            val chapterUrlToId = HashMap<String, String>(rm.chapters.size)
            for ((idx, rc) in rm.chapters.withIndex()) {
                val chapterId = "$mangaId:ch_${idx}_${rc.url.hashCode().toUInt()}"
                chapterUrlToId[rc.url] = chapterId
                chapters += Chapter(
                    id = chapterId,
                    mangaId = mangaId,
                    url = rc.url,
                    name = rc.name.ifBlank { "Chapter ${rc.chapterNumber}" },
                    chapterNumber = rc.chapterNumber,
                    scanlator = rc.scanlator,
                    uploadedAt = when {
                        rc.dateUpload > 0L -> rc.dateUpload
                        rc.dateFetch > 0L -> rc.dateFetch
                        else -> System.currentTimeMillis()
                    },
                    read = rc.read,
                    bookmark = rc.bookmark,
                    lastPageRead = rc.lastPageRead
                )
            }

            for (rh in rm.history) {
                val chId = chapterUrlToId[rh.url] ?: continue
                history += TachibkHistoryItem(
                    mangaId = mangaId,
                    chapterId = chId,
                    readAt = rh.lastRead
                )
            }
        }

        return TachibkImportResult(
            categories = categories,
            mangas = mangas,
            chapters = chapters,
            history = history,
            sourceNames = sourceNames
        )
    }

    private data class RawBackupCategory(
        val name: String = "",
        val order: Int = 0
    )

    private data class RawBackupSource(
        val name: String = "",
        val sourceId: Long = 0L
    )

    private data class RawBackupChapter(
        val url: String = "",
        val name: String = "",
        val scanlator: String = "",
        val read: Boolean = false,
        val bookmark: Boolean = false,
        val lastPageRead: Int = 0,
        val dateFetch: Long = 0L,
        val dateUpload: Long = 0L,
        val chapterNumber: Float = 0f
    )

    private data class RawBackupHistory(
        val url: String = "",
        val lastRead: Long = 0L
    )

    private data class RawBackupManga(
        val source: Long = 0L,
        val url: String = "",
        val title: String = "",
        val artist: String = "",
        val author: String = "",
        val description: String = "",
        val genres: List<String> = emptyList(),
        val status: Int = 0,
        val thumbnailUrl: String = "",
        val dateAdded: Long = 0L,
        val categories: List<Long> = emptyList(),
        val chapters: List<RawBackupChapter> = emptyList(),
        val history: List<RawBackupHistory> = emptyList()
    )

    private fun parseCategory(bytes: ByteArray): RawBackupCategory {
        var name = ""
        var order = 0
        val r = ProtoReader(bytes)
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == WIRE_BYTES) name = r.readString() else r.skip(wire)
                2 -> if (wire == WIRE_VARINT) order = r.readVarint().toInt() else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return RawBackupCategory(name = name, order = order)
    }

    private fun parseSource(bytes: ByteArray): RawBackupSource {
        var name = ""
        var sourceId = 0L
        val r = ProtoReader(bytes)
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == WIRE_BYTES) name = r.readString() else r.skip(wire)
                2 -> if (wire == WIRE_VARINT) sourceId = r.readVarint() else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return RawBackupSource(name = name, sourceId = sourceId)
    }

    private fun parseManga(bytes: ByteArray): RawBackupManga {
        var source = 0L
        var url = ""
        var title = ""
        var artist = ""
        var author = ""
        var description = ""
        val genres = mutableListOf<String>()
        var status = 0
        var thumbnailUrl = ""
        var dateAdded = 0L
        val categories = mutableListOf<Long>()
        val chapters = mutableListOf<RawBackupChapter>()
        val history = mutableListOf<RawBackupHistory>()

        val r = ProtoReader(bytes)
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == WIRE_VARINT) source = r.readVarint() else r.skip(wire)
                2 -> if (wire == WIRE_BYTES) url = r.readString() else r.skip(wire)
                3 -> if (wire == WIRE_BYTES) title = r.readString() else r.skip(wire)
                4 -> if (wire == WIRE_BYTES) artist = r.readString() else r.skip(wire)
                5 -> if (wire == WIRE_BYTES) author = r.readString() else r.skip(wire)
                6 -> if (wire == WIRE_BYTES) description = r.readString() else r.skip(wire)
                7 -> if (wire == WIRE_BYTES) genres += r.readString() else r.skip(wire)
                8 -> if (wire == WIRE_VARINT) status = r.readVarint().toInt() else r.skip(wire)
                9 -> if (wire == WIRE_BYTES) thumbnailUrl = r.readString() else r.skip(wire)
                13 -> if (wire == WIRE_VARINT) dateAdded = r.readVarint() else r.skip(wire)
                16 -> if (wire == WIRE_BYTES) chapters += parseChapter(r.readBytes()) else r.skip(wire)
                17 -> when (wire) {
                    WIRE_VARINT -> categories += r.readVarint()
                    WIRE_BYTES -> {
                        val packed = ProtoReader(r.readBytes())
                        while (!packed.eof()) categories += packed.readVarint()
                    }
                    else -> r.skip(wire)
                }
                18 -> if (wire == WIRE_BYTES) history += parseHistory(r.readBytes()) else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return RawBackupManga(
            source = source,
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genres = genres,
            status = status,
            thumbnailUrl = thumbnailUrl,
            dateAdded = dateAdded,
            categories = categories,
            chapters = chapters,
            history = history
        )
    }

    private fun parseChapter(bytes: ByteArray): RawBackupChapter {
        var url = ""
        var name = ""
        var scanlator = ""
        var read = false
        var bookmark = false
        var lastPageRead = 0
        var dateFetch = 0L
        var dateUpload = 0L
        var chapterNumber = 0f

        val r = ProtoReader(bytes)
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == WIRE_BYTES) url = r.readString() else r.skip(wire)
                2 -> if (wire == WIRE_BYTES) name = r.readString() else r.skip(wire)
                3 -> if (wire == WIRE_BYTES) scanlator = r.readString() else r.skip(wire)
                4 -> if (wire == WIRE_VARINT) read = r.readVarint() != 0L else r.skip(wire)
                5 -> if (wire == WIRE_VARINT) bookmark = r.readVarint() != 0L else r.skip(wire)
                6 -> if (wire == WIRE_VARINT) lastPageRead = r.readVarint().toInt() else r.skip(wire)
                7 -> if (wire == WIRE_VARINT) dateFetch = r.readVarint() else r.skip(wire)
                8 -> if (wire == WIRE_VARINT) dateUpload = r.readVarint() else r.skip(wire)
                9 -> if (wire == WIRE_FIXED32) chapterNumber = r.readFloatFixed32() else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return RawBackupChapter(
            url = url,
            name = name,
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = dateFetch,
            dateUpload = dateUpload,
            chapterNumber = chapterNumber
        )
    }

    private fun parseHistory(bytes: ByteArray): RawBackupHistory {
        var url = ""
        var lastRead = 0L
        val r = ProtoReader(bytes)
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val wire = tag and 0x7
            when (field) {
                1 -> if (wire == WIRE_BYTES) url = r.readString() else r.skip(wire)
                2 -> if (wire == WIRE_VARINT) lastRead = r.readVarint() else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return RawBackupHistory(url = url, lastRead = lastRead)
    }

    private class ProtoReader(private val buf: ByteArray) {
        private var pos = 0

        fun eof(): Boolean = pos >= buf.size

        fun readVarint(): Long {
            var shift = 0
            var result = 0L
            while (pos < buf.size) {
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
            return result
        }

        fun readBytes(): ByteArray {
            val len = readVarint().toInt().coerceAtLeast(0)
            val end = (pos + len).coerceAtMost(buf.size)
            val out = buf.copyOfRange(pos, end)
            pos = end
            return out
        }

        fun readString(): String = readBytes().toString(Charsets.UTF_8)

        fun readFloatFixed32(): Float {
            val end = (pos + 4).coerceAtMost(buf.size)
            val slice = buf.copyOfRange(pos, end)
            pos = end
            if (slice.size < 4) return 0f
            return ByteBuffer.wrap(slice).order(ByteOrder.LITTLE_ENDIAN).float
        }

        fun skip(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint()
                WIRE_FIXED64 -> pos = (pos + 8).coerceAtMost(buf.size)
                WIRE_BYTES -> {
                    val len = readVarint().toInt().coerceAtLeast(0)
                    pos = (pos + len).coerceAtMost(buf.size)
                }
                WIRE_FIXED32 -> pos = (pos + 4).coerceAtMost(buf.size)
                else -> pos = buf.size
            }
        }
    }

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
