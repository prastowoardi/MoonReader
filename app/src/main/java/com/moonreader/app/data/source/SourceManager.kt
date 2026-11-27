package com.moonreader.app.data.source

import android.content.Context
import com.moonreader.app.data.local.OpdsServerConfig
import com.moonreader.app.data.local.PreferencesStore
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.domain.source.MangaSource
import okhttp3.OkHttpClient

/**
 * Central registry for all modular comic sources (API, Local CBZ, OPDS, Demo, future extensions).
 */
class SourceManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    val mangaDexSource: MangaDexSource,
    val localCbzSource: LocalCbzSource,
    val demoCatalogSource: DemoCatalogSource,
    private val preferencesStore: PreferencesStore
) {
    private val staticSources: Map<String, MangaSource> = linkedMapOf(
        MangaDexSource.SOURCE_ID to mangaDexSource,
        LocalCbzSource.SOURCE_ID to localCbzSource,
        DemoCatalogSource.SOURCE_ID to demoCatalogSource
    )

    private val opdsCache = mutableMapOf<String, Pair<OpdsServerConfig, OpdsSource>>()

    private fun currentOpdsSources(): List<OpdsSource> {
        val configs = preferencesStore.state.value.opdsServers
        val activeIds = configs.map { it.id }.toSet()
        opdsCache.keys.retainAll(activeIds)
        return configs.map { cfg ->
            val cached = opdsCache[cfg.id]
            if (cached != null && cached.first == cfg) {
                cached.second
            } else {
                val created = OpdsSource(context, okHttpClient, cfg)
                opdsCache[cfg.id] = cfg to created
                created
            }
        }
    }

    private fun allSources(): List<MangaSource> =
        staticSources.values.toList() + currentOpdsSources()

    fun getSource(sourceId: String): MangaSource? {
        return staticSources[sourceId] ?: currentOpdsSources().firstOrNull { it.info.id == sourceId }
    }

    fun getAllSourceInfos(): List<SourceInfo> {
        val disabled = preferencesStore.state.value.disabledSourceIds
        return allSources().map { source ->
            source.info.copy(isEnabled = source.info.id !in disabled)
        }
    }

    fun getEnabledSources(): List<MangaSource> {
        val disabled = preferencesStore.state.value.disabledSourceIds
        return allSources().filter { it.info.id !in disabled }
    }
}
