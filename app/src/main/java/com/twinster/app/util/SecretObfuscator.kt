package com.twinster.app.util

import android.util.Base64

/**
 * Decodes the XOR+Base64 obfuscated BuildConfig values (see the `obfuscate()` helper in
 * app/build.gradle.kts, which encodes them at build time). This is a deterrent against a casual
 * `strings`/apktool scan of the compiled APK turning up a plain-text API token, not real secrecy —
 * an app-embedded value can always be recovered by a determined reverse engineer, since the app
 * itself must be able to decode it too. [KEY] must match the one in build.gradle.kts exactly.
 */
object SecretObfuscator {
    private const val KEY = "Twinster-2026-ObfKey"

    fun decode(obfuscated: String): String {
        val xored = Base64.decode(obfuscated, Base64.NO_WRAP)
        val keyBytes = KEY.toByteArray(Charsets.UTF_8)
        val bytes = ByteArray(xored.size) { i -> (xored[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
        return String(bytes, Charsets.UTF_8)
    }
}
