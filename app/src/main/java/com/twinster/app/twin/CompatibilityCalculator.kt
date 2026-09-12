package com.twinster.app.twin

import kotlin.math.abs
import kotlin.math.roundToInt

data class CompatibilityResult(
    val overallPercent: Int,
    val sharedGenres: List<String>,
    val sharedArtists: List<String>,
    val biggestDifferences: List<String>
)

object CompatibilityCalculator {

    // No cosine-over-audio-features here: no source supplies real per-track energy/valence/
    // danceability. Similarity is genre + artist overlap plus how close the two genre-derived mood
    // heuristics and obscurity scores land. Works for any two MusicProfiles regardless of source
    // (Local Library <-> Local Library, Local Library/Demo <-> Demo Friend).
    fun compare(mine: TastePayload, theirs: TastePayload): CompatibilityResult {
        val myGenres = mine.genres.map { it.g.lowercase() }.toSet()
        val theirGenres = theirs.genres.map { it.g.lowercase() }.toSet()
        val genreJaccard = jaccard(myGenres, theirGenres)

        val myArtists = mine.artists.map { it.lowercase() }.toSet()
        val theirArtists = theirs.artists.map { it.lowercase() }.toSet()
        val artistJaccard = jaccard(myArtists, theirArtists)

        val moodSimilarity = 1.0 - ((abs(mine.energy - theirs.energy) + abs(mine.valence - theirs.valence)) / 2.0)
        val obscuritySimilarity = 1.0 - (abs(mine.obscurity - theirs.obscurity) / 100.0)

        val overall = (genreJaccard * 0.4 + artistJaccard * 0.3 + moodSimilarity * 0.2 + obscuritySimilarity * 0.1) * 100
        val overallPercent = overall.roundToInt().coerceIn(0, 100)

        val sharedGenres = mine.genres.map { it.g }
            .filter { it.lowercase() in theirGenres }
        val sharedArtists = mine.artists.filter { it.lowercase() in theirArtists }

        val differences = mutableListOf<String>()
        if (abs(mine.energy - theirs.energy) > 0.25f) differences.add("energy level")
        if (abs(mine.valence - theirs.valence) > 0.25f) differences.add("mood positivity")
        if (abs(mine.obscurity - theirs.obscurity) > 25) differences.add("mainstream vs. underground taste")
        if (sharedGenres.isEmpty()) differences.add("genre overlap")

        return CompatibilityResult(overallPercent, sharedGenres, sharedArtists, differences)
    }

    fun verdict(result: CompatibilityResult, theirName: String?): String {
        val name = theirName ?: "your friend"
        return when {
            result.overallPercent >= 85 -> "You and $name are basically taste twins — scary good match."
            result.overallPercent >= 65 -> "Solid overlap with $name. You'd survive each other's playlists."
            result.overallPercent >= 40 -> "Some common ground with $name, but you'd fight over the aux."
            else -> "You and $name are musical opposites. Chaotic playlist energy if combined."
        }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }
}
