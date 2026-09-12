package com.twinster.app.domain

import kotlin.math.roundToInt

/** One item's own obscurity score inside a category breakdown — a single genre's or playlist's
 *  aggregate mainstream-ness, shown alongside the category's overall number. */
data class ObscurityItem(val label: String, val score: Int)

/** One category's obscurity result. [available] mirrors the established honest-fallback pattern
 *  used elsewhere on [MusicProfile] (e.g. genresAvailable) — false means this category couldn't be
 *  computed for this library/device, and the UI should show [basisNote] as an explanation rather
 *  than a fabricated or zeroed score. [items] is only populated for the Genres/Playlists categories,
 *  which break down into named sub-items rather than a single blended figure. */
data class ObscurityRating(
    val available: Boolean,
    val score: Int = 0,
    val basisNote: String? = null,
    val items: List<ObscurityItem> = emptyList()
)

/** The four obscurity/mainstream-ness categories surfaced on [MusicProfile], replacing the old
 *  single blended obscurityScore. */
data class ObscurityBreakdown(
    val songs: ObscurityRating,
    val artists: ObscurityRating,
    val genres: ObscurityRating,
    val playlists: ObscurityRating
) {
    /** Backward-compatible blended figure for consumers that want one overall number rather than
     *  the four-category breakdown (Taste Twin compatibility scoring, archetype/mood heuristics,
     *  share-card text) — averages whichever categories are actually available, falling back to a
     *  neutral 50 (matching [MusicAnalyzer]'s existing "no real data" convention) only if none are. */
    val overallScore: Int get() {
        val available = listOf(songs, artists, genres, playlists).filter { it.available }
        return if (available.isEmpty()) 50 else available.map { it.score }.average().roundToInt()
    }
    val overallAvailable: Boolean get() = listOf(songs, artists, genres, playlists).any { it.available }
}

/** A local playlist as scanned from MediaStore — see LocalLibraryScanner.scanPlaylists. */
data class ProfilePlaylist(val id: String, val name: String, val trackIds: List<String>)

object ObscurityAnalyzer {

    /** Sources with real first-party popularity (currently just Demo): derive all four categories
     *  from popularity, since there's no separate external mainstream signal to tell them apart.
     *  Demo has no playlist concept at all, so that category is honestly reported unavailable
     *  rather than faked with a placeholder number. */
    fun fromPopularity(artists: List<ProfileArtist>, tracks: List<ProfileTrack>): ObscurityBreakdown {
        val songsScore = obscurityFromPopularity(tracks.map { it.popularity })
        val artistsScore = obscurityFromPopularity(artists.map { it.popularity })

        val genreItems = genreGroups(artists).map { (genre, members) ->
            ObscurityItem(genre, obscurityFromPopularity(members.map { it.popularity }))
        }

        return ObscurityBreakdown(
            songs = ObscurityRating(available = true, score = songsScore),
            artists = ObscurityRating(available = true, score = artistsScore),
            genres = ObscurityRating(
                available = genreItems.isNotEmpty(),
                score = genreItems.map { it.score }.average0(),
                items = genreItems
            ),
            playlists = ObscurityRating(
                available = false,
                basisNote = "Playlist data isn't available for this profile."
            )
        )
    }

    /** Local Library: every category is derived from an external Genius mainstream-footprint signal
     *  (see GeniusObscurityService) rather than any first-party popularity field, since local files
     *  don't carry one. Each category degrades independently and honestly to "not available" when
     *  it has no real signal — there's no popularity fallback to lean on for local files the way
     *  there is for Demo. */
    fun fromGeniusSignals(
        artists: List<ProfileArtist>,
        tracks: List<ProfileTrack>,
        // Keyed by normalized artist name (ProfileArtist.name) -> 0..100 mainstream-ness.
        artistMainstreamScores: Map<String, Int>,
        // Keyed by ProfileTrack.id -> 0..100 mainstream-ness.
        trackMainstreamScores: Map<String, Int>,
        playlists: List<ProfilePlaylist>
    ): ObscurityBreakdown {
        val songsRating = ratingFromMainstreamMap(
            trackMainstreamScores,
            basisNote = "Estimated from ${trackMainstreamScores.size} local tracks' mainstream footprint on Genius."
        )
        val artistsRating = ratingFromMainstreamMap(
            artistMainstreamScores,
            basisNote = "Estimated from ${artistMainstreamScores.size} local artists' mainstream footprint on Genius."
        )

        val genreItems = genreGroups(artists).mapNotNull { (genre, members) ->
            val mainstream = members.mapNotNull { artistMainstreamScores[it.name] }
            if (mainstream.isEmpty()) null else ObscurityItem(genre, obscurityFromMainstream(mainstream))
        }
        val genresRating = ObscurityRating(
            available = genreItems.isNotEmpty(),
            score = genreItems.map { it.score }.average0(),
            basisNote = if (genreItems.isNotEmpty()) {
                "Aggregated per genre from local artists' mainstream footprint on Genius."
            } else null,
            items = genreItems
        )

        val playlistsRating = when {
            playlists.isEmpty() -> ObscurityRating(
                available = false,
                basisNote = "Playlist data isn't available from this device — many music apps don't share playlists system-wide."
            )
            else -> {
                val playlistItems = playlists.mapNotNull { playlist ->
                    val memberIds = playlist.trackIds.toSet()
                    val mainstream = tracks.filter { it.id in memberIds }
                        .mapNotNull { trackMainstreamScores[it.id] }
                    if (mainstream.isEmpty()) null else ObscurityItem(playlist.name, obscurityFromMainstream(mainstream))
                }
                if (playlistItems.isEmpty()) {
                    ObscurityRating(
                        available = false,
                        basisNote = "Found ${playlists.size} playlist(s) on this device, but none of their tracks had a Genius mainstream match yet."
                    )
                } else {
                    ObscurityRating(
                        available = true,
                        score = playlistItems.map { it.score }.average0(),
                        basisNote = "Aggregated from each playlist's tracks' mainstream footprint on Genius.",
                        items = playlistItems
                    )
                }
            }
        }

        return ObscurityBreakdown(songsRating, artistsRating, genresRating, playlistsRating)
    }

    private fun ratingFromMainstreamMap(map: Map<String, Int>, basisNote: String): ObscurityRating {
        if (map.isEmpty()) return ObscurityRating(available = false)
        return ObscurityRating(available = true, score = obscurityFromMainstream(map.values.toList()), basisNote = basisNote)
    }

    private fun obscurityFromMainstream(mainstreamScores: List<Int>): Int =
        (100 - mainstreamScores.average()).roundToInt().coerceIn(0, 100)

    private fun obscurityFromPopularity(popularities: List<Int>): Int {
        val avg = popularities.takeIf { it.isNotEmpty() }?.average() ?: 50.0
        return (100 - avg).roundToInt().coerceIn(0, 100)
    }

    private fun genreGroups(artists: List<ProfileArtist>): List<Pair<String, List<ProfileArtist>>> {
        val map = LinkedHashMap<String, MutableList<ProfileArtist>>()
        artists.forEach { artist -> artist.genres.forEach { g -> map.getOrPut(g) { mutableListOf() }.add(artist) } }
        return map.entries.sortedByDescending { it.value.size }.take(8).map { it.key to it.value }
    }

    private fun List<Int>.average0(): Int = if (isEmpty()) 0 else average().roundToInt()
}
