package com.moonreader.app.data.tracker

import com.moonreader.app.data.local.PreferencesStore
import com.moonreader.app.data.local.TrackerCredentials
import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackSearchResult
import com.moonreader.app.domain.model.TrackStatus
import com.moonreader.app.domain.model.TrackerAccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object AniListParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSearchResults(rawJson: String): List<TrackSearchResult> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val mediaArr = root["data"]?.jsonObject
            ?.get("Page")?.jsonObject
            ?.get("media")?.jsonArray ?: JsonArray(emptyList())

        return mediaArr.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val titleObj = obj["title"] as? JsonObject
            val title = titleObj?.get("userPreferred")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("english")?.jsonPrimitive?.contentOrNull
                ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null

            val coverUrl = (obj["coverImage"] as? JsonObject)
                ?.get("large")?.jsonPrimitive?.contentOrNull.orEmpty()
            val chapters = obj["chapters"]?.jsonPrimitive?.intOrNull ?: 0
            val status = obj["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val siteUrl = obj["siteUrl"]?.jsonPrimitive?.contentOrNull
                ?: "https://anilist.co/manga/$id"

            TrackSearchResult(
                trackerId = AniListTracker.TRACKER_ID,
                remoteId = id,
                title = title,
                coverUrl = coverUrl,
                totalChapters = chapters,
                publishingStatus = status,
                remoteUrl = siteUrl
            )
        }
    }

    fun parseViewer(rawJson: String): Pair<String, String> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val viewer = root["data"]?.jsonObject?.get("Viewer") as? JsonObject
            ?: throw IOException("Token AniList tidak valid")
        val id = viewer["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = viewer["name"]?.jsonPrimitive?.contentOrNull ?: "AniList User"
        return id to name
    }

    fun parseMediaListEntry(
        rawJson: String,
        fieldName: String,
        fallback: TrackRecord
    ): TrackRecord {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val entry = root["data"]?.jsonObject?.get(fieldName)
        if (entry == null || entry is JsonNull || entry !is JsonObject) {
            return fallback
        }
        val entryId = entry["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val statusStr = entry["status"]?.jsonPrimitive?.contentOrNull
        val progress = entry["progress"]?.jsonPrimitive?.intOrNull?.toFloat() ?: fallback.lastChapterRead
        val score = entry["score"]?.jsonPrimitive?.floatOrNull ?: fallback.score
        return fallback.copy(
            libraryEntryId = entryId,
            status = fromAniListStatus(statusStr),
            lastChapterRead = progress,
            score = score,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toAniListStatus(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "CURRENT"
        TrackStatus.COMPLETED -> "COMPLETED"
        TrackStatus.ON_HOLD -> "PAUSED"
        TrackStatus.DROPPED -> "DROPPED"
        TrackStatus.PLAN_TO_READ -> "PLANNING"
    }

    fun fromAniListStatus(raw: String?): TrackStatus = when (raw?.uppercase()) {
        "COMPLETED" -> TrackStatus.COMPLETED
        "PAUSED" -> TrackStatus.ON_HOLD
        "DROPPED" -> TrackStatus.DROPPED
        "PLANNING" -> TrackStatus.PLAN_TO_READ
        else -> TrackStatus.READING
    }
}

class AniListTracker(
    private val client: OkHttpClient,
    private val prefs: PreferencesStore
) : TrackerService {

    companion object {
        const val TRACKER_ID = "anilist"
        private const val GRAPHQL_URL = "https://graphql.anilist.co"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    override val id: String = TRACKER_ID
    override val name: String = "AniList"
    override val requiresClientIdForLogin: Boolean = true
    override val supportsPasswordLogin: Boolean = false

    override fun getAccountInfo(): TrackerAccountInfo {
        val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
        return TrackerAccountInfo(
            id = TRACKER_ID,
            name = name,
            isLoggedIn = !creds?.accessToken.isNullOrBlank(),
            username = creds?.username.orEmpty(),
            clientId = creds?.clientId.orEmpty(),
            requiresClientIdForLogin = true,
            supportsPasswordLogin = false
        )
    }

    fun buildAuthorizeUrl(clientId: String): String {
        return "https://anilist.co/api/v2/oauth/authorize?client_id=${clientId.trim()}&response_type=token"
    }

    suspend fun loginWithToken(accessToken: String, clientId: String = ""): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken.trim()
                require(token.isNotBlank()) { "Access token AniList kosong" }

                val query = "query { Viewer { id name } }"
                val payload = buildJsonObject { put("query", query) }.toString()
                val raw = executeGraphQl(payload, token)
                val (userId, username) = AniListParser.parseViewer(raw)

                val existingClientId = prefs.state.value.trackerCredentials[TRACKER_ID]?.clientId.orEmpty()
                prefs.saveTrackerCredentials(
                    TrackerCredentials(
                        trackerId = TRACKER_ID,
                        accessToken = token,
                        username = username,
                        userId = userId,
                        clientId = clientId.trim().ifBlank { existingClientId }
                    )
                )
                username
            }
        }

    override suspend fun search(query: String): Result<List<TrackSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val gql = """
                    query (${'$'}search: String) {
                      Page(page: 1, perPage: 15) {
                        media(search: ${'$'}search, type: MANGA) {
                          id
                          title { userPreferred english romaji }
                          coverImage { large }
                          chapters
                          status
                          siteUrl
                        }
                      }
                    }
                """.trimIndent()
                val body = buildJsonObject {
                    put("query", gql)
                    put("variables", buildJsonObject { put("search", query.trim()) })
                }.toString()
                val token = prefs.state.value.trackerCredentials[TRACKER_ID]?.accessToken
                val raw = executeGraphQl(body, token)
                AniListParser.parseSearchResults(raw)
            }
        }

    override suspend fun bind(mangaId: String, candidate: TrackSearchResult): Result<TrackRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
                val base = TrackRecord(
                    id = "$mangaId:$TRACKER_ID",
                    mangaId = mangaId,
                    trackerId = TRACKER_ID,
                    remoteId = candidate.remoteId,
                    title = candidate.title,
                    coverUrl = candidate.coverUrl,
                    status = TrackStatus.READING,
                    lastChapterRead = 0f,
                    totalChapters = candidate.totalChapters,
                    score = 0f,
                    remoteUrl = candidate.remoteUrl,
                    updatedAt = System.currentTimeMillis()
                )
                if (creds == null || creds.accessToken.isBlank() || creds.userId.isBlank()) {
                    return@runCatching base
                }

                val gql = """
                    query (${'$'}mediaId: Int, ${'$'}userId: Int) {
                      MediaList(mediaId: ${'$'}mediaId, userId: ${'$'}userId) {
                        id
                        status
                        progress
                        score(format: POINT_10_DECIMAL)
                      }
                    }
                """.trimIndent()
                val body = buildJsonObject {
                    put("query", gql)
                    put("variables", buildJsonObject {
                        put("mediaId", candidate.remoteId.toInt())
                        put("userId", creds.userId.toIntOrNull() ?: 0)
                    })
                }.toString()

                val raw = runCatching { executeGraphQl(body, creds.accessToken) }.getOrNull()
                if (raw != null) {
                    AniListParser.parseMediaListEntry(raw, "MediaList", base)
                } else {
                    base
                }
            }
        }

    override suspend fun update(track: TrackRecord): Result<TrackRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = prefs.state.value.trackerCredentials[TRACKER_ID]?.accessToken
                require(!token.isNullOrBlank()) { "Login ke AniList terlebih dahulu untuk sinkronisasi" }

                val gql = """
                    mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int, ${'$'}score: Float) {
                      SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, progress: ${'$'}progress, score: ${'$'}score) {
                        id
                        status
                        progress
                        score(format: POINT_10_DECIMAL)
                      }
                    }
                """.trimIndent()

                val variables = buildJsonObject {
                    put("mediaId", track.remoteId.toInt())
                    put("status", AniListParser.toAniListStatus(track.status))
                    put("progress", track.lastChapterRead.toInt().coerceAtLeast(0))
                    if (track.score > 0f) {
                        put("score", track.score.toDouble())
                    }
                }
                val body = buildJsonObject {
                    put("query", gql)
                    put("variables", variables)
                }.toString()

                val raw = executeGraphQl(body, token)
                AniListParser.parseMediaListEntry(raw, "SaveMediaListEntry", track)
            }
        }

    override fun logout() {
        prefs.clearTrackerSession(TRACKER_ID)
    }

    private fun executeGraphQl(bodyJson: String, accessToken: String?): String {
        val builder = Request.Builder()
            .url(GRAPHQL_URL)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("AniList HTTP ${resp.code}: ${text.take(120)}")
            }
            return text
        }
    }
}
