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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.SecureRandom

object MalParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseSearchResults(rawJson: String): List<TrackSearchResult> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val data = root["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.mapNotNull { elem ->
            val node = (elem as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val title = node["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val pic = node["main_picture"] as? JsonObject
            val cover = pic?.get("large")?.jsonPrimitive?.contentOrNull
                ?: pic?.get("medium")?.jsonPrimitive?.contentOrNull.orEmpty()
            val chapters = node["num_chapters"]?.jsonPrimitive?.intOrNull ?: 0
            val status = node["status"]?.jsonPrimitive?.contentOrNull.orEmpty()

            TrackSearchResult(
                trackerId = MyAnimeListTracker.TRACKER_ID,
                remoteId = id,
                title = title,
                coverUrl = cover,
                totalChapters = chapters,
                publishingStatus = status,
                remoteUrl = "https://myanimelist.net/manga/$id"
            )
        }
    }

    fun parseTokenResponse(rawJson: String): Pair<String, String> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Token MyAnimeList tidak ditemukan")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return access to refresh
    }

    fun parseUserMe(rawJson: String): Pair<String, String> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val id = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = root["name"]?.jsonPrimitive?.contentOrNull ?: "MAL User"
        return id to name
    }

    fun parseMyListStatus(rawJson: String, fallback: TrackRecord, nestedInManga: Boolean): TrackRecord {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val statusObj = if (nestedInManga) {
            root["my_list_status"] as? JsonObject ?: return fallback
        } else {
            root
        }
        val status = statusObj["status"]?.jsonPrimitive?.contentOrNull
        val read = statusObj["num_chapters_read"]?.jsonPrimitive?.intOrNull?.toFloat()
            ?: fallback.lastChapterRead
        val score = statusObj["score"]?.jsonPrimitive?.floatOrNull ?: fallback.score
        return fallback.copy(
            status = fromMalStatus(status),
            lastChapterRead = read,
            score = score,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toMalStatus(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "reading"
        TrackStatus.COMPLETED -> "completed"
        TrackStatus.ON_HOLD -> "on_hold"
        TrackStatus.DROPPED -> "dropped"
        TrackStatus.PLAN_TO_READ -> "plan_to_read"
    }

    fun fromMalStatus(raw: String?): TrackStatus = when (raw?.lowercase()) {
        "completed" -> TrackStatus.COMPLETED
        "on_hold" -> TrackStatus.ON_HOLD
        "dropped" -> TrackStatus.DROPPED
        "plan_to_read" -> TrackStatus.PLAN_TO_READ
        else -> TrackStatus.READING
    }
}

class MyAnimeListTracker(
    private val client: OkHttpClient,
    private val prefs: PreferencesStore
) : TrackerService {

    companion object {
        const val TRACKER_ID = "mal"
        private const val BASE_API = "https://api.myanimelist.net/v2"
        private const val OAUTH_AUTHORIZE = "https://myanimelist.net/v1/oauth2/authorize"
        private const val OAUTH_TOKEN = "https://myanimelist.net/v1/oauth2/token"
        const val REDIRECT_URI = "moonreader://mal-auth"
        private const val PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    }

    override val id: String = TRACKER_ID
    override val name: String = "MyAnimeList"
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

    fun prepareAuthorizeUrl(clientId: String): String {
        val cleanId = clientId.trim()
        val verifier = generatePkceVerifier()
        prefs.saveMalPkceVerifier(verifier)

        val existing = prefs.state.value.trackerCredentials[TRACKER_ID]
        prefs.saveTrackerCredentials(
            (existing ?: TrackerCredentials(trackerId = TRACKER_ID)).copy(clientId = cleanId)
        )

        return OAUTH_AUTHORIZE.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", cleanId)
            .addQueryParameter("code_challenge", verifier)
            .addQueryParameter("code_challenge_method", "plain")
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .build()
            .toString()
    }

    suspend fun exchangeAuthCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clientId = prefs.state.value.trackerCredentials[TRACKER_ID]?.clientId.orEmpty()
            require(clientId.isNotBlank()) { "Client ID MyAnimeList belum diisi" }
            val verifier = prefs.consumeMalPkceVerifier()
            require(verifier.isNotBlank()) { "PKCE verifier kadaluwarsa, ulangi login" }

            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code.trim())
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val tokenJson = executeRequest(
                Request.Builder().url(OAUTH_TOKEN).post(form).build()
            )
            val (access, refresh) = MalParser.parseTokenResponse(tokenJson)
            fetchAndSaveUser(access, refresh, clientId)
        }
    }

    suspend fun loginWithToken(accessToken: String, clientId: String = ""): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existingClientId = prefs.state.value.trackerCredentials[TRACKER_ID]?.clientId.orEmpty()
                fetchAndSaveUser(accessToken.trim(), "", clientId.trim().ifBlank { existingClientId })
            }
        }

    override suspend fun search(query: String): Result<List<TrackSearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
                val hasAuth = !creds?.accessToken.isNullOrBlank() || !creds?.clientId.isNullOrBlank()
                require(hasAuth) { "Isi Client ID atau login MyAnimeList terlebih dahulu" }

                val url = "$BASE_API/manga".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query.trim())
                    .addQueryParameter("limit", "15")
                    .addQueryParameter("fields", "id,title,main_picture,num_chapters,status")
                    .build()

                val reqBuilder = Request.Builder().url(url).get()
                if (!creds?.accessToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${creds!!.accessToken}")
                } else {
                    reqBuilder.header("X-MAL-CLIENT-ID", creds!!.clientId)
                }
                val raw = executeRequest(reqBuilder.build())
                MalParser.parseSearchResults(raw)
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
                val token = prefs.state.value.trackerCredentials[TRACKER_ID]?.accessToken
                if (token.isNullOrBlank()) return@runCatching base

                val url = "$BASE_API/manga/${candidate.remoteId}?fields=id,title,num_chapters,my_list_status"
                val raw = runCatching {
                    executeAuthorizedGet(url)
                }.getOrNull() ?: return@runCatching base

                MalParser.parseMyListStatus(raw, base, nestedInManga = true)
            }
        }

    override suspend fun update(track: TrackRecord): Result<TrackRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = prefs.state.value.trackerCredentials[TRACKER_ID]?.accessToken
                require(!token.isNullOrBlank()) { "Login ke MyAnimeList terlebih dahulu" }

                val formBuilder = FormBody.Builder()
                    .add("status", MalParser.toMalStatus(track.status))
                    .add("num_chapters_read", track.lastChapterRead.toInt().coerceAtLeast(0).toString())
                if (track.score > 0f) {
                    formBuilder.add("score", track.score.toInt().coerceIn(0, 10).toString())
                }
                val url = "$BASE_API/manga/${track.remoteId}/my_list_status"
                val raw = executeAuthorizedPatch(url, formBuilder.build())
                MalParser.parseMyListStatus(raw, track, nestedInManga = false)
            }
        }

    override fun logout() {
        prefs.clearTrackerSession(TRACKER_ID)
    }

    private fun fetchAndSaveUser(accessToken: String, refreshToken: String, clientId: String): String {
        val meJson = executeRequest(
            Request.Builder()
                .url("$BASE_API/users/@me")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
        )
        val (userId, username) = MalParser.parseUserMe(meJson)
        prefs.saveTrackerCredentials(
            TrackerCredentials(
                trackerId = TRACKER_ID,
                accessToken = accessToken,
                refreshToken = refreshToken,
                username = username,
                userId = userId,
                clientId = clientId
            )
        )
        return username
    }

    private fun executeAuthorizedGet(url: String): String {
        val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
            ?: throw IOException("Belum login MyAnimeList")
        return try {
            executeRequest(
                Request.Builder().url(url).header("Authorization", "Bearer ${creds.accessToken}").get().build()
            )
        } catch (e: IOException) {
            if (creds.refreshToken.isNotBlank() && creds.clientId.isNotBlank()) {
                val refreshed = refreshAccessToken(creds)
                executeRequest(
                    Request.Builder().url(url).header("Authorization", "Bearer ${refreshed.accessToken}").get().build()
                )
            } else throw e
        }
    }

    private fun executeAuthorizedPatch(url: String, form: FormBody): String {
        val creds = prefs.state.value.trackerCredentials[TRACKER_ID]
            ?: throw IOException("Belum login MyAnimeList")
        return try {
            executeRequest(
                Request.Builder().url(url).header("Authorization", "Bearer ${creds.accessToken}").patch(form).build()
            )
        } catch (e: IOException) {
            if (creds.refreshToken.isNotBlank() && creds.clientId.isNotBlank()) {
                val refreshed = refreshAccessToken(creds)
                executeRequest(
                    Request.Builder().url(url).header("Authorization", "Bearer ${refreshed.accessToken}").patch(form).build()
                )
            } else throw e
        }
    }

    private fun refreshAccessToken(creds: TrackerCredentials): TrackerCredentials {
        val form = FormBody.Builder()
            .add("client_id", creds.clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", creds.refreshToken)
            .build()
        val raw = executeRequest(Request.Builder().url(OAUTH_TOKEN).post(form).build())
        val (access, refresh) = MalParser.parseTokenResponse(raw)
        val updated = creds.copy(accessToken = access, refreshToken = refresh.ifBlank { creds.refreshToken })
        prefs.saveTrackerCredentials(updated)
        return updated
    }

    private fun executeRequest(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("MyAnimeList HTTP ${resp.code}: ${body.take(120)}")
            }
            return body
        }
    }

    private fun generatePkceVerifier(): String {
        val rnd = SecureRandom()
        return buildString(64) {
            repeat(64) {
                append(PKCE_CHARS[rnd.nextInt(PKCE_CHARS.length)])
            }
        }
    }
}
