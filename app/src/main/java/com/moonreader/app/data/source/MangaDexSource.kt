package com.moonreader.app.data.source

import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.source.MangaPageResult
import com.moonreader.app.domain.source.MangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

object MangaDexParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseMangaList(rawJson: String, sourceId: String): MangaPageResult {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        val total = root["total"]?.jsonPrimitive?.intOrNull ?: 0
        val offset = root["offset"]?.jsonPrimitive?.intOrNull ?: 0
        val limit = root["limit"]?.jsonPrimitive?.intOrNull ?: 20

        val mangas = data.mapNotNull { elem ->
            runCatching { parseMangaObject(elem.jsonObject, sourceId) }.getOrNull()
        }
        val hasNext = (offset + mangas.size) < total && mangas.size >= limit
        return MangaPageResult(mangas = mangas, hasNextPage = hasNext)
    }

    fun parseSingleManga(rawJson: String, sourceId: String): Manga {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val obj = root["data"]?.jsonObject ?: throw IOException("Respons MangaDex tidak valid")
        return parseMangaObject(obj, sourceId)
    }

    fun parseMangaObject(obj: JsonObject, sourceId: String): Manga {
        val id = obj["id"]?.jsonPrimitive?.content ?: throw IOException("ID manga kosong")
        val attrs = (obj["attributes"] as? JsonObject) ?: JsonObject(emptyMap())

        val titleMap = attrs["title"] as? JsonObject
        val title = pickLocalizedText(titleMap)
            ?: pickAltTitle(attrs["altTitles"] as? JsonArray)
            ?: "Tanpa Judul"

        val description = pickLocalizedText(attrs["description"] as? JsonObject).orEmpty()

        val status = when (attrs["status"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "ongoing" -> MangaStatus.ONGOING
            "completed" -> MangaStatus.COMPLETED
            "hiatus" -> MangaStatus.HIATUS
            "cancelled" -> MangaStatus.CANCELLED
            else -> MangaStatus.UNKNOWN
        }

        val genres = (attrs["tags"] as? JsonArray)?.mapNotNull { tagElem ->
            val tagAttrs = (tagElem as? JsonObject)?.get("attributes") as? JsonObject
            pickLocalizedText(tagAttrs?.get("name") as? JsonObject)
        }.orEmpty()

        val relationships = (obj["relationships"] as? JsonArray) ?: JsonArray(emptyList())
        var author = ""
        var artist = ""
        var coverFileName = ""

        for (relElem in relationships) {
            val rel = (relElem as? JsonObject) ?: continue
            val type = rel["type"]?.jsonPrimitive?.contentOrNull
            val relAttrs = (rel["attributes"] as? JsonObject) ?: continue
            when (type) {
                "author" -> if (author.isBlank()) {
                    author = relAttrs["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
                "artist" -> if (artist.isBlank()) {
                    artist = relAttrs["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
                "cover_art" -> if (coverFileName.isBlank()) {
                    coverFileName = relAttrs["fileName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }
            }
        }

        val coverUrl = if (coverFileName.isNotBlank()) {
            "https://uploads.mangadex.org/covers/$id/$coverFileName.512.jpg"
        } else {
            ""
        }

        return Manga(
            id = "$sourceId:$id",
            sourceId = sourceId,
            url = id,
            title = title,
            author = author,
            artist = artist.ifBlank { author },
            description = description,
            genres = genres,
            status = status,
            coverUrl = coverUrl
        )
    }

    fun parseChapterFeed(rawJson: String, mangaId: String): List<Chapter> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()

        return data.mapNotNull { elem ->
            val obj = elem.jsonObject
            val chapterId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null

            val externalUrl = attrs["externalUrl"]?.jsonPrimitive?.contentOrNull
            val pagesCount = attrs["pages"]?.jsonPrimitive?.intOrNull ?: 0
            if (!externalUrl.isNullOrBlank() && pagesCount == 0) {
                return@mapNotNull null
            }

            val chStr = attrs["chapter"]?.jsonPrimitive?.contentOrNull
            val chNum = chStr?.toFloatOrNull() ?: 0f
            val chTitle = attrs["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val lang = attrs["translatedLanguage"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val displayName = buildString {
                if (!chStr.isNullOrBlank()) {
                    append("Ch. ").append(chStr)
                } else {
                    append("Oneshot")
                }
                if (chTitle.isNotBlank()) {
                    append(" - ").append(chTitle)
                }
            }

            val rels = (obj["relationships"] as? JsonArray) ?: JsonArray(emptyList())
            val groupName = rels.firstOrNull {
                (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "scanlation_group"
            }?.let { (it as? JsonObject)?.get("attributes") as? JsonObject }
                ?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()

            val scanlatorLabel = listOfNotNull(
                lang.uppercase().takeIf { it.isNotBlank() },
                groupName.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            val publishAt = attrs["publishAt"]?.jsonPrimitive?.contentOrNull
            val uploadedMillis = publishAt?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: System.currentTimeMillis()

            Chapter(
                id = "mangadex:$chapterId",
                mangaId = mangaId,
                url = chapterId,
                name = displayName,
                chapterNumber = chNum,
                scanlator = scanlatorLabel,
                uploadedAt = uploadedMillis,
                totalPages = pagesCount
            )
        }.sortedByDescending { it.chapterNumber }
    }

    fun parseAtHomePages(rawJson: String): List<Page> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val baseUrl = root["baseUrl"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Base URL halaman MangaDex tidak ditemukan")
        val chapterObj = root["chapter"]?.jsonObject
            ?: throw IOException("Data chapter MangaDex tidak ditemukan")
        val hash = chapterObj["hash"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Hash chapter MangaDex kosong")
        val files = chapterObj["data"]?.jsonArray
            ?: chapterObj["dataSaver"]?.jsonArray
            ?: JsonArray(emptyList())

        val modeFolder = if (chapterObj["data"]?.jsonArray?.isNotEmpty() == true) "data" else "data-saver"

        return files.mapIndexedNotNull { idx, fileElem ->
            val fileName = fileElem.jsonPrimitive.contentOrNull ?: return@mapIndexedNotNull null
            Page(
                index = idx,
                imageUrl = "$baseUrl/$modeFolder/$hash/$fileName"
            )
        }
    }

    private fun pickLocalizedText(map: JsonObject?): String? {
        if (map == null || map.isEmpty()) return null
        val priority = listOf("id", "en", "ja-ro", "ko-ro", "ja")
        for (lang in priority) {
            val value = map[lang]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return map.values.firstOrNull()?.jsonPrimitive?.contentOrNull
    }

    private fun pickAltTitle(array: JsonArray?): String? {
        if (array == null) return null
        for (elem in array) {
            val obj = elem.jsonObject
            val candidate = pickLocalizedText(obj)
            if (!candidate.isNullOrBlank()) return candidate
        }
        return null
    }
}

/**
 * Integrasi resmi dengan MangaDex Public Open API v5 (https://api.mangadex.org).
 */
class MangaDexSource(
    private val client: OkHttpClient,
    private val preferIndonesianProvider: () -> Boolean = { true }
) : MangaSource {

    companion object {
        const val SOURCE_ID = "mangadex"
        private const val BASE_API = "https://api.mangadex.org"
        private const val PAGE_LIMIT = 20
    }

    override val info = SourceInfo(
        id = SOURCE_ID,
        name = "MangaDex",
        lang = "ID / EN",
        description = "API publik resmi MangaDex v5 (gratis & legal)",
        isLocal = false,
        supportsSearch = true,
        supportsLatest = true
    )

    override suspend fun getPopularManga(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        val offset = ((page - 1).coerceAtLeast(0)) * PAGE_LIMIT
        val url = "$BASE_API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("order[followedCount]", "desc")
            .addQueryParameter("hasAvailableChapters", "true")
            .build()

        val body = executeGet(url.toString())
        MangaDexParser.parseMangaList(body, SOURCE_ID)
    }

    override suspend fun getLatestUpdates(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        val offset = ((page - 1).coerceAtLeast(0)) * PAGE_LIMIT
        val url = "$BASE_API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("order[latestUploadedChapter]", "desc")
            .addQueryParameter("hasAvailableChapters", "true")
            .build()

        val body = executeGet(url.toString())
        MangaDexParser.parseMangaList(body, SOURCE_ID)
    }

    override suspend fun searchManga(page: Int, query: String): MangaPageResult = withContext(Dispatchers.IO) {
        val offset = ((page - 1).coerceAtLeast(0)) * PAGE_LIMIT
        val url = "$BASE_API/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("title", query.trim())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("order[relevance]", "desc")
            .build()

        val body = executeGet(url.toString())
        MangaDexParser.parseMangaList(body, SOURCE_ID)
    }

    override suspend fun getMangaDetails(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val url = "$BASE_API/manga/${manga.url}".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .build()

        val body = executeGet(url.toString())
        val parsed = MangaDexParser.parseSingleManga(body, SOURCE_ID)
        parsed.copy(
            inLibrary = manga.inLibrary,
            categoryId = manga.categoryId,
            addedAt = manga.addedAt
        )
    }

    override suspend fun getChapterList(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        val builder = "$BASE_API/manga/${manga.url}/feed".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("offset", "0")
            .addQueryParameter("includeExternalUrl", "0")
            .addQueryParameter("includes[]", "scanlation_group")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("order[chapter]", "desc")

        if (preferIndonesianProvider()) {
            builder.addQueryParameter("translatedLanguage[]", "id")
            builder.addQueryParameter("translatedLanguage[]", "en")
        } else {
            builder.addQueryParameter("translatedLanguage[]", "en")
            builder.addQueryParameter("translatedLanguage[]", "id")
        }

        val body = executeGet(builder.build().toString())
        MangaDexParser.parseChapterFeed(body, manga.id)
    }

    override suspend fun getPageList(chapter: Chapter): List<Page> = withContext(Dispatchers.IO) {
        val body = executeGet("$BASE_API/at-home/server/${chapter.url}")
        MangaDexParser.parseAtHomePages(body)
    }

    private fun executeGet(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MoonReader/1.0 (Android)")
            .get()
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("MangaDex API error HTTP ${response.code}")
            }
            return response.body?.string() ?: throw IOException("Respons kosong dari MangaDex")
        }
    }
}
