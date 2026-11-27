package com.moonreader.app.data.local

import android.content.Context
import com.moonreader.app.domain.model.AppThemeMode
import com.moonreader.app.domain.model.LibraryDisplayMode
import com.moonreader.app.domain.model.ReaderMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OpdsServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String = "",
    val password: String = ""
)

data class AppPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultReaderMode: ReaderMode = ReaderMode.WEBTOON,
    val libraryDisplayMode: LibraryDisplayMode = LibraryDisplayMode.COMPACT_GRID,
    val gridColumns: Int = 3,
    val showUnreadBadge: Boolean = true,
    val keepScreenOnInReader: Boolean = true,
    val readerBrightnessOverride: Float? = null,
    val disabledSourceIds: Set<String> = emptySet(),
    val preferIndonesianLanguage: Boolean = true,
    val opdsServers: List<OpdsServerConfig> = emptyList(),
    val updateFeedUrl: String = PreferencesStore.DEFAULT_UPDATE_FEED_URL
)

class PreferencesStore(context: Context) {
    companion object {
        const val DEFAULT_UPDATE_FEED_URL = "https://github.com/prastowoardi/MoonReader"
    }

    private val prefs = context.getSharedPreferences("moonreader_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    private fun load(): AppPreferences {
        val theme = runCatching {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)!!)
        }.getOrDefault(AppThemeMode.SYSTEM)

        val reader = runCatching {
            ReaderMode.valueOf(prefs.getString("reader_mode", ReaderMode.WEBTOON.name)!!)
        }.getOrDefault(ReaderMode.WEBTOON)

        val display = runCatching {
            LibraryDisplayMode.valueOf(prefs.getString("display_mode", LibraryDisplayMode.COMPACT_GRID.name)!!)
        }.getOrDefault(LibraryDisplayMode.COMPACT_GRID)

        val brightness = if (prefs.contains("reader_brightness")) {
            prefs.getFloat("reader_brightness", 1f)
        } else null

        val opdsRaw = prefs.getString("opds_servers_json", null)
        val opdsList = if (!opdsRaw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<OpdsServerConfig>>(opdsRaw) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        return AppPreferences(
            themeMode = theme,
            defaultReaderMode = reader,
            libraryDisplayMode = display,
            gridColumns = prefs.getInt("grid_columns", 3).coerceIn(2, 5),
            showUnreadBadge = prefs.getBoolean("show_unread_badge", true),
            keepScreenOnInReader = prefs.getBoolean("keep_screen_on", true),
            readerBrightnessOverride = brightness,
            disabledSourceIds = prefs.getStringSet("disabled_sources", emptySet()) ?: emptySet(),
            preferIndonesianLanguage = prefs.getBoolean("prefer_id_lang", true),
            opdsServers = opdsList,
            updateFeedUrl = prefs.getString("update_feed_url", null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_UPDATE_FEED_URL
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDefaultReaderMode(mode: ReaderMode) {
        prefs.edit().putString("reader_mode", mode.name).apply()
        _state.update { it.copy(defaultReaderMode = mode) }
    }

    fun setLibraryDisplayMode(mode: LibraryDisplayMode) {
        prefs.edit().putString("display_mode", mode.name).apply()
        _state.update { it.copy(libraryDisplayMode = mode) }
    }

    fun setGridColumns(columns: Int) {
        val clamped = columns.coerceIn(2, 5)
        prefs.edit().putInt("grid_columns", clamped).apply()
        _state.update { it.copy(gridColumns = clamped) }
    }

    fun setShowUnreadBadge(show: Boolean) {
        prefs.edit().putBoolean("show_unread_badge", show).apply()
        _state.update { it.copy(showUnreadBadge = show) }
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", keepOn).apply()
        _state.update { it.copy(keepScreenOnInReader = keepOn) }
    }

    fun setReaderBrightness(value: Float?) {
        val editor = prefs.edit()
        if (value == null) {
            editor.remove("reader_brightness")
        } else {
            editor.putFloat("reader_brightness", value.coerceIn(0.1f, 1.0f))
        }
        editor.apply()
        _state.update { it.copy(readerBrightnessOverride = value?.coerceIn(0.1f, 1.0f)) }
    }

    fun toggleSourceEnabled(sourceId: String, enabled: Boolean) {
        val current = _state.value.disabledSourceIds.toMutableSet()
        if (enabled) current.remove(sourceId) else current.add(sourceId)
        prefs.edit().putStringSet("disabled_sources", current).apply()
        _state.update { it.copy(disabledSourceIds = current) }
    }

    fun setPreferIndonesianLanguage(prefer: Boolean) {
        prefs.edit().putBoolean("prefer_id_lang", prefer).apply()
        _state.update { it.copy(preferIndonesianLanguage = prefer) }
    }

    fun addOrUpdateOpdsServer(config: OpdsServerConfig) {
        val updated = _state.value.opdsServers
            .filterNot { it.id == config.id } + config
        prefs.edit().putString("opds_servers_json", json.encodeToString(updated)).apply()
        _state.update { it.copy(opdsServers = updated) }
    }

    fun removeOpdsServer(serverId: String) {
        val updated = _state.value.opdsServers.filterNot { it.id == serverId }
        prefs.edit().putString("opds_servers_json", json.encodeToString(updated)).apply()
        _state.update { it.copy(opdsServers = updated) }
    }

    fun setUpdateFeedUrl(url: String) {
        val trimmed = url.trim()
        prefs.edit().putString("update_feed_url", trimmed).apply()
        _state.update { it.copy(updateFeedUrl = trimmed) }
    }
}
