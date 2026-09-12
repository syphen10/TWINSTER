package com.twinster.app.data

import com.twinster.app.BuildConfig
import com.twinster.app.domain.ArtistNameNormalizer
import com.twinster.app.util.SecretObfuscator
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Third and last-resort genre-lookup tier for Local Library artists with NO genre signal at all
 * after file-tag and on-device YAMNet content-analysis detection (see LocalLibraryScanner). Genius
 * (GeniusObscurityService) has no genre field whatsoever in its API responses (confirmed by direct
 * testing) — TheAudioDb is a free, commercial-use-friendly music metadata database with real
 * `strGenre`/`strStyle` fields per artist, and (unlike MusicBrainz) explicitly permits commercial use
 * of its API, which matters since this app is ad-monetized.
 *
 * Uses TheAudioDb's own documented shared "123" test key by default (see
 * BuildConfig.THEAUDIODB_API_KEY_OBF), so — like MusicBrainz would have been — there's no
 * "isConfigured" gate; this is always available. The shared key's documented rate limit is 30
 * requests/minute, so lookups here run strictly one artist at a time with a delay comfortably under
 * that between each call, never concurrently. Best-effort throughout: any failure (timeout, network
 * error, no match, rate-limit response, malformed JSON) just means that artist contributes no
 * TheAudioDb signal; this never throws.
 */
object TheAudioDbGenreService {

    // Same class of bug as the MediaMetadataRetriever/ContentResolver hangs fixed elsewhere in the
    // scan pipeline — a slow/stuck TheAudioDb response must not hang the whole scan.
    private const val REQUEST_TIMEOUT_MS = 5_000L

    // The shared/free key's documented limit is 30 requests/minute (one every 2s); 2100ms keeps a
    // safe margin under that for sequential, non-concurrent calls.
    private const val RATE_LIMIT_DELAY_MS = 2_100L

    private val apiKey: String by lazy { SecretObfuscator.decode(BuildConfig.THEAUDIODB_API_KEY_OBF) }

    /** artistName -> genre string, for at most [maxArtists] distinct names — a hard cap given the
     *  30 req/min rate limit makes every lookup cost real wall-clock time. Runs strictly one artist
     *  at a time; [onProgress], if given, fires after each attempt (found or not) with
     *  (completed, total). */
    suspend fun genresForArtists(
        artistNames: List<String>,
        maxArtists: Int = 20,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Map<String, String> {
        val names = artistNames.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(maxArtists)
        val results = LinkedHashMap<String, String>()
        names.forEachIndexed { index, name ->
            val genre = lookupOne(ArtistNameNormalizer.normalize(name))
            if (genre != null) results[name] = genre
            onProgress?.invoke(index + 1, names.size)
            // No need to wait after the very last lookup.
            if (index < names.size - 1) delay(RATE_LIMIT_DELAY_MS)
        }
        return results
    }

    private suspend fun lookupOne(artistName: String): String? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            try {
                val artist = NetworkModule.theAudioDbApi.searchArtist(apiKey, artistName).artists?.firstOrNull()
                artist?.strGenre?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
                    ?: artist?.strStyle?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
            } catch (e: Exception) {
                null
            }
        }
}
