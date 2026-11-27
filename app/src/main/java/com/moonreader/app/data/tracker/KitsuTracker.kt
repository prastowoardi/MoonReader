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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object KitsuParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSearchResults(rawJson: String): List<TrackSearchResult> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.mapNotNull { elem ->
            val obj = elem as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val attrs = obj["attributes"] as? JsonObject ?: return@mapNotNull null
            val title = attrs["canonicalTitle"]?.jsonPrimitive?.contentOrNull
                ?: (attrs["titles"] as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val poster = attrs["posterImage"] as? JsonObject
            val cover = poster?.get("medium")?.jsonPrimitive?.contentOrNull
                ?: poster?.get("small")?.jsonPrimitive?.contentOrNull.orEmpty()
            val chapters = attrs["chapterCount"]?.jsonPrimitive?.intOrNull ?: 0
            val status = attrs["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val slug = attrs["slug"]?.jsonPrimitive?.contentOrNull ?: id.toString()

            TrackSearchResult(
                trackerId = KitsuTracker.TRACKER_ID,
                remoteId = id,
                title = title,
                coverUrl = cover,
                totalChapters = chapters,
                publishingStatus = status,
                remoteUrl = "https://kitsu.io/manga/$slug"
            )
        }
    }

    fun parseOAuthToken(rawJson: String): Pair<String, String> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Login Kitsu gagal: token tidak ditemukan")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return access to refresh
    }

    fun parseSelfUser(rawJson: String): Pair<String, String> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        val first = data.firstOrNull() as? JsonObject
            ?: throw IOException("Profil Kitsu tidak ditemukan")
        val id = first["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = (first["attributes"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            ?: "Kitsu User"
        return id to name
    }

    fun parseLibraryEntry(rawJson: String, isCollection: Boolean, fallback: TrackRecord): TrackRecord {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val entryObj = if (isCollection) {
            (root["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return fallback
        } else {
            root["data"] as? JsonObject ?: return fallback
        }
        val entryId = entryObj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val attrs = entryObj["attributes"] as? JsonObject ?: return fallback
        val status = attrs["status"]?.jsonPrimitive?.contentOrNull
        val progress = attrs["progress"]?.jsonPrimitive?.intOrNull?.toFloat() ?: fallback.lastChapterRead
        val ratingTwenty = attrs["ratingTwenty"]?.jsonPrimitive?.intOrNull ?: 0
        val score = if (ratingTwenty > 0) ratingTwenty / 2f else fallback.score

        return fallback.copy(
            libraryEntryId = entryId,
            status = fromKitsuStatus(status),
            lastChapterRead = progress,
            score = score,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toKitsuStatus(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "current"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_READ -> "planned"
    }

    fun fromKitsuStatus(raw: String?): TrackStatus = when (raw?.lowercase()) {
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.ON_HOLD
        "dropped" -> TrackStatus.DROPPED
        "planned" -> TrackStatus.PLAN_TO_READ
        else -> TrackStatus.READING
    }
}

class KitsuTracker(
    private val client: OkHttpClient,
    private val prefs: PreferencesStore
) : TrackerService {

    companion object {
        const val TRACKER_ID = "kitsu"
        private const val EDGE_API = "https://kitsu.io/api/edge"
        private const val OAUTH_TOKEN = "https://kitsu.io/api/oauth/token"
        private val VND_API = "application/vnd.api+json".toMediaType()
    }

    override val id: String = TRACKER_ID
    override val name: String = "Kitsu"
    override val requiresClientIdForLogin: Boolean = false
    override val supportsPasswordLogin: Boolean = true

    override fun getAccountInfo(): TrackerAccountInfo {
        val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
        return TrackerAccountInfo(
            id = TRACKER_ID,
            name = name,
            isLoggedIn = !creds?.accessToken.isNullOrBlank(),
            username = creds?.username.orEmpty(),
            requiresClientIdForLogin = false,
            supportsPasswordLogin = true
        )
    }

    suspend fun login(usernameOrEmail: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder()
                    .add("grant_type", "password")
                    .add("username", usernameOrEmail.trim())
                    .add("password", password)
                    .build()

                val tokenRaw = executeRequest(
                    Request.Builder().url(OAUTH_TOKEN).post(form).build()
                )
                val (access, refresh) = KitsuParser.parseOAuthToken(tokenRaw)

                val meRaw = executeRequest(
                    Request.Builder()
                        .url("$EDGE_API/users?filter[self]=true")
                        .header("Accept", "application/vnd.api+json")
                        .header("Authorization", "Bearer $access")
                        .get()
                        .build()
                )
                val (userId, displayName) = KitsuParser.parseSelfUser(meRaw)
                prefs.saveTrackerCredentials(
                    TrackerCredentials(
                        trackerId = TRACKER_ID,
                        accessToken = access,
                        refreshToken = refresh,
                        username = displayName,
                        userId = userId
                    )
                )
                displayName
            }
        }

    override suspend fun search(query: String): Result<List<TrackSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$EDGE_API/manga".toHttpUrl().newBuilder()
                    .addQueryParameter("filter[text]", query.trim())
                    .addQueryParameter("page[limit]", "15")
                    .build()
                val raw = executeRequest(
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/vnd.api+json")
                        .get()
                        .build()
                )
                KitsuParser.parseSearchResults(raw)
            }
        }

    override suspend fun bind(mangaId: String, candidate: TrackSearchResult): Result<TrackRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
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
                val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
                if (creds == null || creds.accessToken.isBlank() || creds.userId.isBlank()) {
                    return@runCatching base
                }

                val url = "$EDGE_API/library-entries".toHttpUrl().newBuilder()
                    .addQueryParameter("filter[userId]", creds.userId)
                    .addQueryParameter("filter[mangaId]", candidate.remoteId.toString())
                    .build()

                val raw = runCatching {
                    executeRequest(
                        Request.Builder()
                            .url(url)
                            .header("Accept", "application/vnd.api+json")
                            .header("Authorization", "Bearer ${creds.accessToken}")
                            .get()
                            .build()
                    )
                }.getOrNull() ?: return@runCatching base

                KitsuParser.parseLibraryEntry(raw, isCollection = true, fallback = base)
            }
        }

    override suspend fun update(track: TrackRecord): Result<TrackRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
                require(creds != null && creds.accessToken.isNotBlank() && creds.userId.isNotBlank()) {
                    "Login ke Kitsu terlebih dahulu untuk sinkronisasi"
                }

                val attrs = buildJsonObject {
                    put("status", KitsuParser.toKitsuStatus(track.status))
                    put("progress", track.lastChapterRead.toInt().coerceAtLeast(0))
                    if (track.score > 0f) {
                        put("ratingTwenty", (track.score * 2f).toInt().coerceIn(2, 20))
                    }
                }

                if (track.libraryEntryId.isNotBlank()) {
                    val body = buildJsonObject {
                        put("data", buildJsonObject {
                            put("id", track.libraryEntryId)
                            put("type", "libraryEntries")
                            put("attributes", attrs)
                        })
                    }.toString()
                    val req = Request.Builder()
                        .url("$EDGE_API/library-entries/${track.libraryEntryId}")
                        .header("Accept", "application/vnd.api+json")
                        .header("Authorization", "Bearer ${creds.accessToken}")
                        .patch(body.toRequestBody(VND_API))
                        .build()
                    val raw = executeRequest(req)
                    KitsuParser.parseLibraryEntry(raw, isCollection = false, fallback = track)
                } else {
                    val body = buildJsonObject {
                        put("data", buildJsonObject {
                            put("type", "libraryEntries")
                            put("attributes", attrs)
                            put("relationships", buildJsonObject {
                                put("user", buildJsonObject {
                                    put("data", buildJsonObject {
                                        put("type", "users")
                                        put("id", creds.userId)
                                    })
                                })
                                put("manga", buildJsonObject {
                                    put("data", buildJsonObject {
                                        put("type", "manga")
                                        put("id", track.remoteId.toString())
                                    })
                                })
                            })
                        })
                    }.toString()
                    val req = Request.Builder()
                        .url("$EDGE_API/library-entries")
                        .header("Accept", "application/vnd.api+json")
                        .header("Authorization", "Bearer ${creds.accessToken}")
                        .post(body.toRequestBody(VND_API))
                        .build()
                    val raw = executeRequest(req)
                    KitsuParser.parseLibraryEntry(raw, isCollection = false, fallback = track)
                }
            }
        }

    override fun logout() {
        prefs.clearTrackerSession(TRACKER_ID)
    }

    private fun executeRequest(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Kitsu HTTP ${resp.code}: ${text.take(120)}")
            }
            return text
        }
    }
}
