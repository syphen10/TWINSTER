package com.twinster.app.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object NetworkModule {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    val geniusApi: GeniusApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.genius.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeniusApi::class.java)
    }

    // TheAudioDb documents no special header requirement (unlike MusicBrainz's mandatory
    // User-Agent) — the shared okHttpClient above is fine here.
    val theAudioDbApi: TheAudioDbApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.theaudiodb.com/api/v1/json/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TheAudioDbApi::class.java)
    }
}
