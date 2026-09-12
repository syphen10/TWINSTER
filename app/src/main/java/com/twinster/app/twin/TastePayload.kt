package com.twinster.app.twin

import com.twinster.app.domain.MusicProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable
data class GenrePctDto(val g: String, val p: Int)

@Serializable
data class TastePayload(
    val n: String? = null,
    val genres: List<GenrePctDto>,
    val artists: List<String>,
    val energy: Float,
    val valence: Float,
    val obscurity: Int
)

object TastePayloadCodec {

    private val json = Json { encodeDefaults = true }

    fun fromProfile(displayName: String?, profile: MusicProfile): TastePayload = TastePayload(
        n = displayName,
        genres = profile.genreBreakdown.take(5).map { GenrePctDto(it.genre, it.percent) },
        artists = profile.topArtistNames,
        energy = profile.energyScore,
        valence = profile.valenceScore,
        // Blended figure across whichever of Songs/Artists/Genres/Playlists are actually available —
        // Taste Twin compatibility scoring only wants one overall obscurity number, not the full
        // per-category breakdown. See MusicProfile.overallObscurityScore/ObscurityBreakdown.
        obscurity = profile.overallObscurityScore
    )

    fun encode(payload: TastePayload): String {
        val jsonText = json.encodeToString(payload)
        val gzipped = gzip(jsonText.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(gzipped)
    }

    fun decode(encoded: String): TastePayload? = try {
        val gzipped = Base64.getUrlDecoder().decode(encoded)
        val jsonText = ungzip(gzipped).toString(Charsets.UTF_8)
        json.decodeFromString(TastePayload.serializer(), jsonText)
    } catch (e: Exception) {
        null
    }

    fun buildDeepLink(payload: TastePayload): String = "twinster://compare?d=${encode(payload)}"

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun ungzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }
}
