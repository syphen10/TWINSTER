package com.twinster.app.domain

import com.twinster.app.data.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request

object LlmArchetypeService {

    suspend fun sharpenArchetype(
        apiKey: String,
        ruleBasedTitle: String,
        profile: MusicProfile
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                append("In one short punchy sentence (max 20 words), write a witty music-personality tagline for someone ")
                append("whose rule-based archetype is '$ruleBasedTitle'. Their top genre is ${profile.topGenre}, ")
                append("mood is ${profile.moodSummary}, obscurity score is ${profile.overallObscurityScore}/100. ")
                append("Return only the tagline text, no quotes.")
            }
            val bodyJson = JsonObject(
                mapOf(
                    "model" to JsonPrimitive("claude-haiku-4-5"),
                    "max_tokens" to JsonPrimitive(60),
                    "messages" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "role" to JsonPrimitive("user"),
                                    "content" to JsonPrimitive(prompt)
                                )
                            )
                        )
                    )
                )
            )
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            val text = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null
            val json = NetworkModule.json.parseToJsonElement(text).jsonObject
            val contentArray = json["content"]?.jsonArray ?: return@withContext null
            contentArray.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.trim()
        } catch (e: Exception) {
            null
        }
    }
}
