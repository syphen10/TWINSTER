package com.twinster.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.twinster.app.data.DemoDataProvider
import com.twinster.app.data.GeniusObscurityService
import com.twinster.app.data.LocalLibraryScanner
import com.twinster.app.data.SecureStore
import com.twinster.app.data.SettingsStore
import com.twinster.app.domain.LlmArchetypeService
import com.twinster.app.domain.MusicAnalyzer
import com.twinster.app.domain.MusicProfile
import com.twinster.app.domain.ObscurityAnalyzer
import com.twinster.app.domain.ProfileArtist
import com.twinster.app.domain.ProfilePlaylist
import com.twinster.app.domain.ProfileSource
import com.twinster.app.domain.ProfileTrack
import com.twinster.app.twin.CompatibilityCalculator
import com.twinster.app.twin.CompatibilityResult
import com.twinster.app.twin.TastePayloadCodec
import com.twinster.app.util.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TwinsterViewModel(app: Application) : AndroidViewModel(app) {

    val secureStore = SecureStore(app)
    val settingsStore = SettingsStore(app)

    private val _profileState = MutableStateFlow<UiState<MusicProfile>>(UiState.Idle)
    val profileState: StateFlow<UiState<MusicProfile>> = _profileState.asStateFlow()

    private val _sharpenedArchetype = MutableStateFlow<String?>(null)
    val sharpenedArchetype: StateFlow<String?> = _sharpenedArchetype.asStateFlow()

    private val _twinResultState = MutableStateFlow<UiState<Pair<CompatibilityResult, String>>>(UiState.Idle)
    val twinResultState: StateFlow<UiState<Pair<CompatibilityResult, String>>> = _twinResultState.asStateFlow()

    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private val _localLibraryState = MutableStateFlow<UiState<MusicProfile>>(UiState.Idle)
    val localLibraryState: StateFlow<UiState<MusicProfile>> = _localLibraryState.asStateFlow()

    // Human-readable progress text for the (now slower, since it runs on-device audio analysis)
    // Local Library scan — StateFlow.value is safe to set from any dispatcher, so the scan's
    // background work can report progress directly without hopping back to Main.
    private val _localLibraryScanProgress = MutableStateFlow<String?>(null)
    val localLibraryScanProgress: StateFlow<String?> = _localLibraryScanProgress.asStateFlow()

    fun onTasteTwinLink(uri: Uri) {
        val payload = uri.getQueryParameter("d") ?: return
        compareWithPayload(payload)
    }

    /** Loads a hardcoded, realistic profile so every screen is browsable without scanning a real
     *  local library — see DemoDataProvider. */
    fun loadDemoProfile() {
        viewModelScope.launch {
            _profileState.value = UiState.Loading
            _sharpenedArchetype.value = null
            _displayName.value = "You (Demo)"
            val musicProfile = DemoDataProvider.demoProfile()
            _profileState.value = UiState.Success(musicProfile)
            maybeSharpenArchetype(musicProfile)
        }
    }

    /** Compares the current profile against a second synthetic profile so Taste Twin is demoable
     *  without a second real device. */
    fun compareWithDemoFriend() {
        viewModelScope.launch {
            _twinResultState.value = UiState.Loading
            val myProfile = (_profileState.value as? UiState.Success)?.data
                ?: (_localLibraryState.value as? UiState.Success)?.data
            if (myProfile == null) {
                _twinResultState.value = UiState.Error("Load your Music Personality first to compare with the demo friend.")
                return@launch
            }
            val mine = TastePayloadCodec.fromProfile(_displayName.value, myProfile)
            val theirs = TastePayloadCodec.fromProfile(DemoDataProvider.DEMO_FRIEND_NAME, DemoDataProvider.demoFriendProfile())
            val result = CompatibilityCalculator.compare(mine, theirs)
            val verdict = CompatibilityCalculator.verdict(result, theirs.n)
            _twinResultState.value = UiState.Success(result to verdict)
        }
    }

    /** Scans on-device audio via MediaStore (see LocalLibraryScanner) — caller is responsible for
     *  the runtime permission prompt (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE) before calling this.
     *  Persists the raw scan so it can be reopened later via [loadCachedLocalLibrary] without rescanning. */
    fun scanLocalLibrary() {
        viewModelScope.launch {
            _localLibraryState.value = UiState.Loading
            _localLibraryScanProgress.value = "Reading your local music library…"
            try {
                val result = withContext(Dispatchers.IO) {
                    LocalLibraryScanner.scan(getApplication()) { stage, analyzed, total ->
                        _localLibraryScanProgress.value = "$stage: $analyzed of $total tracks…"
                    }
                }
                if (result.tracks.isEmpty()) {
                    _localLibraryState.value = UiState.Error("No local audio files found on this device.")
                    return@launch
                }
                settingsStore.saveLocalLibrarySnapshot(result.artists, result.tracks)
                val musicProfile = analyzeLocalLibrary(
                    result.artists, result.tracks, result.contentAnalyzedCount, result.contentAnalysisCapped,
                    result.playlists, result.audioDbGenresFoundCount
                )
                _localLibraryState.value = UiState.Success(musicProfile)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Last line of defense for the whole scan: per-track/per-frame failures inside
                // LocalLibraryScanner/YamnetClassifier/AudioContentDecoder are already caught closer
                // to their source, but this catches Throwable (not just Exception) as a safety net so
                // nothing from the scan pipeline can ever escape this coroutine and crash the app.
                _localLibraryState.value = UiState.Error(e.message ?: "Couldn't scan your local music library.")
            } finally {
                _localLibraryScanProgress.value = null
            }
        }
    }

    /** Loads the last completed scan straight from the cache — no re-scan, no MediaStore permission
     *  needed, no detour through Connect. Used by the "My Library" entry point. */
    fun loadCachedLocalLibrary() {
        viewModelScope.launch {
            _localLibraryState.value = UiState.Loading
            val snapshot = settingsStore.loadLocalLibrarySnapshot()
            if (snapshot == null) {
                _localLibraryState.value = UiState.Error("No local library scan found yet — run a scan first.")
                return@launch
            }
            val musicProfile = analyzeLocalLibrary(snapshot.artists, snapshot.tracks)
            _localLibraryState.value = UiState.Success(musicProfile)
        }
    }

    /** Local files carry no first-party popularity of their own, so every obscurity category
     *  (Songs/Artists/Genres/Playlists) is estimated from Genius mainstream-footprint signals (see
     *  GeniusObscurityService — a no-op if no access token is configured) plus, for Playlists, real
     *  on-device MediaStore playlist data when it exists. Any category with no real signal to work
     *  from is honestly reported unavailable rather than defaulting to a meaningless number — see
     *  ObscurityAnalyzer.fromGeniusSignals. */
    private suspend fun analyzeLocalLibrary(
        artists: List<ProfileArtist>,
        tracks: List<ProfileTrack>,
        contentAnalyzedCount: Int = 0,
        contentAnalysisCapped: Boolean = false,
        playlists: List<ProfilePlaylist> = emptyList(),
        audioDbGenresFoundCount: Int = 0
    ): MusicProfile {
        val (artistScores, trackScores, resolvedPlaylists) = withContext(Dispatchers.IO) {
            val artistScores = try {
                GeniusObscurityService.mainstreamScores(artists.map { it.name })
            } catch (e: Exception) {
                emptyMap()
            }
            val trackScores = try {
                GeniusObscurityService.trackMainstreamScores(tracks)
            } catch (e: Exception) {
                emptyMap()
            }
            // Cached reloads (loadCachedLocalLibrary) don't carry a fresh scan's playlists — a
            // MediaStore playlist query is cheap (no audio decode), so just re-run it rather than
            // caching playlist membership alongside the artist/track snapshot.
            val playlistsToUse = playlists.ifEmpty {
                try {
                    LocalLibraryScanner.scanPlaylists(getApplication())
                } catch (e: Exception) {
                    emptyList()
                }
            }
            Triple(artistScores, trackScores, playlistsToUse)
        }

        val obscurity = ObscurityAnalyzer.fromGeniusSignals(
            artists = artists,
            tracks = tracks,
            artistMainstreamScores = artistScores,
            trackMainstreamScores = trackScores,
            playlists = resolvedPlaylists
        )

        // Honest basis note covering whichever of the three genre-detection tiers actually
        // contributed (file tags are the unstated default and never mentioned here — see
        // MusicAnalyzer/GenreCard's existing convention of only calling out the non-obvious sources).
        val genreNoteParts = mutableListOf<String>()
        if (contentAnalyzedCount > 0) {
            genreNoteParts += if (contentAnalysisCapped) {
                "on-device audio analysis of a $contentAnalyzedCount-track sample"
            } else {
                "on-device audio analysis of $contentAnalyzedCount tracks"
            }
        }
        if (audioDbGenresFoundCount > 0) {
            genreNoteParts += "an internet lookup for $audioDbGenresFoundCount artist(s) with no other genre signal"
        }
        val genreBasisNote = if (genreNoteParts.isEmpty()) null else {
            "Includes " + genreNoteParts.joinToString(" and ") + " to fill in genre gaps."
        }

        return MusicAnalyzer.analyze(
            artists = artists,
            tracks = tracks,
            source = ProfileSource.LOCAL_LIBRARY,
            obscurity = obscurity,
            genreBasisNote = genreBasisNote
        )
    }

    val hasLocalLibraryScan: Boolean get() = _localLibraryState.value is UiState.Success
    val hasCachedLocalLibrary: Flow<Boolean> get() = settingsStore.hasCachedLocalLibrary

    private fun maybeSharpenArchetype(profile: MusicProfile) {
        val apiKey = secureStore.byokApiKey ?: return
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            val sharpened = LlmArchetypeService.sharpenArchetype(apiKey, profile.archetypeTitle, profile)
            if (sharpened != null) _sharpenedArchetype.value = sharpened
        }
    }

    fun buildTasteTwinLink(): String? {
        val profile = (_profileState.value as? UiState.Success)?.data
            ?: (_localLibraryState.value as? UiState.Success)?.data
            ?: return null
        val payload = TastePayloadCodec.fromProfile(_displayName.value, profile)
        return TastePayloadCodec.buildDeepLink(payload)
    }

    fun compareWithPayload(encodedPayload: String) {
        viewModelScope.launch {
            _twinResultState.value = UiState.Loading
            val theirs = TastePayloadCodec.decode(encodedPayload)
            val myProfile = (_profileState.value as? UiState.Success)?.data
                ?: (_localLibraryState.value as? UiState.Success)?.data
            if (theirs == null || myProfile == null) {
                _twinResultState.value = UiState.Error("Couldn't read this Taste Twin link. Make sure your own Twinster profile has loaded first.")
                return@launch
            }
            val mine = TastePayloadCodec.fromProfile(_displayName.value, myProfile)
            val result = CompatibilityCalculator.compare(mine, theirs)
            var verdict = CompatibilityCalculator.verdict(result, theirs.n)
            val apiKey = secureStore.byokApiKey
            if (!apiKey.isNullOrBlank()) {
                val sharpened = LlmArchetypeService.sharpenArchetype(apiKey, verdict, myProfile)
                if (sharpened != null) verdict = sharpened
            }
            _twinResultState.value = UiState.Success(result to verdict)
        }
    }

    fun saveByokKey(key: String?) {
        secureStore.byokApiKey = key?.takeIf { it.isNotBlank() }
    }

    fun currentByokKey(): String? = secureStore.byokApiKey

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TwinsterViewModel(app) as T
    }
}
