package com.twinster.app.domain

/**
 * Best-effort cleanup of channel/tag-derived artist names before they're used as a Genius search
 * query or shown in the UI. Real-world libraries (especially YouTube-sourced files) frequently carry
 * the uploading *channel's* name rather than the artist's real name — "PostMaloneVEVO",
 * "Ed Sheeran - Topic", "TheWeekndOfficial" — which breaks a Genius lookup for the real artist and
 * looks unpolished anywhere it's displayed raw.
 *
 * Deliberately conservative: every rule below only fires on a recognizable, bounded pattern (a
 * known suffix word, a lowercase->uppercase transition, a trailing jammed filler word on an
 * otherwise spaceless token) rather than attempting to parse arbitrary names. [normalize] never
 * returns a blank result — it falls back to the trimmed raw name if every rule leaves nothing usable.
 */
object ArtistNameNormalizer {

    // Suffix words stripped only when they sit at a genuine word boundary — either separated by a
    // space, or immediately after a lowercase/digit->uppercase transition (the common
    // channel-name-jammed case, e.g. "PostMaloneVEVO", "DrakeOfficial"). Longer phrases are listed
    // first so "Official Music" strips as one unit rather than two separate passes leaving debris.
    private val boundedSuffixWords = listOf(
        "Official Music Channel", "Official Music", "Official Channel", "Music Channel",
        "VEVO", "Official", "Music", "Records", "Channel"
    )

    // Fan/leak-archive channel jargon that, unlike the above, is typically glued onto a name with
    // NO case-boundary at all (e.g. "sofaygoarchive", "faygoanything" — all lowercase, no
    // recognizable seam). No static rule set can enumerate every fan channel's naming convention, so
    // this only strips a single trailing filler word from an otherwise spaceless token, as a
    // best-effort attempt to expose the underlying stem for Genius's own fuzzy search to resolve —
    // it is not expected to always leave a "clean" name behind.
    private val fanChannelFillers = listOf(
        "unreleased", "archive", "tracker", "central", "updates", "fanpage", "fan page",
        "leaks", "leak", "vault", "anything", "hub", "news", "daily", "world", "source", "love"
    )

    /** Cleans a raw channel/tag-derived artist name for display and for use as a Genius search
     *  query. Always returns a non-blank string (falls back to the trimmed [rawName] if every rule
     *  would otherwise leave nothing). Callers that want to keep the original around for reference/
     *  fallback (e.g. [ProfileArtist.rawName]) should hold onto [rawName] themselves — this function
     *  is a pure transform, not a record of both values. */
    fun normalize(rawName: String): String {
        val trimmedRaw = rawName.trim()
        if (trimmedRaw.isEmpty()) return trimmedRaw

        var name = stripTopicSuffix(trimmedRaw)
        name = stripBoundedSuffixWords(name)
        name = stripFanChannelFiller(name)
        name = maybeSplitCamelCase(name)

        return name.trim().ifBlank { trimmedRaw }
    }

    /** YouTube's auto-generated "topic channel" suffix for a bare artist upload, e.g.
     *  "Ed Sheeran - Topic" -> "Ed Sheeran". */
    private fun stripTopicSuffix(input: String): String {
        val stripped = input.replace(Regex("(?i)\\s*-\\s*topic$"), "")
        return stripped.ifBlank { input }
    }

    private fun stripBoundedSuffixWords(input: String): String {
        var name = input
        // Bounded to a few passes: a name could carry more than one suffix ("Drake Official
        // Music"), but this must terminate rather than eat into a genuine short name.
        repeat(3) {
            val before = name
            for (word in boundedSuffixWords) {
                name = stripTrailingBoundedWord(name, word)
            }
            if (name == before) return name
        }
        return name
    }

    private fun stripTrailingBoundedWord(input: String, word: String): String {
        val regex = Regex("(?i)(?:^|\\s|(?<=[a-z0-9]))${Regex.escape(word)}$")
        val match = regex.find(input) ?: return input
        val stripped = input.substring(0, match.range.first).trimEnd()
        // Never strip down to nothing/near-nothing — a name that's *only* "Music" or "Official"
        // is exactly the ambiguous case this rule shouldn't guess about.
        return if (stripped.length >= 2) stripped else input
    }

    /** Only applies to a single spaceless token (a channel-handle-style name, not an already
     *  reasonably formatted one) and strips trailing filler words one at a time, repeating until
     *  none match — real fan-channel handles often jam on more than one ("kidlaroiloveunreleased" =
     *  "kidlaroi" + "love" + "unreleased"), and stopping after a single removal would leave debris
     *  ("kidlaroilove") that Genius's fuzzy search is far less likely to resolve correctly than the
     *  clean stem. Bounded to a few passes for the same termination reason as the bounded-suffix
     *  stripper above. This is a probabilistic stem-exposing step, not a guarantee of a clean name —
     *  see the class doc. */
    private fun stripFanChannelFiller(input: String): String {
        if (input.contains(' ')) return input
        var name = input
        repeat(3) {
            val lower = name.lowercase()
            val matched = fanChannelFillers.firstOrNull { filler ->
                lower.endsWith(filler) && lower.length - filler.length >= 3
            } ?: return name
            name = name.substring(0, name.length - matched.length)
        }
        return name
    }

    /** Fallback-only: a name with no spaces at all that looks like two (or three) words jammed
     *  together gets split at each lowercase/digit->uppercase transition — "PostMalone" ->
     *  "Post Malone". Never applied to names that already have spaces (already correctly
     *  formatted), and rejected if the split would produce more than 3 fragments or any blank
     *  fragment — that shape is more likely an all-caps stylization or acronym than two jammed
     *  words, and this heuristic isn't meant to guess at those. */
    private fun maybeSplitCamelCase(input: String): String {
        if (input.contains(' ') || input.length < 4) return input
        val spaced = input.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        if (spaced == input) return input
        val words = spaced.split(" ")
        return if (words.size in 2..3 && words.all { it.isNotBlank() }) spaced else input
    }
}
