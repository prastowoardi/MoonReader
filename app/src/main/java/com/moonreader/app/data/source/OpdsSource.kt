package com.moonreader.app.data.source

import android.content.Context
import android.net.Uri
import com.moonreader.app.data.local.OpdsServerConfig
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.source.MangaPageResult
import com.moonreader.app.domain.source.MangaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class OpdsFeedLink(
    val href: String,
    val rel: String,
    val type: String,
    val pseCount: Int? = null
)

data class OpdsParsedEntry(
    val id: String,
    val title: String,
    val author: String,
    val summary: String,
    val categories: List<String>,
    val updatedMillis: Long,
    val links: List<OpdsFeedLink>
)

data class OpdsParsedFeed(
    val entries: List<OpdsParsedEntry>,
    val nextPageUrl: String?,
    val searchTemplateUrl: String?
)

object OpdsParser {
    private val chapterNumRegex = Regex("(?:ch(?:apter)?\\.?|ep(?:isode)?\\.?|#)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val fallbackNumRegex = Regex("(\\d+(?:\\.\\d+)?)")

    fun parseFeed(xml: String, baseUrl: String): OpdsParsedFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        var nextUrl: String? = null
        var searchUrl: String? = null

        val topChildren = root.childNodes
        for (i in 0 until topChildren.length) {
            val node = topChildren.item(i) as? Element ?: continue
            if (node.localName == "link") {
                val rel = node.getAttribute("rel")
                val type = node.getAttribute("type")
                val href = resolveUrl(baseUrl, node.getAttribute("href"))
                if (rel == "next" && href.isNotBlank()) {
                    nextUrl = href
                } else if (rel == "search" && href.isNotBlank() &&
                    (type.contains("atom+xml") || type.contains("opensearch") || searchUrl == null)
                ) {
                    searchUrl = href
                }
            }
        }

        val entryNodes = root.getElementsByTagNameNS("*", "entry")
        val entries = mutableListOf<OpdsParsedEntry>()
        for (i in 0 until entryNodes.length) {
            val elem = entryNodes.item(i) as? Element ?: continue
            entries += parseEntry(elem, baseUrl)
        }

        return OpdsParsedFeed(
            entries = entries,
            nextPageUrl = nextUrl,
            searchTemplateUrl = searchUrl
        )
    }

    fun parseOpenSearchTemplate(xml: String, baseUrl: String): String? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val urls = doc.documentElement.getElementsByTagNameNS("*", "Url")
        for (i in 0 until urls.length) {
            val el = urls.item(i) as? Element ?: continue
            val type = el.getAttribute("type")
            val template = el.getAttribute("template")
            if (template.isNotBlank() && (type.contains("atom+xml") || type.isBlank())) {
                return resolveUrl(baseUrl, template)
            }
        }
        return null
    }

    fun toMangaPageResult(
        feed: OpdsParsedFeed,
        sourceId: String
    ): MangaPageResult {
        val mangas = feed.entries.mapNotNull { entry ->
            val targetLink = pickEntryTargetUrl(entry) ?: return@mapNotNull null
            val coverUrl = pickCoverUrl(entry).orEmpty()
            val stableKey = shortHash(entry.id.ifBlank { targetLink })
            Manga(
                id = "$sourceId:$stableKey",
                sourceId = sourceId,
                url = targetLink,
                title = entry.title.ifBlank { "Tanpa Judul" },
                author = entry.author,
                artist = entry.author,
                description = entry.summary,
                genres = entry.categories,
                status = MangaStatus.UNKNOWN,
                coverUrl = coverUrl,
                lastUpdatedAt = entry.updatedMillis
            )
        }
        return MangaPageResult(mangas = mangas, hasNextPage = !feed.nextPageUrl.isNullOrBlank())
    }

    fun toChapterList(feed: OpdsParsedFeed, mangaId: String): List<Chapter> {
        val total = feed.entries.size
        return feed.entries.mapIndexedNotNull { idx, entry ->
            val pseLink = entry.links.firstOrNull {
                it.rel.contains("opds-pse") || (it.pseCount != null && it.pseCount > 0)
            }
            val cbzLink = entry.links.firstOrNull {
                it.type.contains("comicbook") || it.type.contains("application/zip") ||
                    it.href.endsWith(".cbz", ignoreCase = true) || it.href.endsWith(".zip", ignoreCase = true)
            }
            val feedLink = entry.links.firstOrNull {
                it.type.contains("atom+xml") || it.rel == "subsection"
            }

            val encodedTarget = when {
                pseLink != null && (pseLink.pseCount ?: 0) > 0 ->
                    "pse|${pseLink.pseCount}|${pseLink.href}"
                cbzLink != null ->
                    "cbz|${cbzLink.href}"
                feedLink != null ->
                    "feed|${feedLink.href}"
                else -> return@mapIndexedNotNull null
            }

            val chNumber = extractChapterNumber(entry.title, (total - idx).toFloat())
            val chKey = shortHash(entry.id.ifBlank { encodedTarget })

            Chapter(
                id = "$mangaId:opds_$chKey",
                mangaId = mangaId,
                url = encodedTarget,
                name = entry.title.ifBlank { "Chapter ${idx + 1}" },
                chapterNumber = chNumber,
                scanlator = entry.author.ifBlank { "OPDS" },
                uploadedAt = entry.updatedMillis,
                totalPages = pseLink?.pseCount ?: 0
            )
        }.sortedByDescending { it.chapterNumber }
    }

    fun expandPsePages(count: Int, templateUrl: String): List<Page> {
        if (count <= 0) return emptyList()
        return (0 until count).map { idx ->
            val resolved = templateUrl
                .replace("{pageNumber}", idx.toString())
                .replace("{maxWidth}", "1600")
            Page(index = idx, imageUrl = resolved)
        }
    }

    fun pickNavigationHref(feed: OpdsParsedFeed, preferLatest: Boolean): String? {
        if (feed.entries.isEmpty()) return null
        val looksLikeSeriesList = feed.entries.any { e ->
            e.links.any {
                it.rel.contains("image") || it.rel.contains("opds-pse") ||
                    it.type.startsWith("image/") || it.type.contains("comicbook")
            }
        }
        if (looksLikeSeriesList) return null

        val keywords = if (preferLatest) {
            listOf("latest", "terbaru", "new", "recent", "updated")
        } else {
            listOf("all", "series", "semua", "library", "libraries", "browse", "books", "popular")
        }

        val matching = feed.entries.firstOrNull { entry ->
            val lower = entry.title.lowercase()
            keywords.any { kw -> lower.contains(kw) }
        } ?: feed.entries.firstOrNull()

        return matching?.links?.firstOrNull {
            it.type.contains("atom+xml") || it.rel == "subsection" || it.href.isNotBlank()
        }?.href
    }

    private fun parseEntry(elem: Element, baseUrl: String): OpdsParsedEntry {
        val id = firstChildText(elem, "id").orEmpty()
        val title = firstChildText(elem, "title").orEmpty()
        val summary = firstChildText(elem, "summary")
            ?: firstChildText(elem, "content")
            ?: ""

        var author = ""
        val authorNodes = elem.getElementsByTagNameNS("*", "author")
        if (authorNodes.length > 0) {
            val authorEl = authorNodes.item(0) as? Element
            if (authorEl != null) {
                author = firstChildText(authorEl, "name").orEmpty()
            }
        }

        val categories = mutableListOf<String>()
        val catNodes = elem.getElementsByTagNameNS("*", "category")
        for (i in 0 until catNodes.length) {
            val catEl = catNodes.item(i) as? Element ?: continue
            val term = catEl.getAttribute("label").ifBlank { catEl.getAttribute("term") }
            if (term.isNotBlank()) categories += term
        }

        val updatedText = firstChildText(elem, "updated") ?: firstChildText(elem, "published")
        val updatedMillis = updatedText?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        } ?: System.currentTimeMillis()

        val links = mutableListOf<OpdsFeedLink>()
        val linkNodes = elem.getElementsByTagNameNS("*", "link")
        for (i in 0 until linkNodes.length) {
            val l = linkNodes.item(i) as? Element ?: continue
            val href = resolveUrl(baseUrl, l.getAttribute("href"))
            if (href.isBlank()) continue
            val rel = l.getAttribute("rel")
            val type = l.getAttribute("type")
            val pseCount = l.getAttribute("pse:count").ifBlank {
                l.getAttributeNS("*", "count")
            }.toIntOrNull()
            links += OpdsFeedLink(href = href, rel = rel, type = type, pseCount = pseCount)
        }

        return OpdsParsedEntry(
            id = id,
            title = title,
            author = author,
            summary = summary,
            categories = categories,
            updatedMillis = updatedMillis,
            links = links
        )
    }

    private fun pickEntryTargetUrl(entry: OpdsParsedEntry): String? {
        return entry.links.firstOrNull { it.rel == "subsection" && it.type.contains("atom+xml") }?.href
            ?: entry.links.firstOrNull { it.type.contains("atom+xml") }?.href
            ?: entry.links.firstOrNull { it.rel.contains("acquisition") || it.rel.contains("opds-pse") }?.href
            ?: entry.links.firstOrNull { !it.rel.contains("image") && !it.type.startsWith("image/") }?.href
    }

    private fun pickCoverUrl(entry: OpdsParsedEntry): String? {
        return entry.links.firstOrNull { it.rel == "http://opds-spec.org/image" }?.href
            ?: entry.links.firstOrNull { it.rel == "http://opds-spec.org/image/thumbnail" }?.href
            ?: entry.links.firstOrNull { it.rel.contains("image") || it.type.startsWith("image/") }?.href
    }

    private fun firstChildText(parent: Element, localName: String): String? {
        val nodes: NodeList = parent.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (el.localName == localName) {
                return el.textContent?.trim()
            }
        }
        return null
    }

    private fun extractChapterNumber(title: String, fallback: Float): Float {
        chapterNumRegex.find(title)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        fallbackNumRegex.find(title)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        return fallback
    }

    internal fun resolveUrl(baseUrl: String, href: String): String {
        val trimmed = href.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val sanitized = trimmed.replace("{", "__LBRACE__").replace("}", "__RBRACE__")
        return runCatching {
            URI(baseUrl).resolve(sanitized).toString()
                .replace("__LBRACE__", "{")
                .replace("__RBRACE__", "}")
        }.getOrDefault(trimmed)
    }

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Generic OPDS v1.2 / OPDS-PSE source connector.
 * Works with Komga, Kavita, Suwayomi-Server, Internet Archive, and other standard OPDS catalogs.
 * Authentication is optional (leave username/password blank for no-login servers).
 */
class OpdsSource(
    private val context: Context,
    private val client: OkHttpClient,
    val config: OpdsServerConfig
) : MangaSource {

    private val cbzCacheDir: File by lazy {
        File(context.cacheDir, "opds_cbz").apply { mkdirs() }
    }

    private var cachedNextPageUrls = mutableMapOf<Int, String>()
    private var cachedSearchTemplate: String? = null

    override val info = SourceInfo(
        id = config.id,
        name = config.name,
        lang = if (config.username.isBlank()) "OPDS • Tanpa Login" else "OPDS • Auth",
        description = config.baseUrl,
        isLocal = false,
        supportsSearch = true,
        supportsLatest = true
    )

    override suspend fun getPopularManga(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        loadBrowsePage(page, preferLatest = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        loadBrowsePage(page, preferLatest = true)
    }

    override suspend fun searchManga(page: Int, query: String): MangaPageResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext getPopularManga(page)

        val encoded = URLEncoder.encode(q, "UTF-8")
        val template = cachedSearchTemplate ?: discoverSearchTemplate()
        val searchUrl = if (template != null && template.contains("{searchTerms}")) {
            template.replace("{searchTerms}", encoded)
        } else {
            val sep = if (config.baseUrl.contains("?")) "&" else "?"
            "${config.baseUrl}${sep}search=$encoded"
        }

        val xml = fetchText(searchUrl)
        val parsed = OpdsParser.parseFeed(xml, searchUrl)
        OpdsParser.toMangaPageResult(parsed, info.id)
    }

    override suspend fun getMangaDetails(manga: Manga): Manga = withContext(Dispatchers.IO) {
        manga
    }

    override suspend fun getChapterList(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        val xml = fetchText(manga.url)
        val parsed = OpdsParser.parseFeed(xml, manga.url)
        OpdsParser.toChapterList(parsed, manga.id)
    }

    override suspend fun getPageList(chapter: Chapter): List<Page> = withContext(Dispatchers.IO) {
        val raw = chapter.url
        when {
            raw.startsWith("pse|") -> {
                val parts = raw.split("|", limit = 3)
                val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val template = parts.getOrNull(2).orEmpty()
                OpdsParser.expandPsePages(count, template)
            }
            raw.startsWith("cbz|") -> {
                val downloadUrl = raw.removePrefix("cbz|")
                extractRemoteCbzPages(chapter.id, downloadUrl)
            }
            raw.startsWith("feed|") -> {
                val feedUrl = raw.removePrefix("feed|")
                val xml = fetchText(feedUrl)
                val parsed = OpdsParser.parseFeed(xml, feedUrl)
                val images = parsed.entries.flatMap { e ->
                    e.links.filter { it.type.startsWith("image/") || it.rel.contains("image") }.map { it.href }
                }
                images.mapIndexed { idx, img -> Page(index = idx, imageUrl = img) }
            }
            else -> emptyList()
        }
    }

    private fun loadBrowsePage(page: Int, preferLatest: Boolean): MangaPageResult {
        val startUrl = if (page <= 1) {
            cachedNextPageUrls.clear()
            resolveSeriesFeedUrl(config.baseUrl, preferLatest, depth = 0)
        } else {
            cachedNextPageUrls[page] ?: return MangaPageResult(emptyList(), false)
        }

        val xml = fetchText(startUrl)
        val parsed = OpdsParser.parseFeed(xml, startUrl)
        if (!parsed.searchTemplateUrl.isNullOrBlank()) {
            cachedSearchTemplate = parsed.searchTemplateUrl
        }
        if (!parsed.nextPageUrl.isNullOrBlank()) {
            cachedNextPageUrls[page + 1] = parsed.nextPageUrl
        }
        return OpdsParser.toMangaPageResult(parsed, info.id)
    }

    private fun resolveSeriesFeedUrl(url: String, preferLatest: Boolean, depth: Int): String {
        if (depth >= 2) return url
        val xml = fetchText(url)
        val parsed = OpdsParser.parseFeed(xml, url)
        if (!parsed.searchTemplateUrl.isNullOrBlank()) {
            cachedSearchTemplate = parsed.searchTemplateUrl
        }
        val subHref = OpdsParser.pickNavigationHref(parsed, preferLatest)
        return if (subHref != null && subHref != url) {
            resolveSeriesFeedUrl(subHref, preferLatest, depth + 1)
        } else {
            url
        }
    }

    private fun discoverSearchTemplate(): String? {
        val xml = runCatching { fetchText(config.baseUrl) }.getOrNull() ?: return null
        val parsed = runCatching { OpdsParser.parseFeed(xml, config.baseUrl) }.getOrNull() ?: return null
        val link = parsed.searchTemplateUrl ?: return null
        if (link.contains("{searchTerms}")) return link
        val openSearchXml = runCatching { fetchText(link) }.getOrNull() ?: return null
        return runCatching { OpdsParser.parseOpenSearchTemplate(openSearchXml, link) }.getOrNull()
    }

    private fun extractRemoteCbzPages(chapterId: String, downloadUrl: String): List<Page> {
        val safeKey = chapterId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val archiveFile = File(cbzCacheDir, "$safeKey.cbz")
        val extractDir = File(cbzCacheDir, "${safeKey}_pages").apply { mkdirs() }

        if (!archiveFile.exists() || archiveFile.length() == 0L) {
            val reqBuilder = Request.Builder().url(downloadUrl).get()
            applyAuth(reqBuilder)
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Gagal mengunduh CBZ OPDS (HTTP ${resp.code})")
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(archiveFile).use { output -> input.copyTo(output) }
                } ?: throw IOException("Respons kosong saat mengunduh CBZ")
            }
        }

        val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        return ZipFile(archiveFile).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { name ->
                    !name.contains("__MACOSX") &&
                        name.substringAfterLast('.', "").lowercase() in imageExts
                }
                .sortedWith(NaturalOrderComparator)
                .toList()

            entries.mapIndexed { idx, entryName ->
                val ext = entryName.substringAfterLast('.', "jpg")
                val outFile = File(extractDir, "page_%04d.%s".format(idx, ext))
                if (!outFile.exists() || outFile.length() == 0L) {
                    zip.getInputStream(zip.getEntry(entryName)).use { zin ->
                        FileOutputStream(outFile).use { fout -> zin.copyTo(fout) }
                    }
                }
                Page(index = idx, imageUrl = Uri.fromFile(outFile).toString())
            }
        }
    }

    private fun fetchText(url: String): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "MoonReader/1.0 (Android)")
            .get()
        applyAuth(builder)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("OPDS error HTTP ${resp.code}")
            }
            return resp.body?.string() ?: throw IOException("Respons OPDS kosong")
        }
    }

    private fun applyAuth(builder: Request.Builder) {
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }
    }
}
