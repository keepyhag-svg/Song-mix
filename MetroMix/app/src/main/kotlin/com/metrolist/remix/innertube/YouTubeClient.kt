package com.metrolist.remix.innertube

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Modeled after Metrolist's `com.metrolist.innertube.models.YouTubeClient`.
 * Uses multi-client InnerTube contexts to scrape YouTube Music free streams
 * without API keys, login requirements, or signatureCipher 403 playback errors.
 */
@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val embeddedPlayer: Boolean = false
) {
    companion object {
        /**
         * Used for YouTube Music search (/youtubei/v1/search) & metadata browsing.
         */
        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260115.01.00",
            clientId = "67",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
        )

        /**
         * Primary stream extraction client in Metrolist.
         * Returns direct `adaptiveFormats` Opus/AAC stream URLs without requiring JS signature decryption or poToken.
         */
        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = 32,
            buildId = "SQ3A.220605.009.A1",
            cronetVersion = "132.0.6808.3"
        )

        /**
         * Secondary direct stream client fallback.
         */
        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "20.10.4",
            clientId = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2"
        )

        /**
         * Tertiary Android client fallback for tracks restricted on VR/iOS.
         */
        val ANDROID_NO_SDK = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "20.10.38",
            clientId = "3",
            userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34
        )

        val STREAM_FALLBACK_ORDER = listOf(ANDROID_VR, IOS, ANDROID_NO_SDK)
    }
}

@Serializable
data class ScrapedSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Int,
    val thumbnailUrl: String
)

data class ResolvedAudioStream(
    val videoId: String,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val itag: Int,
    val contentLength: Long,
    val durationMs: Long,
    val loudnessDb: Float,
    val clientUsed: YouTubeClient,
    val expiresAtEpochMs: Long
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs >= (expiresAtEpochMs - 120_000L) // Refresh 2 minutes before expiry
    }
}

/**
 * Scrapes YouTube Music free client endpoints directly (`/youtubei/v1/search` and `/youtubei/v1/player`).
 * Implements automatic multi-client fallback and stream URL caching to guarantee zero playback errors.
 */
class InnerTubeScraper(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val streamCache = ConcurrentHashMap<String, ResolvedAudioStream>()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Searches YouTube Music free catalog for songs matching [query].
     * Uses WEB_REMIX search filter `EgWKAQIIAWoKEAkQBRAKEAMQBA==` (Songs filter).
     */
    suspend fun searchSongs(query: String, maxResults: Int = 20): Result<List<ScrapedSong>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (query.isBlank()) return@runCatching emptyList()

                val payload = buildJsonObject {
                    put("context", buildContextJson(YouTubeClient.WEB_REMIX))
                    put("query", query.trim())
                    put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==") // Strict YTM Songs filter
                }

                val request = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                    .header("User-Agent", YouTubeClient.WEB_REMIX.userAgent)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .header("X-YouTube-Client-Name", YouTubeClient.WEB_REMIX.clientId)
                    .header("X-YouTube-Client-Version", YouTubeClient.WEB_REMIX.clientVersion)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("InnerTube search HTTP ${response.code}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty search body")
                    val root = json.parseToJsonElement(bodyString).jsonObject
                    parseSearchResults(root, maxResults)
                }
            }
        }

    /**
     * Resolves a direct, playable audio stream URL for [videoId].
     * Cycles through ANDROID_VR -> IOS -> ANDROID_NO_SDK to avoid signatureCipher locks and HTTP 403 errors.
     */
    suspend fun resolveStream(videoId: String, forceRefresh: Boolean = false): Result<ResolvedAudioStream> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!forceRefresh) {
                    streamCache[videoId]?.let { cached ->
                        if (!cached.isExpired()) return@runCatching cached
                    }
                }

                var lastError: Throwable? = null
                for (client in YouTubeClient.STREAM_FALLBACK_ORDER) {
                    try {
                        val stream = requestPlayerStream(videoId, client)
                        if (stream != null) {
                            streamCache[videoId] = stream
                            return@runCatching stream
                        }
                    } catch (e: Throwable) {
                        lastError = e
                    }
                }
                throw IOException(
                    "Unable to resolve playable audio stream for $videoId across fallback clients",
                    lastError
                )
            }
        }

    private fun requestPlayerStream(videoId: String, client: YouTubeClient): ResolvedAudioStream? {
        val payload = buildJsonObject {
            put("context", buildContextJson(client))
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            if (client.clientName == "ANDROID") {
                put("params", "CgIIAQ==")
            }
        }

        val endpoint = if (client.clientName == "WEB_REMIX") {
            "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"
        } else {
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val root = json.parseToJsonElement(bodyString).jsonObject

            val playability = root["playabilityStatus"]?.jsonObject
            val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
            if (status != "OK") return null

            val streamingData = root["streamingData"]?.jsonObject ?: return null
            val expiresInSeconds = streamingData["expiresInSeconds"]
                ?.jsonPrimitive?.longOrNull ?: 21_600L

            val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray ?: return null

            // Select highest-quality audio-only stream (prefer Opus itag 251, then AAC itag 140)
            val bestAudio = adaptiveFormats
                .mapNotNull { it.jsonObject }
                .filter { fmt ->
                    val mime = fmt["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                    val url = fmt["url"]?.jsonPrimitive?.contentOrNull
                    mime.startsWith("audio/") && !url.isNullOrBlank()
                }
                .maxWithOrNull(
                    compareBy<JsonObject> { fmt ->
                        val itag = fmt["itag"]?.jsonPrimitive?.intOrNull ?: 0
                        when (itag) {
                            251 -> 3 // WebM Opus ~160kbps (best quality & gapless seeking)
                            140 -> 2 // MP4 AAC ~128kbps
                            250 -> 1
                            else -> 0
                        }
                    }.thenBy { fmt ->
                        fmt["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                    }
                ) ?: return null

            val rawUrl = bestAudio["url"]?.jsonPrimitive?.contentOrNull ?: return null
            val mimeType = bestAudio["mimeType"]?.jsonPrimitive?.contentOrNull ?: "audio/webm"
            val bitrate = bestAudio["bitrate"]?.jsonPrimitive?.intOrNull ?: 128_000
            val itag = bestAudio["itag"]?.jsonPrimitive?.intOrNull ?: 251
            val contentLength = bestAudio["contentLength"]?.jsonPrimitive?.longOrNull ?: 0L
            val approxDurationMs = bestAudio["approxDurationMs"]?.jsonPrimitive?.longOrNull
                ?: root["videoDetails"]?.jsonObject?.get("lengthSeconds")?.jsonPrimitive?.longOrNull?.times(1000L)
                ?: 180_000L

            val loudnessDb = root["playerConfig"]?.jsonObject
                ?.get("audioConfig")?.jsonObject
                ?.get("loudnessDb")?.jsonPrimitive?.floatOrNull ?: 0f

            return ResolvedAudioStream(
                videoId = videoId,
                streamUrl = rawUrl,
                mimeType = mimeType.substringBefore(";").trim(),
                bitrate = bitrate,
                itag = itag,
                contentLength = contentLength,
                durationMs = approxDurationMs,
                loudnessDb = loudnessDb,
                clientUsed = client,
                expiresAtEpochMs = System.currentTimeMillis() + (expiresInSeconds * 1000L)
            )
        }
    }

    private fun buildContextJson(client: YouTubeClient): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "en")
            put("gl", "US")
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
        }
        putJsonObject("user") {
            put("lockedSafetyMode", false)
        }
    }

    private fun parseSearchResults(root: JsonObject, maxResults: Int): List<ScrapedSong> {
        val songs = mutableListOf<ScrapedSong>()
        val tabs = root["contents"]?.jsonObject
            ?.get("tabbedSearchResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray ?: return emptyList()

        for (tab in tabs) {
            val sectionList = tab.jsonObject["tabRenderer"]?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: continue

            for (section in sectionList) {
                val shelfItems = section.jsonObject["musicShelfRenderer"]?.jsonObject
                    ?.get("contents")?.jsonArray ?: continue

                for (itemEl in shelfItems) {
                    val renderer = itemEl.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject
                        ?: continue
                    parseSongFromItemRenderer(renderer)?.let {
                        songs.add(it)
                        if (songs.size >= maxResults) return songs
                    }
                }
            }
        }
        return songs
    }

    private fun parseSongFromItemRenderer(renderer: JsonObject): ScrapedSong? {
        val videoId = renderer["playlistItemData"]?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: return null

        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        if (flexColumns.isEmpty()) return null

        val titleRuns = flexColumns[0].jsonObject
            .get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray
        val title = titleRuns?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

        val subtitleRuns = flexColumns.getOrNull(1)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject
            ?.get("runs")?.jsonArray

        var artist = "Unknown Artist"
        var durationSec = 180
        if (subtitleRuns != null) {
            val textParts = subtitleRuns.mapNotNull {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.filter { it != " • " && it != " & " && it != ", " }

            if (textParts.isNotEmpty()) {
                artist = textParts.first()
            }
            val lastPart = textParts.lastOrNull()
            if (lastPart != null && lastPart.contains(":")) {
                durationSec = parseDurationToSeconds(lastPart)
            }
        }

        val thumbnails = renderer["thumbnail"]?.jsonObject
            ?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray
        val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

        return ScrapedSong(
            videoId = videoId,
            title = title,
            artist = artist,
            durationSec = durationSec.coerceAtLeast(45),
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun parseDurationToSeconds(raw: String): Int {
        val parts = raw.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 180
        }
    }
}
