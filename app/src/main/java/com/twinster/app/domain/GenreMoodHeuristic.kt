package com.twinster.app.domain

/**
 * No source here (Local Library, Demo) exposes real per-track audio features (energy/valence/
 * danceability), so instead we guess a rough energy/valence position from genre keywords (weighted
 * by genre share) blended with popularity/obscurity, using only genre and popularity data.
 */
object GenreMoodHeuristic {

    private val highEnergyKeywords = listOf(
        "edm", "dance", "house", "techno", "trap", "drum and bass", "dubstep",
        "punk", "metal", "hardcore", "hip hop", "rap", "reggaeton", "dancehall", "phonk"
    )
    private val mellowKeywords = listOf(
        "acoustic", "lo-fi", "lofi", "ambient", "classical", "chill", "folk",
        "jazz", "soul", "singer-songwriter", "new age", "instrumental", "piano"
    )
    private val brightKeywords = listOf(
        "pop", "funk", "disco", "reggae", "tropical", "dancehall", "latin", "k-pop", "afrobeats"
    )
    private val darkKeywords = listOf(
        "emo", "sad", "doom", "dark", "gothic", "black metal", "drill", "grunge", "screamo"
    )

    /** [mainstreamScore] is 0-100 "how mainstream" (i.e. `100 - obscurityScore`) — for Local Library
     *  this is the Genius-derived signal (see ObscurityAnalyzer), not a raw popularity field. Local
     *  Library's own ProfileArtist/ProfileTrack.popularity is always 0 (there's no first-party
     *  popularity source the way Spotify had one), so this used to be called with that always-zero
     *  value — silently applying the same fixed negative bias to every single Local Library user's
     *  Energy score regardless of their actual data. Now wired to the real Genius mainstream signal,
     *  falling back to a neutral 50 when Genius has no match, same as everywhere else that degrades
     *  honestly rather than fabricating a number. */
    fun energyScore(genreBreakdown: List<GenreShare>, mainstreamScore: Double): Float {
        val genreEnergy = weightedKeywordScore(genreBreakdown, highEnergyKeywords, 0.8f, mellowKeywords, 0.25f)
        // Mainstream, high-production genres skew slightly more energetic in this heuristic.
        val popularityBoost = ((mainstreamScore - 50.0) / 200.0).toFloat()
        return (genreEnergy + popularityBoost).coerceIn(0f, 1f)
    }

    fun valenceScore(genreBreakdown: List<GenreShare>, obscurityScore: Int): Float {
        val genreValence = weightedKeywordScore(genreBreakdown, brightKeywords, 0.75f, darkKeywords, 0.25f)
        // More underground taste leans slightly moodier in this heuristic.
        val obscurityPull = (50 - obscurityScore) / 200f
        return (genreValence + obscurityPull).coerceIn(0f, 1f)
    }

    private fun weightedKeywordScore(
        genreBreakdown: List<GenreShare>,
        highKeywords: List<String>,
        highScore: Float,
        lowKeywords: List<String>,
        lowScore: Float
    ): Float {
        if (genreBreakdown.isEmpty()) return 0.5f
        var weighted = 0f
        var totalWeight = 0f
        genreBreakdown.forEach { share ->
            val g = share.genre.lowercase()
            val weight = share.percent.toFloat().coerceAtLeast(1f)
            val score = when {
                highKeywords.any { g.contains(it) } -> highScore
                lowKeywords.any { g.contains(it) } -> lowScore
                else -> 0.5f
            }
            weighted += score * weight
            totalWeight += weight
        }
        return if (totalWeight > 0f) weighted / totalWeight else 0.5f
    }
}
