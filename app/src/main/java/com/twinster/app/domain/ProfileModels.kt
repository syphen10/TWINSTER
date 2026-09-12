package com.twinster.app.domain

import kotlinx.serialization.Serializable

/** Where a [MusicProfile] came from. Downstream screens branch on this. */
enum class ProfileSource {
    DEMO,
    /** Scanned from the device's own on-device audio files via MediaStore — see LocalLibraryScanner. */
    LOCAL_LIBRARY
}

/**
 * Source-agnostic stand-in for an artist. Decouples [MusicAnalyzer] and [MusicProfile] from any
 * one source's DTOs so the same analysis pipeline works for both Demo and Local Library.
 * Serializable so a Local Library scan's raw artists can be cached to disk (see SettingsStore).
 */
@Serializable
data class ProfileArtist(
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
    val popularity: Int = 0,
    val imageUrl: String? = null,
    /** Local Library only: the original, un-normalized tag/channel-name value [name] was cleaned up
     *  from (see ArtistNameNormalizer) — kept around as a fallback/reference in case normalization
     *  guessed wrong for this name, rather than destructively discarding it. Null when [name] wasn't
     *  changed by normalization (nothing to fall back to) or for sources (Demo) that don't go
     *  through the normalizer at all. Defaults to null so old cached LocalLibrarySnapshot JSON
     *  without this field still decodes fine. */
    val rawName: String? = null
)

/** Source-agnostic stand-in for a track. Serializable for the same on-disk caching reason as
 *  [ProfileArtist]. */
@Serializable
data class ProfileTrack(
    val id: String,
    val name: String,
    val artistNames: List<String> = emptyList(),
    val releaseDate: String? = null,
    val popularity: Int = 0,
    val imageUrl: String? = null,
    /** Local Library only: this track's own combined genre signal — its file tag when present,
     *  otherwise a YAMNet content-detected genre when the track was one of the scan's sampled tracks
     *  (see LocalLibraryScanner). Null for Demo tracks, which carry genre at the artist level only,
     *  and for untagged/unsampled local tracks with no signal at all. Defaults to null so old
     *  cached [LocalLibrarySnapshot] JSON without this field still decodes fine. */
    val genre: String? = null
)
