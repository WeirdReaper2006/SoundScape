package com.example.data.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.util.AppLogger
import com.example.util.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "LrcLibClient"

/** Thrown when LRCLIB couldn't be reached at all - distinct from a confirmed "no lyrics" 404. */
class LyricsNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when there's no network connectivity at all, so LRCLIB wasn't even attempted. */
class LyricsOfflineException(message: String) : Exception(message)

/**
 * Minimal client for the free, keyless LRCLIB.net API. Uses [HttpURLConnection] + [JSONObject]
 * (both bundled in the Android SDK) rather than pulling in OkHttp/Retrofit for a single simple
 * GET endpoint - consistent with this project's deliberate avoidance of heavy dependencies. The
 * trade-off is no connection pooling/retry sophistication, which is fine since LRCLIB calls are
 * low-frequency, one-shot, and off the playback-critical path.
 */
class LrcLibClient {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * @throws LyricsOfflineException if there's no active network connection.
     * @throws LyricsNetworkException if LRCLIB couldn't be reached or returned an unexpected
     * response - the caller must not cache a negative result for this case (it should retry).
     */
    suspend fun fetch(
        context: Context,
        artist: String,
        title: String,
        album: String?,
        durationSec: Int?
    ): LyricsResult = withContext(Dispatchers.IO) {
        if (!isOnline(context)) {
            throw LyricsOfflineException("No network connection available for LRCLIB lookup")
        }

        val urlBuilder = StringBuilder("https://lrclib.net/api/get?")
            .append("artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            .append("&track_name=").append(URLEncoder.encode(title, "UTF-8"))
        if (!album.isNullOrBlank()) {
            urlBuilder.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"))
        }
        if (durationSec != null && durationSec > 0) {
            urlBuilder.append("&duration=").append(durationSec)
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlBuilder.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseResponse(body)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> LyricsResult.NotFound
                else -> throw LyricsNetworkException("LRCLIB returned unexpected HTTP status $code")
            }
        } catch (e: LyricsNetworkException) {
            throw e
        } catch (e: IOException) {
            throw LyricsNetworkException("Failed to reach LRCLIB", e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Unexpected error parsing LRCLIB response", e)
            throw LyricsNetworkException("Failed to parse LRCLIB response", e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(body: String): LyricsResult {
        val json = JSONObject(body)
        val syncedLyrics = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
        val plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

        if (syncedLyrics != null) {
            val sanitized = InputValidator.sanitizeUntrustedLyricsText(syncedLyrics)
            val lines = LrcParser.parse(sanitized)
            if (lines.isNotEmpty()) {
                return LyricsResult.Synced(lines, LyricsSource.LRCLIB)
            }
        }
        if (plainLyrics != null) {
            return LyricsResult.Plain(InputValidator.sanitizeUntrustedLyricsText(plainLyrics), LyricsSource.LRCLIB)
        }
        return LyricsResult.NotFound
    }
}
