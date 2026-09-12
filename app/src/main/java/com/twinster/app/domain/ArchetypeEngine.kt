package com.twinster.app.domain

object ArchetypeEngine {

    fun deriveMoodSummary(energy: Float, valence: Float): String {
        val energyWord = when {
            energy > 0.66f -> "high-energy"
            energy > 0.4f -> "mid-tempo"
            else -> "mellow"
        }
        val valenceWord = when {
            valence > 0.66f -> "upbeat"
            valence > 0.4f -> "bittersweet"
            else -> "moody"
        }
        return "$energyWord, $valenceWord"
    }

    fun deriveArchetype(
        genreBreakdown: List<GenreShare>,
        decadeSplit: List<DecadeShare>,
        obscurityScore: Int,
        energy: Float,
        valence: Float
    ): Pair<String, String> {
        val topGenre = genreBreakdown.firstOrNull()?.genre ?: ""
        val genreCount = genreBreakdown.size
        val oldDecadeShare = decadeSplit.filter { decadeValue(it.decade) < 2000 }.sumOf { it.percent }

        return when {
            obscurityScore > 70 -> "The Underground Scout" to
                "You dig deeper than the algorithm. Deep cuts and unsigned names dominate your rotation."
            genreCount >= 8 -> "The Genre Hopper" to
                "No single lane holds you. Your library reads like a record store with no section labels."
            oldDecadeShare > 45 -> "The Nostalgic Curator" to
                "Half your library predates streaming itself. Your taste runs by era more than by single song."
            energy > 0.7f && valence > 0.6f -> "The Main Character" to
                "Everything you play sounds like a montage. High-energy, upbeat, built for motion."
            valence < 0.35f && energy < 0.45f -> "The Late-Night Thinker" to
                "Moody, unhurried, built for headphones after midnight rather than a party."
            topGenre.contains("pop", true) -> "The Chart Chaser" to
                "You keep a finger on the pulse of what's actually popular right now."
            obscurityScore < 30 -> "The Crowd Pleaser" to
                "Your top artists are everyone's top artists. Reliable taste, mainstream comfort."
            else -> "The Balanced Listener" to
                "No extremes here — a genuinely well-rounded rotation across mood and popularity."
        }
    }

    private fun decadeValue(label: String): Int = label.filter { it.isDigit() }.take(4).toIntOrNull() ?: 2020
}
