package com.moonreader.app.data.local

import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.Manga
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MoonBackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val categories: List<Category>,
    val mangas: List<Manga>,
    val chapters: List<Chapter>
)

object BackupManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(
        categories: List<Category>,
        mangas: List<Manga>,
        chapters: List<Chapter>,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val payload = MoonBackupPayload(
            version = 1,
            exportedAt = exportedAt,
            categories = categories,
            mangas = mangas,
            chapters = chapters
        )
        return json.encodeToString(payload)
    }

    fun deserialize(rawJson: String): MoonBackupPayload {
        return json.decodeFromString(MoonBackupPayload.serializer(), rawJson)
    }
}
