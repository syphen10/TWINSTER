package com.twinster.app.domain

/**
 * Maps YAMNet's 521 AudioSet output classes (see assets/yamnet_class_map.csv, the full official
 * ontology bundled alongside the model) down to a clean, genre-shaped subset. AudioSet is an event
 * taxonomy, not a genre taxonomy — most of its 521 classes ("Vehicle", "Speech", "Dog", "Siren"...)
 * would be nonsense to surface as a music genre, so only two curated slices of it are used here:
 *
 *  - [primaryGenreLabels]: AudioSet's own "Music genre" branch (Rock music, Reggae, Techno, ...) —
 *    these map close to 1:1 onto real genre names, so they're trusted as a direct, high-confidence
 *    signal and normalized to lowercase tokens that match the vocabulary [GenreMoodHeuristic] already
 *    keys off of (e.g. "hip hop", "edm", "drum and bass").
 *  - [instrumentHintLabels]: a small allowlist of instrument classes whose presence is a strong,
 *    fairly unambiguous genre tell on its own (a saxophone lead strongly implies jazz; a steel/slide
 *    guitar strongly implies country). Deliberately excludes instruments that show up across too many
 *    genres to mean anything (piano, generic "Guitar", drum kit) — those would just add noise.
 *
 * Everything else YAMNet can detect (vocals, sound effects, non-music audio events) is intentionally
 * left out of both maps and never reaches the genre pipeline.
 */
object AudioGenreTaxonomy {

    /** AudioSet "Music genre" branch display name -> normalized genre token. */
    val primaryGenreLabels: Map<String, String> = mapOf(
        "Pop music" to "pop",
        "Hip hop music" to "hip hop",
        "Rock music" to "rock",
        "Heavy metal" to "metal",
        "Punk rock" to "punk",
        "Rhythm and blues" to "r&b",
        "Reggae" to "reggae",
        "Country" to "country",
        "Funk" to "funk",
        "Soul music" to "soul",
        "Swing music" to "swing",
        "Jazz" to "jazz",
        "Disco" to "disco",
        "Folk music" to "folk",
        "Middle Eastern music" to "middle eastern",
        "Classical music" to "classical",
        "Opera" to "opera",
        "Electronic music" to "electronic",
        "House music" to "house",
        "Techno" to "techno",
        "Dubstep" to "dubstep",
        "Drum and bass" to "drum and bass",
        "Electronic dance music" to "edm",
        "Ambient music" to "ambient",
        "Trance music" to "trance",
        "Music of Latin America" to "latin",
        "Salsa music" to "salsa",
        "Ska" to "ska",
        "Blues" to "blues",
        "New-age music" to "new age",
        "Music of Africa" to "african music",
        "Christian music" to "christian",
        "Gospel music" to "gospel",
        "Music of Asia" to "asian",
        "Carnatic music" to "carnatic",
        "Music of Bollywood" to "bollywood",
        "Traditional music" to "traditional",
        "Independent music" to "indie",
        "Dance music" to "dance"
    )

    /** Instrument-level AudioSet class -> weak genre hint, used only when nothing in
     *  [primaryGenreLabels] scores above threshold for a track (see YamnetClassifier). */
    val instrumentHintLabels: Map<String, String> = mapOf(
        "Electric guitar" to "rock",
        "Steel guitar, slide guitar" to "country",
        "Banjo" to "folk",
        "Ukulele" to "folk",
        "Saxophone" to "jazz",
        "Trumpet" to "jazz",
        "Orchestra" to "classical",
        "Violin, fiddle" to "classical",
        "Drum machine" to "electronic"
    )

    /** Minimum averaged sigmoid score (across analyzed frames) for a primary genre-branch class to
     *  count as a real detection — YAMNet's multi-label outputs run low-confidence on real mixed
     *  music, so this is deliberately permissive rather than requiring near-certainty. */
    const val PRIMARY_SCORE_THRESHOLD = 0.15f

    /** Higher bar for the much weaker instrument-hint fallback, since it's a proxy signal rather than
     *  a direct genre class. */
    const val HINT_SCORE_THRESHOLD = 0.25f

    /** Keyed by a normalized (trimmed, lowercased) label rather than the exact display-name string.
     *  This was never verified against a real device: [YamnetClassifier] passes through whatever
     *  string the Task Library's [org.tensorflow.lite.task.audio.classifier.AudioClassifier] surfaces
     *  as a category's label, which is expected to be the model's embedded AudioSet display name
     *  ("Pop music", etc.) but could plausibly differ in case or incidental whitespace depending on
     *  how that metadata was authored — a case-sensitive exact match would then silently produce zero
     *  genre hits for every single track without ever throwing or logging anything wrong. Normalizing
     *  both sides removes that entire failure class at effectively no cost. */
    private fun norm(s: String) = s.trim().lowercase()

    private val normalizedPrimaryGenreLabels: Map<String, String> =
        primaryGenreLabels.entries.associate { (label, genre) -> norm(label) to genre }

    private val normalizedInstrumentHintLabels: Map<String, String> =
        instrumentHintLabels.entries.associate { (label, genre) -> norm(label) to genre }

    /** Picks up to [maxGenres] content-detected genres from a track's averaged AudioSet class scores.
     *  Falls back to instrument hints only when no genre-branch class clears [PRIMARY_SCORE_THRESHOLD]. */
    fun detectGenres(averagedScores: Map<String, Float>, maxGenres: Int = 2): List<String> {
        val primaryHits = averagedScores.entries
            .mapNotNull { (label, score) -> normalizedPrimaryGenreLabels[norm(label)]?.let { it to score } }
            .filter { it.second >= PRIMARY_SCORE_THRESHOLD }
            .sortedByDescending { it.second }

        if (primaryHits.isNotEmpty()) {
            return primaryHits.take(maxGenres).map { it.first }.distinct()
        }

        val hintHit = averagedScores.entries
            .mapNotNull { (label, score) -> normalizedInstrumentHintLabels[norm(label)]?.let { it to score } }
            .filter { it.second >= HINT_SCORE_THRESHOLD }
            .maxByOrNull { it.second }

        return listOfNotNull(hintHit?.first)
    }
}
