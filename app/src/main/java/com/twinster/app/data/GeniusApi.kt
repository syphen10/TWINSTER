package com.twinster.app.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** Genius's free public search API — used only as a secondary "how mainstream is this artist on the
 *  internet" signal for obscurity scoring (see GeniusObscurityService). Requires a free Client Access
 *  Token from genius.com/api-clients; when unset, GeniusObscurityService no-ops entirely. */
interface GeniusApi {
    @GET("search")
    suspend fun search(
        @Header("Authorization") bearerToken: String,
        @Query("q") query: String
    ): GeniusSearchResponse
}

@Serializable
data class GeniusSearchResponse(val response: GeniusSearchResult? = null)

@Serializable
data class GeniusSearchResult(val hits: List<GeniusHit> = emptyList())

@Serializable
data class GeniusHit(val result: GeniusSong? = null)

@Serializable
data class GeniusSong(
    val stats: GeniusStats? = null,
    val annotation_count: Int? = null
)

@Serializable
data class GeniusStats(val pageviews: Long? = null)
