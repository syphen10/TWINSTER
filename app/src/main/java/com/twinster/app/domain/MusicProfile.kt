package com.twinster.app.domain

data class GenreShare(val genre: String, val percent: Int)
data class DecadeShare(val decade: String, val percent: Int)

data class MusicProfile(
    val source: ProfileSource,
    val topArtists: List<ProfileArtist>,
    val topTracks: List<ProfileTrack>,
    val genreBreakdown: List<GenreShare>,
    val genresAvailable: Boolean,
    /** Non-null only when genre data for this profile blends file tags with on-device YAMNet audio
     *  content analysis (Local Library) — see MusicAnalyzer. */
    val genreBasisNote: String? = null,
    val decadeSplit: List<DecadeShare>,
    /** Non-null only when decades were derived from listen dates rather than real release dates. */
    val decadeBasisNote: String? = null,
    /** Songs/Artists/Genres/Playlists obscurity-rating categories — see ObscurityAnalyzer. Replaces
     *  the old single blended obscurityScore/obscurityAvailable/obscurityBasisNote fields; each
     *  category carries its own availability and honest fallback note. */
    val obscurity: ObscurityBreakdown,
    // Heuristic 0..1 scores derived from genres/popularity — see MusicAnalyzer/GenreMoodHeuristic.
    val energyScore: Float,
    val valenceScore: Float,
    val moodSummary: String,
    val archetypeTitle: String,
    val archetypeDescription: String
) {
    val topGenre: String get() = genreBreakdown.firstOrNull()?.genre ?: "eclectic"
    val topArtistNames: List<String> get() = topArtists.take(5).map { it.name }

    /** Blended overall obscurity figure for consumers that want one number rather than the
     *  four-category breakdown — see [ObscurityBreakdown.overallScore]. */
    val overallObscurityScore: Int get() = obscurity.overallScore
    val overallObscurityAvailable: Boolean get() = obscurity.overallAvailable
}
