package com.moonreader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.moonreader.app.data.local.AppUpdater
import com.moonreader.app.data.local.MoonDatabase
import com.moonreader.app.data.local.PreferencesStore
import com.moonreader.app.data.repository.MangaRepository
import com.moonreader.app.data.source.ChapterDownloader
import com.moonreader.app.data.source.DemoCatalogSource
import com.moonreader.app.data.source.DemoPageFetcher
import com.moonreader.app.data.source.LocalCbzSource
import com.moonreader.app.data.source.MangaDexSource
import com.moonreader.app.data.source.SourceManager
import com.moonreader.app.data.tracker.AniListTracker
import com.moonreader.app.data.tracker.KitsuTracker
import com.moonreader.app.data.tracker.MyAnimeListTracker
import com.moonreader.app.data.tracker.TrackerManager
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

class AppContainer(app: Application) {
    val preferencesStore = PreferencesStore(app)
    val database = MoonDatabase.build(app)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val orig = chain.request()
            val builder = orig.newBuilder()
                .header("User-Agent", "MoonReader/1.0 (Android)")

            if (orig.header("Authorization") == null) {
                val reqHost = orig.url.host
                val matching = preferencesStore.state.value.opdsServers.firstOrNull { srv ->
                    srv.username.isNotBlank() &&
                        runCatching { URI(srv.baseUrl).host == reqHost }.getOrDefault(false)
                }
                if (matching != null) {
                    builder.header("Authorization", Credentials.basic(matching.username, matching.password))
                }
            }

            chain.proceed(builder.build())
        }
        .build()

    val mangaDexSource = MangaDexSource(
        client = okHttpClient,
        preferIndonesianProvider = { preferencesStore.state.value.preferIndonesianLanguage }
    )
    val localCbzSource = LocalCbzSource(app)
    val demoCatalogSource = DemoCatalogSource()

    val sourceManager = SourceManager(
        context = app,
        okHttpClient = okHttpClient,
        mangaDexSource = mangaDexSource,
        localCbzSource = localCbzSource,
        demoCatalogSource = demoCatalogSource,
        preferencesStore = preferencesStore
    )

    val chapterDownloader = ChapterDownloader(
        context = app,
        okHttpClient = okHttpClient,
        sourceManager = sourceManager,
        chapterDao = database.chapterDao()
    )

    val trackerManager = TrackerManager(
        aniList = AniListTracker(okHttpClient, preferencesStore),
        myAnimeList = MyAnimeListTracker(okHttpClient, preferencesStore),
        kitsu = KitsuTracker(okHttpClient, preferencesStore)
    )

    val repository = MangaRepository(
        context = app,
        database = database,
        sourceManager = sourceManager,
        downloader = chapterDownloader,
        trackerManager = trackerManager
    )

    val appUpdater = AppUpdater(
        context = app,
        okHttpClient = okHttpClient
    )
}

class MoonReaderApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(container.okHttpClient)
            .components {
                add(DemoPageFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
