package com.twinster.app.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** TheAudioDB's public API (theaudiodb.com/api/v1/json/{key}/) — used as the third and last-resort
 *  genre signal for Local Library artists with no file-tag or on-device content-analysis genre at
 *  all (see TheAudioDbGenreService). Unlike Genius's search API (which has no genre field
 *  whatsoever), an artist record here carries `strGenre`/`strStyle` fields directly — a single call,
 *  no separate lookup step needed. [apiKey] defaults to TheAudioDb's own documented shared "123"
 *  test key (see BuildConfig.THEAUDIODB_API_KEY_OBF / SETUP.md) — fine for development, but a real
 *  Patreon-tier personal key is recommended before any wide public release for reliability/rate
 *  limits, same as this project's other optional keys. */
interface TheAudioDbApi {
    @GET("{apiKey}/search.php")
    suspend fun searchArtist(
        @Path("apiKey") apiKey: String,
        @Query("s") artistName: String
    ): TheAudioDbSearchResponse
}

@Serializable
data class TheAudioDbSearchResponse(val artists: List<TheAudioDbArtist>? = null)

@Serializable
data class TheAudioDbArtist(
    val strArtist: String? = null,
    val strGenre: String? = null,
    val strStyle: String? = null,
    val strMood: String? = null
)
