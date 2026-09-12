package com.twinster.app.domain

import kotlin.math.roundToInt

object MusicAnalyzer {

    fun analyze(
        artists: List<ProfileArtist>,
        tracks: List<ProfileTrack>,
        source: ProfileSource,
        // Songs/Artists/Genres/Playlists obscurity breakdown — see ObscurityAnalyzer. Callers build
        // this themselves (DemoDataProvider via ObscurityAnalyzer.fromPopularity, Local Library via
        // ObscurityAnalyzer.fromGeniusSignals) since the right signal to use depends entirely on the
        // source; MusicAnalyzer only consumes the result for its own genre/mood/archetype heuristics.
        obscurity: ObscurityBreakdown,
        decadeBasisNote: String? = null,
        // Non-null only for Local Library scans where YAMNet content analysis ran (see
        // LocalLibraryScanner/TwinsterViewModel) — surfaced on the profile so the UI can be honest
        // about genre data being a tag+content blend rather than pure file-tag data.
        genreBasisNote: String? = null
    ): MusicProfile {
        // Demo always carries genre tags on its artist objects. Local Library genre tags come
        // from MediaStore's genre column, a MediaMetadataRetriever fallback, or on-device YAMNet audio
        // content analysis for a sampled subset of tracks (see LocalLibraryScanner) — all three feed
        // the same ProfileArtist.genres list, so this counting logic doesn't need to know which source
        // any individual genre string came from.
        val genreCounts = LinkedHashMap<String, Int>()
        artists.forEach { artist ->
            artist.genres.forEach { g -> genreCounts[g] = (genreCounts[g] ?: 0) + 1 }
        }
        val genresAvailable = if (source == ProfileSource.LOCAL_LIBRARY) {
            genreCounts.isNotEmpty()
        } else {
            true
        }

        val genreBreakdown = if (genresAvailable) {
            val totalGenreHits = genreCounts.values.sum().coerceAtLeast(1)
            genreCounts.entries
                .sortedByDescending { it.value }
                .take(8)
                .map { GenreShare(it.key, ((it.value * 100f) / totalGenreHits).roundToInt()) }
                .ifEmpty { listOf(GenreShare("eclectic", 100)) }
        } else {
            emptyList()
        }

        val decadeCounts = LinkedHashMap<String, Int>()
        tracks.forEach { track ->
            val year = track.releaseDate?.take(4)?.toIntOrNull()
            if (year != null) {
                val decade = "${(year / 10) * 10}s"
                decadeCounts[decade] = (decadeCounts[decade] ?: 0) + 1
            }
        }
        val totalDecadeHits = decadeCounts.values.sum().coerceAtLeast(1)
        val decadeSplit = decadeCounts.entries
            .sortedByDescending { it.value }
            .map { DecadeShare(it.key, ((it.value * 100f) / totalDecadeHits).roundToInt()) }
            .ifEmpty { listOf(DecadeShare("2020s", 100)) }

        // The mood/archetype heuristics below only need one blended obscurity figure, not the full
        // four-category breakdown — overallScore already falls back to a neutral 50 when no category
        // has real data, mirroring this function's old obscurityAvailable-gated fallback.
        val obscurityScore = obscurity.overallScore
        // "Mainstream-ness" is just the inverse of obscurity — for Local Library this IS the Genius
        // signal (there's no first-party popularity field the way Spotify had one; ProfileArtist/
        // ProfileTrack.popularity is always 0 for this source). Previously energyScore was called
        // with that always-zero popularity average directly, which silently applied the exact same
        // fixed negative bias to every single Local Library profile's Energy score regardless of the
        // actual music — a likely cause of a suspiciously identical score showing up across different
        // libraries.
        val mainstreamScore = (100 - obscurityScore).toDouble()

        val energyScore = GenreMoodHeuristic.energyScore(genreBreakdown, mainstreamScore)
        val valenceScore = GenreMoodHeuristic.valenceScore(genreBreakdown, obscurityScore)

        val moodSummary = ArchetypeEngine.deriveMoodSummary(energyScore, valenceScore) +
            if (!genresAvailable) " (estimated — no genre data available from this source)" else ""
        val (archetypeTitle, archetypeDescription) = ArchetypeEngine.deriveArchetype(
            genreBreakdown, decadeSplit, obscurityScore, energyScore, valenceScore
        )

        return MusicProfile(
            source = source,
            topArtists = artists,
            topTracks = tracks,
            genreBreakdown = genreBreakdown,
            genresAvailable = genresAvailable,
            genreBasisNote = genreBasisNote,
            decadeSplit = decadeSplit,
            decadeBasisNote = decadeBasisNote,
            obscurity = obscurity,
            energyScore = energyScore,
            valenceScore = valenceScore,
            moodSummary = moodSummary,
            archetypeTitle = archetypeTitle,
            archetypeDescription = archetypeDescription
        )
    }
}
