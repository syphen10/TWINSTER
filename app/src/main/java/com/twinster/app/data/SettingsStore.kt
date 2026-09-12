package com.twinster.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twinster.app.domain.ProfileArtist
import com.twinster.app.domain.ProfileTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "twinster_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val LOCAL_LIBRARY_SNAPSHOT = stringPreferencesKey("local_library_snapshot")
    }

    /** True once a Local Library scan has been cached — lets the UI show a "My Library" entry point
     *  without needing to load the (possibly large) snapshot itself. */
    val hasCachedLocalLibrary: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LOCAL_LIBRARY_SNAPSHOT] != null }

    /** Persists the raw scan output (not the analyzed MusicProfile) so a fresh MusicAnalyzer.analyze()
     *  run on load always reflects the current analyzer logic. Overwrites any previous scan. */
    suspend fun saveLocalLibrarySnapshot(artists: List<ProfileArtist>, tracks: List<ProfileTrack>) {
        val snapshot = LocalLibrarySnapshot(artists, tracks, System.currentTimeMillis())
        val json = Json.encodeToString(LocalLibrarySnapshot.serializer(), snapshot)
        context.dataStore.edit { it[Keys.LOCAL_LIBRARY_SNAPSHOT] = json }
    }

    /** Null when no scan has ever completed, or the cached JSON can't be parsed (e.g. after an
     *  incompatible app update) — callers should treat that the same as "never scanned". */
    suspend fun loadLocalLibrarySnapshot(): LocalLibrarySnapshot? {
        val json = context.dataStore.data.map { it[Keys.LOCAL_LIBRARY_SNAPSHOT] }.first() ?: return null
        return try {
            Json.decodeFromString(LocalLibrarySnapshot.serializer(), json)
        } catch (e: Exception) {
            null
        }
    }
}
