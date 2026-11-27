package com.moonreader.app.data.source

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.MangaStatus
import com.moonreader.app.domain.model.Page
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.source.MangaPageResult
import com.moonreader.app.domain.source.MangaSource

/**
 * Offline demo catalog source so every reader mode, library badge, update feed,
 * and download flow can be tested immediately without internet.
 */
class DemoCatalogSource : MangaSource {

    companion object {
        const val SOURCE_ID = "moon_demo"
    }

    override val info = SourceInfo(
        id = SOURCE_ID,
        name = "Moon Demo Catalog",
        lang = "ID",
        description = "Katalog demo offline bawaan untuk uji coba Reader, Library & Download",
        isLocal = false,
        supportsSearch = true,
        supportsLatest = true
    )

    private val now = System.currentTimeMillis()
    private val oneDayMs = 86_400_000L

    val demoCatalog: List<Manga> = listOf(
        Manga(
            id = "$SOURCE_ID:cyber_moon",
            sourceId = SOURCE_ID,
            url = "cyber_moon",
            title = "Cyber Moon Chronicles",
            author = "Raka Pratama",
            artist = "Raka Pratama",
            description = "Di Neo-Jakarta tahun 2099, seorang kurir data menemukan arsip satelit kuno yang terhubung dengan memori kota bulan.",
            genres = listOf("Sci-Fi", "Action", "Cyberpunk"),
            status = MangaStatus.ONGOING,
            coverUrl = "demo://cover/cyber_moon",
            lastUpdatedAt = now - 3_600_000L
        ),
        Manga(
            id = "$SOURCE_ID:akademi_nusantara",
            sourceId = SOURCE_ID,
            url = "akademi_nusantara",
            title = "Akademi Pedang Nusantara",
            author = "Sari Wijaya",
            artist = "Dimas Putra",
            description = "Kisah pendekar muda yang menuntut ilmu di akademi silat terapung demi mengembalikan pusaka yang hilang.",
            genres = listOf("Action", "Fantasy", "Adventure"),
            status = MangaStatus.ONGOING,
            coverUrl = "demo://cover/akademi_nusantara",
            lastUpdatedAt = now - oneDayMs
        ),
        Manga(
            id = "$SOURCE_ID:kopi_senja",
            sourceId = SOURCE_ID,
            url = "kopi_senja",
            title = "Kopi & Senja di Stasiun Lama",
            author = "Nadia Lestari",
            artist = "Nadia Lestari",
            description = "Kumpulan cerita hangat para komuter yang selalu bertemu di kedai kopi kecil menjelang kereta terakhir.",
            genres = listOf("Slice of Life", "Drama", "Romance"),
            status = MangaStatus.COMPLETED,
            coverUrl = "demo://cover/kopi_senja",
            lastUpdatedAt = now - (3 * oneDayMs)
        ),
        Manga(
            id = "$SOURCE_ID:starlight_drift",
            sourceId = SOURCE_ID,
            url = "starlight_drift",
            title = "Starlight Drift",
            author = "Kenji Takahashi",
            artist = "Kenji Takahashi",
            description = "Pembalap jalanan di jalur pegunungan malam hari menghadapi rival misterius yang hanya muncul saat gerhana.",
            genres = listOf("Sports", "Drama", "Seinen"),
            status = MangaStatus.HIATUS,
            coverUrl = "demo://cover/starlight_drift",
            lastUpdatedAt = now - (5 * oneDayMs)
        )
    )

    override suspend fun getPopularManga(page: Int): MangaPageResult {
        return if (page == 1) MangaPageResult(demoCatalog, false) else MangaPageResult(emptyList(), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangaPageResult {
        val sorted = demoCatalog.sortedByDescending { it.lastUpdatedAt }
        return if (page == 1) MangaPageResult(sorted, false) else MangaPageResult(emptyList(), false)
    }

    override suspend fun searchManga(page: Int, query: String): MangaPageResult {
        if (page > 1) return MangaPageResult(emptyList(), false)
        val q = query.trim().lowercase()
        val filtered = demoCatalog.filter {
            q.isBlank() || it.title.lowercase().contains(q) || it.genres.any { g -> g.lowercase().contains(q) }
        }
        return MangaPageResult(filtered, false)
    }

    override suspend fun getMangaDetails(manga: Manga): Manga {
        return demoCatalog.firstOrNull { it.url == manga.url }?.copy(
            inLibrary = manga.inLibrary,
            categoryId = manga.categoryId,
            addedAt = manga.addedAt
        ) ?: manga
    }

    override suspend fun getChapterList(manga: Manga): List<Chapter> {
        val count = when (manga.url) {
            "cyber_moon" -> 5
            "akademi_nusantara" -> 4
            else -> 3
        }
        return (count downTo 1).map { ch ->
            Chapter(
                id = "$SOURCE_ID:${manga.url}:ch$ch",
                mangaId = manga.id,
                url = "${manga.url}/$ch",
                name = "Chapter $ch - ${chapterSubtitle(manga.url, ch)}",
                chapterNumber = ch.toFloat(),
                scanlator = "ID • Moon Demo",
                uploadedAt = now - ((count - ch) * oneDayMs),
                totalPages = 6
            )
        }
    }

    override suspend fun getPageList(chapter: Chapter): List<Page> {
        val parts = chapter.url.split("/")
        val key = parts.getOrNull(0) ?: "cyber_moon"
        val chNum = parts.getOrNull(1) ?: "1"
        return (0 until 6).map { index ->
            Page(
                index = index,
                imageUrl = "demo://page/$key/$chNum/${index + 1}"
            )
        }
    }

    private fun chapterSubtitle(key: String, ch: Int): String = when (key) {
        "cyber_moon" -> when (ch) {
            1 -> "Sinyal dari Orbit"
            2 -> "Jalur Bawah Tanah"
            3 -> "Protokol Bulan"
            4 -> "Bayangan Server"
            else -> "Gerbang Terakhir"
        }
        "akademi_nusantara" -> when (ch) {
            1 -> "Ujian Masuk"
            2 -> "Bilah Bambu"
            3 -> "Kabut Lembah"
            else -> "Turnamen Musim Hujan"
        }
        else -> "Episode $ch"
    }
}

/**
 * Custom Coil Fetcher that renders crisp procedural comic covers and sample pages
 * for `demo://cover/...` and `demo://page/...` URIs.
 */
class DemoPageFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "demo") DemoPageFetcher(data, options) else null
        }
    }

    override suspend fun fetch(): FetchResult {
        val segments = uri.pathSegments
        val isCover = uri.host == "cover"
        val bitmap = if (isCover) {
            renderCover(segments.getOrNull(0) ?: "cyber_moon")
        } else {
            val key = segments.getOrNull(0) ?: "cyber_moon"
            val ch = segments.getOrNull(1) ?: "1"
            val page = segments.getOrNull(2) ?: "1"
            renderComicPage(key, ch, page)
        }

        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    private fun paletteFor(key: String): Pair<Int, Int> = when (key) {
        "cyber_moon" -> Color.parseColor("#0F2027") to Color.parseColor("#2979FF")
        "akademi_nusantara" -> Color.parseColor("#3E1E1E") to Color.parseColor("#E53935")
        "kopi_senja" -> Color.parseColor("#2E1F14") to Color.parseColor("#FB8C00")
        else -> Color.parseColor("#1A1438") to Color.parseColor("#8E24AA")
    }

    private fun titleFor(key: String): String = when (key) {
        "cyber_moon" -> "CYBER MOON\nCHRONICLES"
        "akademi_nusantara" -> "AKADEMI PEDANG\nNUSANTARA"
        "kopi_senja" -> "KOPI & SENJA"
        else -> "STARLIGHT\nDRIFT"
    }

    private fun renderCover(key: String): Bitmap {
        val w = 540
        val h = 780
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val (c1, c2) = paletteFor(key)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c1, c2, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 255, 255, 255)
        }
        canvas.drawCircle(w * 0.72f, h * 0.25f, 110f, moonPaint)

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(24f, 24f, w - 24f, h - 24f, framePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lines = titleFor(key).split("\n")
        var y = h * 0.62f
        for (line in lines) {
            canvas.drawText(line, 44f, y, textPaint)
            y += 56f
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            textSize = 22f
        }
        canvas.drawText("MOON DEMO EDITION", 44f, h - 54f, subPaint)
        return bmp
    }

    private fun renderComicPage(key: String, ch: String, page: String): Bitmap {
        val w = 900
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val (c1, c2) = paletteFor(key)

        canvas.drawColor(Color.parseColor("#F5F5F0"))

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Panel 1 (Top hero panel)
        val p1 = RectF(40f, 50f, w - 40f, 470f)
        fillPaint.shader = LinearGradient(p1.left, p1.top, p1.right, p1.bottom, c1, c2, Shader.TileMode.CLAMP)
        canvas.drawRect(p1, fillPaint)
        fillPaint.shader = null
        canvas.drawRect(p1, borderPaint)

        // Panel 2 & 3 (Middle split)
        val p2 = RectF(40f, 495f, w * 0.48f, 890f)
        val p3 = RectF(w * 0.51f, 495f, w - 40f, 890f)
        fillPaint.color = Color.parseColor("#E8ECF4")
        canvas.drawRect(p2, fillPaint)
        canvas.drawRect(p2, borderPaint)
        fillPaint.color = Color.parseColor("#DCE3F0")
        canvas.drawRect(p3, fillPaint)
        canvas.drawRect(p3, borderPaint)

        // Panel 4 (Bottom wide panel)
        val p4 = RectF(40f, 915f, w - 40f, 1270f)
        fillPaint.color = Color.parseColor("#1E2430")
        canvas.drawRect(p4, fillPaint)
        canvas.drawRect(p4, borderPaint)

        // Speech bubble in Panel 2
        val bubble = RectF(75f, 535f, w * 0.44f, 680f)
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(bubble, 32f, 32f, fillPaint)
        canvas.drawRoundRect(bubble, 32f, 32f, Paint(borderPaint).apply { strokeWidth = 4f })

        val darkText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Ch. $ch • Hal. $page", 105f, 600f, darkText)
        canvas.drawText("Test Reader Mode!", 105f, 640f, Paint(darkText).apply {
            textSize = 22f
            typeface = Typeface.DEFAULT
        })

        // Header label inside Panel 1
        val whiteBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(titleFor(key).replace("\n", " "), 75f, 150f, whiteBold)
        canvas.drawText("CHAPTER $ch  —  HALAMAN $page / 6", 75f, 215f, Paint(whiteBold).apply {
            textSize = 28f
            color = Color.argb(220, 255, 255, 255)
        })

        // Bottom caption
        canvas.drawText("Pinch untuk zoom • Ketuk tengah untuk kontrol", 75f, 1100f, Paint(whiteBold).apply {
            textSize = 28f
        })
        return bmp
    }
}
