package com.twinster.app.data

import com.twinster.app.BuildConfig
import com.twinster.app.domain.ArtistNameNormalizer
import com.twinster.app.domain.ProfileTrack
import com.twinster.app.util.SecretObfuscator
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Sole "how mainstream is this artist/track on the internet" signal for Local Library obscurity
 * scoring, sourced from Genius's free search API — see TwinsterViewModel.analyzeLocalLibrary.
 * Deliberately best-effort throughout: a missing access token, a network failure, or a per-item miss
 * just means that item contributes no Genius signal — it never fails or blocks the caller.
 *
 * Every query goes through [ArtistNameNormalizer] first — channel/tag-derived artist names
 * ("PostMaloneVEVO", "sofaygoarchive") otherwise rarely match anything on Genius, even when a clean
 * name for the same artist would. Genius's search is itself fuzzy/substring-tolerant, so even a
 * partial stem left over after normalization still has a real chance of surfacing the right artist.
 */
object GeniusObscurityService {

    // Decoded once and reused rather than decoding on every call — decoding is cheap, but there's no
    // reason to redo it per-request.
    private val accessToken: String by lazy { SecretObfuscator.decode(BuildConfig.GENIUS_ACCESS_TOKEN_OBF) }

    val isConfigured: Boolean
        get() = accessToken.isNotBlank() && accessToken != "YOUR_GENIUS_ACCESS_TOKEN_HERE"

    /** artistName -> 0..100 mainstream-ness score, on the same 0..100 scale as artist popularity. Looks up
     *  at most [maxArtists] distinct names sequentially — Genius has no documented bulk-lookup
     *  endpoint, and sequential requests keep a scan from firing dozens of calls at once. */
    suspend fun mainstreamScores(artistNames: List<String>, maxArtists: Int = 12): Map<String, Int> {
        if (!isConfigured) return emptyMap()
        val bearer = "Bearer $accessToken"
        val scores = LinkedHashMap<String, Int>()
        artistNames.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(maxArtists).forEach { name ->
            // The name has typically already been normalized by the time it reaches here (Local
            // Library artist names are normalized at scan time) — normalizing again is a harmless
            // no-op, and protects any caller that passes a raw name directly.
            val score = lookupOne(bearer, ArtistNameNormalizer.normalize(name))
            if (score != null) scores[name] = score
        }
        return scores
    }

    /** trackId -> 0..100 mainstream-ness score for this specific track, distinct from (and a finer
     *  grain than) the artist-level [mainstreamScores] above — queried as "track title + artist name"
     *  so the top hit's own stats (rather than an average across everything by that artist) become
     *  this track's own signal. Looks up at most [maxTracks] tracks sequentially, for the same reason
     *  [mainstreamScores] caps and sequences its lookups. */
    suspend fun trackMainstreamScores(tracks: List<ProfileTrack>, maxTracks: Int = 10): Map<String, Int> {
        if (!isConfigured) return emptyMap()
        val bearer = "Bearer $accessToken"
        val scores = LinkedHashMap<String, Int>()
        tracks.filter { it.name.isNotBlank() && it.artistNames.isNotEmpty() }
            .take(maxTracks)
            .forEach { track ->
                val artist = ArtistNameNormalizer.normalize(track.artistNames.first())
                val score = lookupTrackOne(bearer, "${track.name} $artist")
                if (score != null) scores[track.id] = score
            }
        return scores
    }

    private suspend fun lookupOne(bearer: String, artistName: String): Int? = try {
        val response = NetworkModule.geniusApi.search(bearer, artistName)
        val hits = response.response?.hits.orEmpty()
        if (hits.isEmpty()) {
            null
        } else {
            val pageviews = hits.mapNotNull { it.result?.stats?.pageviews }
            val raw: Double = if (pageviews.isNotEmpty()) {
                pageviews.average()
            } else {
                // Genius doesn't expose pageviews for every song depending on API tier — fall back to
                // annotation counts, then just the number of search hits, as weaker mainstream proxies.
                val annotationTotal = hits.mapNotNull { it.result?.annotation_count }.sum()
                if (annotationTotal > 0) annotationTotal.toDouble() else hits.size.toDouble()
            }
            normalizeToMainstreamScore(raw)
        }
    } catch (e: Exception) {
        null
    }

    /** Unlike [lookupOne] (which averages across every hit as a per-artist signal), this takes only
     *  the top-ranked hit — Genius returns search results in relevance order, so the first hit is its
     *  own best guess at the specific song being searched for. */
    private suspend fun lookupTrackOne(bearer: String, query: String): Int? = try {
        val response = NetworkModule.geniusApi.search(bearer, query)
        val topHit = response.response?.hits.orEmpty().firstOrNull()?.result
        if (topHit == null) {
            null
        } else {
            val raw = topHit.stats?.pageviews?.toDouble()
                ?: topHit.annotation_count?.takeIf { it > 0 }?.toDouble()
                ?: 1.0
            normalizeToMainstreamScore(raw)
        }
    } catch (e: Exception) {
        null
    }

    /** log10-scales a raw count that can span many orders of magnitude (pageviews in the tens of
     *  millions down to a handful of annotations) into a 0..100 range. Boundaries are deliberately
     *  approximate — this feeds an illustrative score, not a precise metric. */
    private fun normalizeToMainstreamScore(raw: Double): Int {
        if (raw <= 0.0) return 0
        val log = log10(raw + 1.0)
        val lowerBound = 3.0 // ~1,000 — treated as "barely any footprint"
        val upperBound = 7.0 // ~10,000,000 — treated as fully mainstream
        val normalized = ((log - lowerBound) / (upperBound - lowerBound)).coerceIn(0.0, 1.0)
        return (normalized * 100).roundToInt()
    }
}
