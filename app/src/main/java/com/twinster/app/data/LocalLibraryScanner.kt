package com.twinster.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.twinster.app.domain.ArtistNameNormalizer
import com.twinster.app.domain.ProfileArtist
import com.twinster.app.domain.ProfilePlaylist
import com.twinster.app.domain.ProfileTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

class LocalLibraryScanException(message: String) : Exception(message)

data class LocalLibraryScanResult(
    val artists: List<ProfileArtist>,
    val tracks: List<ProfileTrack>,
    // Content-based (YAMNet) genre detection stats — surfaced so the UI/analyzer can be honest about
    // how much of the library actually got audio-analyzed rather than implying full coverage.
    val contentAnalyzedCount: Int = 0,
    val totalTrackCount: Int = 0,
    val contentAnalysisCapped: Boolean = false,
    // MediaStore.Audio.Playlists data, if any actually exists on this device — see scanPlaylists.
    // Empty either because the device genuinely has no MediaStore-visible playlists (the common
    // case — most modern music apps keep playlists in their own private database rather than
    // writing to this legacy system-wide store) or because the scan hasn't found any yet.
    val playlists: List<ProfilePlaylist> = emptyList(),
    // Third-tier genre fallback stats (see TheAudioDbGenreService) — only queried for artists that
    // had ZERO genre signal from tags/content-analysis, so the UI can honestly note when some genre
    // data came from an internet lookup rather than implying it was all on-device.
    val audioDbGenresFoundCount: Int = 0
)

/** Raw scan output cached to disk (see SettingsStore) so the user can revisit their library without
 *  re-scanning. Re-running MusicAnalyzer.analyze() on this each time is deliberate — cheaper and
 *  simpler than trying to keep a serialized, fully-analyzed MusicProfile in sync with analyzer changes. */
@Serializable
data class LocalLibrarySnapshot(
    val artists: List<ProfileArtist>,
    val tracks: List<ProfileTrack>,
    val scannedAtMillis: Long
)

private data class RawTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val genre: String?,
    val dateAdded: Long,
    val contentUri: Uri
)

/** Mutable intermediate per-track state built during the tag-scan pass, then optionally enriched by
 *  content analysis before the final ProfileTrack/ProfileArtist pass. */
private class TrackBuilder(
    val raw: RawTrack,
    val artistName: String,
    val rawArtistName: String,
    var tagGenre: String?,
    var imageUrl: String?
) {
    var contentGenres: List<String> = emptyList()
}

private data class RawPlaylist(val id: Long, val name: String, val memberTrackIds: List<Long>)

/**
 * Scans the device's own on-device audio files via MediaStore.Audio.Media — the correct modern API
 * for this (scoped storage means walking the raw filesystem isn't reliable or necessary). Genre and
 * embedded artwork are unreliable in MediaStore's own columns, so both fall back to
 * MediaMetadataRetriever per-track, which is part of the Android SDK already (no extra dependency).
 *
 * On top of file tags, a capped sample of tracks also gets real content-based genre detection via
 * YAMNet (see [YamnetClassifier]/[AudioGenreTaxonomy]) — tag data stays authoritative when present,
 * content detection fills gaps or adds a supplementary signal when it doesn't.
 */
object LocalLibraryScanner {

    // MediaMetadataRetriever opens each file individually — capping embedded-art decodes keeps a
    // library of thousands of tracks from taking minutes just to build a Top Picks card.
    private const val MAX_ARTWORK_EXTRACTIONS = 30

    // Decoding + running inference per track is meaningfully slower than reading a tag (real audio
    // decode + a TFLite forward pass vs. a metadata read). Capping keeps a library of thousands of
    // tracks from turning a scan into a multi-minute wait — tracks missing a genre tag are prioritized
    // for the cap since that's where content detection adds the most value.
    private const val MAX_CONTENT_ANALYSIS_TRACKS = 150

    // How many tracks get their analyzeTrack() round-trip in flight at once. The remote service
    // itself now bounds actual decode+inference work to 2 concurrent workers (see
    // AudioAnalysisService) — a slightly higher client-side batch just keeps that pool fed (a track
    // waiting on a free worker slot costs nothing but IPC queuing) without firing an unbounded flood
    // of simultaneous requests.
    private const val CONTENT_ANALYSIS_CONCURRENCY = 4

    // MediaMetadataRetriever.setDataSource()+extractMetadata() is a genuinely slow, blocking-I/O
    // call per file — running it strictly one track at a time was the actual "stuck" bottleneck on
    // large libraries (this tag-scan pass runs before content analysis even starts, and previously
    // had no cap, no concurrency, and no progress feedback at all, so a library with many untagged
    // files could sit on a static "Reading your local music library…" message for minutes,
    // indistinguishable from a hang). I/O-bound retriever calls parallelize safely at a higher
    // degree than the audio-analysis pipeline's decode+ML work does.
    private const val TAG_SCAN_CONCURRENCY = 12

    // TheAudioDb's shared-key rate limit (30 req/min, enforced sequentially
    // in TheAudioDbGenreService) means each lookup costs real wall-clock time — capping keeps a
    // library with many genre-less artists from adding minutes to a scan for a last-resort signal.
    private const val MAX_AUDIODB_LOOKUPS = 20

    suspend fun scan(
        context: Context,
        onProgress: ((stage: String, analyzed: Int, total: Int) -> Unit)? = null
    ): LocalLibraryScanResult {
        // ContentResolver.query() is a plain blocking call with no suspension points — same hazard
        // class as MediaMetadataRetriever above, and this one runs FIRST, before any progress text
        // ever updates from "Reading your local music library…". A slow/stuck system media database
        // (which does happen, e.g. right after a large library change triggers a provider-side
        // re-index) would freeze the scan before it even starts, which is indistinguishable from a
        // hang from the very first moment — this was a previously-missed gap of the exact same kind
        // already fixed for the per-track tag reads.
        val rawTracks = withTimeoutOrNull(15_000L) {
            withContext(Dispatchers.IO) { queryMediaStore(context.contentResolver) }
        } ?: emptyList()
        if (rawTracks.isEmpty()) return LocalLibraryScanResult(emptyList(), emptyList())

        val artistArtwork = java.util.concurrent.ConcurrentHashMap<String, String>()
        val artworkExtractions = AtomicInteger(0)
        val tagScanCompleted = AtomicInteger(0)
        val semaphore = Semaphore(TAG_SCAN_CONCURRENCY)

        val builders = coroutineScope {
            rawTracks.map { raw ->
                async {
                    semaphore.withPermit {
                        val rawArtistName = raw.artist.trim().ifBlank { "Unknown Artist" }
                        // Clean up channel/tag-derived names ("PostMaloneVEVO", "Ed Sheeran - Topic")
                        // before this name is used to group tracks into artists, shown anywhere in
                        // the UI, or sent to Genius as a search query — see ArtistNameNormalizer.
                        val artistName = ArtistNameNormalizer.normalize(rawArtistName)
                        var genre = raw.genre?.trim()?.takeIf { it.isNotBlank() }
                        var imageUrl: String? = null

                        // Loosely racy (two tracks for the same never-yet-seen artist could both
                        // decide "yes, extract art") — acceptable, this is a soft perf cap, not a
                        // correctness requirement; worst case is a handful of harmless extra decodes.
                        val wantArtwork = artworkExtractions.get() < MAX_ARTWORK_EXTRACTIONS &&
                            !artistArtwork.containsKey(artistName)
                        if (genre == null || wantArtwork) {
                            // MediaMetadataRetriever.setDataSource()/extractMetadata() are plain
                            // blocking calls with NO suspension points — on certain corrupt/DRM/
                            // truncated files they're known to hang indefinitely rather than throw.
                            // Without this timeout, one such file anywhere in the whole library would
                            // permanently stall every track behind it in awaitAll(), regardless of how
                            // many tracks run concurrently — this was the actual "stuck" root cause,
                            // not the (already-hardened) audio-content-analysis stage. withTimeoutOrNull
                            // can't force-stop an already-blocked native thread, but it does let THIS
                            // coroutine give up and move on immediately once the timeout fires, which is
                            // exactly what unblocks the rest of the scan; the orphaned blocked call (if
                            // any) just finishes later on its own IO-pool thread, harmlessly.
                            kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                                val retriever = MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(context, raw.contentUri)
                                    if (genre == null) {
                                        genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                                            ?.trim()?.takeIf { it.isNotBlank() }
                                    }
                                    if (wantArtwork) {
                                        val art = retriever.embeddedPicture
                                        if (art != null) {
                                            imageUrl = LocalArtworkCache.write(context, raw.id, art)
                                            if (imageUrl != null) artworkExtractions.incrementAndGet()
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Corrupt/unsupported file metadata — keep MediaStore-only fields.
                                } finally {
                                    try {
                                        retriever.release()
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                        imageUrl?.let { artistArtwork.putIfAbsent(artistName, it) }

                        onProgress?.invoke("Reading tags", tagScanCompleted.incrementAndGet(), rawTracks.size)
                        TrackBuilder(raw, artistName, rawArtistName, genre, imageUrl)
                    }
                }
            }.awaitAll()
        }

        val contentStats = runContentAnalysis(context, builders, onProgress)

        val artistTrackCounts = LinkedHashMap<String, Int>()
        val artistGenres = LinkedHashMap<String, MutableSet<String>>()
        val artistRawNames = LinkedHashMap<String, String>()
        val tracks = mutableListOf<ProfileTrack>()

        builders.forEach { b ->
            artistTrackCounts[b.artistName] = (artistTrackCounts[b.artistName] ?: 0) + 1
            if (b.tagGenre != null) artistGenres.getOrPut(b.artistName) { mutableSetOf() }.add(b.tagGenre!!)
            b.contentGenres.forEach { g -> artistGenres.getOrPut(b.artistName) { mutableSetOf() }.add(g) }
            artistRawNames.putIfAbsent(b.artistName, b.rawArtistName)

            tracks.add(
                ProfileTrack(
                    id = "local_${b.raw.id}",
                    name = b.raw.title,
                    artistNames = listOf(b.artistName),
                    releaseDate = null,
                    popularity = 0,
                    imageUrl = b.imageUrl,
                    genre = b.tagGenre ?: b.contentGenres.firstOrNull()
                )
            )
        }

        val audioDbGenresFoundCount = runAudioDbFallback(artistTrackCounts, artistGenres, onProgress)

        val artists = artistTrackCounts.entries
            .sortedByDescending { it.value }
            .map { (name, _) ->
                val rawName = artistRawNames[name]
                ProfileArtist(
                    id = "local_artist_${name.hashCode()}",
                    name = name,
                    genres = artistGenres[name]?.toList() ?: emptyList(),
                    popularity = 0,
                    imageUrl = artistArtwork[name],
                    // Only kept when normalization actually changed the name — nothing useful to
                    // fall back to otherwise.
                    rawName = rawName?.takeIf { it != name }
                )
            }

        return LocalLibraryScanResult(
            artists = artists,
            tracks = tracks,
            contentAnalyzedCount = contentStats.first,
            audioDbGenresFoundCount = audioDbGenresFoundCount,
            totalTrackCount = rawTracks.size,
            contentAnalysisCapped = contentStats.second,
            playlists = scanPlaylistsSafely(context.contentResolver)
        )
    }

    /** Same unguarded-blocking-call hazard as the MediaStore track query and MediaMetadataRetriever
     *  above — ContentResolver.query() has no suspension points, so a stuck/slow playlist provider
     *  would otherwise hang indefinitely with no timeout at all, right at the very last step of
     *  [scan] (after tag reading and content analysis already succeeded), which would present as
     *  "stuck" just as confusingly as a hang anywhere earlier in the pipeline. */
    private suspend fun scanPlaylistsSafely(resolver: ContentResolver): List<ProfilePlaylist> =
        withTimeoutOrNull(10_000L) {
            withContext(Dispatchers.IO) { scanPlaylists(resolver) }
        } ?: emptyList()

    /** Queries MediaStore.Audio.Playlists/Playlists.Members — the system-wide playlist store. This is
     *  a legacy API: many modern music/player apps no longer write to it at all, keeping playlists in
     *  their own app-private database instead, so it commonly (and validly) comes back empty even on
     *  a device where the user has real playlists in whatever app they actually use. Exposed as a
     *  standalone function (not just inlined into [scan]) so callers can re-check playlist data
     *  without repeating the much more expensive tag/content-analysis scan (see
     *  TwinsterViewModel.loadCachedLocalLibrary). */
    suspend fun scanPlaylists(context: Context): List<ProfilePlaylist> = scanPlaylistsSafely(context.contentResolver)

    private fun scanPlaylists(resolver: ContentResolver): List<ProfilePlaylist> {
        val raw = try {
            queryRawPlaylists(resolver)
        } catch (_: Exception) {
            // Removed/non-functional on this OS version or OEM ROM — same honest "no playlist data"
            // outcome as a genuinely empty result.
            emptyList()
        }
        return raw.map { playlist ->
            ProfilePlaylist(
                id = "local_playlist_${playlist.id}",
                name = playlist.name,
                trackIds = playlist.memberTrackIds.map { "local_$it" }
            )
        }
    }

    private fun queryRawPlaylists(resolver: ContentResolver): List<RawPlaylist> {
        val playlists = mutableListOf<RawPlaylist>()
        @Suppress("DEPRECATION")
        val collection = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val projection = arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME)
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            @Suppress("DEPRECATION")
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
            @Suppress("DEPRECATION")
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            while (cursor.moveToNext()) {
                val playlistId = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)?.trim()?.takeIf { it.isNotBlank() } ?: continue
                playlists.add(RawPlaylist(playlistId, name, queryPlaylistMembers(resolver, playlistId)))
            }
        }
        return playlists
    }

    private fun queryPlaylistMembers(resolver: ContentResolver, playlistId: Long): List<Long> {
        val memberIds = mutableListOf<Long>()
        try {
            @Suppress("DEPRECATION")
            val memberUri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            @Suppress("DEPRECATION")
            val audioIdColName = MediaStore.Audio.Playlists.Members.AUDIO_ID
            resolver.query(memberUri, arrayOf(audioIdColName), null, null, null)?.use { cursor ->
                val audioIdCol = cursor.getColumnIndexOrThrow(audioIdColName)
                while (cursor.moveToNext()) memberIds.add(cursor.getLong(audioIdCol))
            }
        } catch (_: Exception) {
            // This playlist's membership couldn't be read — treat it as empty rather than failing
            // the whole playlist scan.
        }
        return memberIds
    }

    /** Returns (tracksActuallyAnalyzed, wasCapped). Mutates [builders] in place with detected genres.
     *  The actual decode+classify work happens out-of-process in [AudioAnalysisService] (via
     *  [AudioAnalysisClient]) precisely so a native TFLite crash there can't take this scan (or the
     *  app) down with it — every failure mode (remote crash, timeout, explicit failure reply)
     *  degrades to "skip this track's content analysis" rather than aborting the scan; tag-based
     *  data for that track is unaffected. */
    private suspend fun runContentAnalysis(
        context: Context,
        builders: List<TrackBuilder>,
        onProgress: ((stage: String, analyzed: Int, total: Int) -> Unit)?
    ): Pair<Int, Boolean> {
        // Tracks missing a genre tag benefit the most from content detection (pure gap-fill), so
        // they get priority for the analysis cap; any remaining slots go to the most recently
        // added tagged tracks as a supplementary signal.
        val untagged = builders.filter { it.tagGenre == null }
        val tagged = builders.filter { it.tagGenre != null }.sortedByDescending { it.raw.dateAdded }
        val selected = (untagged + tagged).take(MAX_CONTENT_ANALYSIS_TRACKS)
        val capped = builders.size > MAX_CONTENT_ANALYSIS_TRACKS
        if (selected.isEmpty()) return 0 to capped

        val client = AudioAnalysisClient(context.applicationContext)
        try {
            val analyzedCount = AtomicInteger(0)
            val semaphore = Semaphore(CONTENT_ANALYSIS_CONCURRENCY)
            // Fires every selected track's analyzeTrack() concurrently (bounded by the semaphore)
            // rather than awaiting one full round-trip before starting the next — otherwise the
            // service-side concurrency fix above would go to waste, since a sequential client would
            // still only ever have one request in flight regardless of how parallel the service is.
            // Progress is reported per-completion (not per-dispatch), so it reflects tracks actually
            // finished even though they may now complete out of order.
            coroutineScope {
                selected.map { builder ->
                    async {
                        semaphore.withPermit {
                            try {
                                // A null result already covers every failure mode the isolated
                                // service and its IPC bridge can produce (remote crash via
                                // onServiceDisconnected, timeout, explicit failure reply) — see
                                // AudioAnalysisClient's class doc.
                                client.analyzeTrack(builder.raw.contentUri)?.let { genres -> builder.contentGenres = genres }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                // Belt-and-suspenders: AudioAnalysisClient already collapses its own
                                // failure modes to null rather than throwing, but any unexpected
                                // client-side exception still must not abort the rest of the scan.
                            }
                            onProgress?.invoke("Analyzing", analyzedCount.incrementAndGet(), selected.size)
                        }
                    }
                }.awaitAll()
            }
            return analyzedCount.get() to capped
        } finally {
            client.close()
        }
    }

    /** Third and last-resort genre tier — see TheAudioDbGenreService's class doc. Only queries
     *  TheAudioDb for artists that still have ZERO genre signal after the tag+content-analysis pass
     *  above (calling it for every artist would be both slow, given the shared key's 30 req/min rate
     *  limit, and pointless for artists that already have a genre). Mutates [artistGenres] in place
     *  with any found tag, merged the same way tag/content genres are; returns how many artists
     *  actually got a genre this way, for the caller's genre-basis-note honesty. */
    private suspend fun runAudioDbFallback(
        artistTrackCounts: Map<String, Int>,
        artistGenres: MutableMap<String, MutableSet<String>>,
        onProgress: ((stage: String, analyzed: Int, total: Int) -> Unit)?
    ): Int {
        // Most-played artists first — if the cap can't cover every genre-less artist in a large
        // library, prioritize the ones that actually make up most of the listener's music.
        val genreless = artistTrackCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { artistGenres[it].isNullOrEmpty() }
        if (genreless.isEmpty()) return 0

        val found = try {
            withContext(Dispatchers.IO) {
                TheAudioDbGenreService.genresForArtists(genreless, maxArtists = MAX_AUDIODB_LOOKUPS) { completed, total ->
                    onProgress?.invoke("Looking up genres online", completed, total)
                }
            }
        } catch (e: Exception) {
            // TheAudioDbGenreService is already best-effort/never-throws internally — this is
            // belt-and-suspenders so a genuinely unexpected failure here still can't abort the scan.
            emptyMap()
        }
        found.forEach { (name, genre) -> artistGenres.getOrPut(name) { mutableSetOf() }.add(genre) }
        return found.size
    }

    private fun queryMediaStore(resolver: ContentResolver): List<RawTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val results = mutableListOf<RawTrack>()

        val cursor = try {
            resolver.query(collection, projection, selection, null, null)
        } catch (_: Exception) {
            // GENRE is a synthetic column only guaranteed from API 30 — retry the query without it
            // for older OS versions/OEM ROMs that reject the projection outright.
            resolver.query(
                collection,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATE_ADDED
                ),
                selection,
                null,
                null
            )
        }

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val genreCol = it.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val dateAddedCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                results.add(
                    RawTrack(
                        id = id,
                        title = it.getString(titleCol) ?: "Unknown Track",
                        artist = it.getString(artistCol) ?: "Unknown Artist",
                        genre = if (genreCol >= 0) it.getString(genreCol) else null,
                        dateAdded = if (dateAddedCol >= 0) it.getLong(dateAddedCol) else 0L,
                        contentUri = ContentUris.withAppendedId(collection, id)
                    )
                )
            }
        }
        return results
    }
}

/** Private cache dir for embedded album art pulled via MediaMetadataRetriever.getEmbeddedPicture() —
 *  Coil's file:// support loads these directly, same as any other ArtworkThumb URL. */
private object LocalArtworkCache {
    fun write(context: Context, trackId: Long, bytes: ByteArray): String? = try {
        val dir = File(context.cacheDir, "local_artwork").apply { mkdirs() }
        val file = File(dir, "$trackId.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        Uri.fromFile(file).toString()
    } catch (_: Exception) {
        null
    }
}
