package com.kolpona.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Uses the public Pollinations text endpoint to translate a prompt to English
 * when the generation model works better with English. Never shown in the UI.
 */
class TextNormalizeService(
    private val client: OkHttpClient
) {
    suspend fun toEnglishIfNeeded(prompt: String): String = withContext(Dispatchers.IO) {
        if (!LanguageDetector.needsEnglish(prompt)) return@withContext prompt
        val instruction =
            "Translate to English for an AI image/video prompt. " +
                "Keep the same meaning. Reply with only the English prompt, no quotes:\n\n$prompt"
        val encoded = URLEncoder.encode(instruction.take(1200), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val url = "https://text.pollinations.ai/$encoded"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("model", "openai")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/plain")
            .header("User-Agent", "Kolpona/1.3.0 (Android)")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext prompt
                val text = response.body?.string()?.trim().orEmpty()
                if (text.isBlank() || text.startsWith("<") || text.length > 2000) prompt
                else text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: prompt
            }
        } catch (_: Exception) {
            prompt
        }
    }
}

object LanguageDetector {
    fun needsEnglish(text: String): Boolean {
        if (text.isBlank()) return false
        var latin = 0
        var other = 0
        text.forEach { c ->
            when (c.code) {
                in 0x0980..0x09FF, // Bengali
                in 0x0900..0x097F, // Devanagari
                in 0x0600..0x06FF, // Arabic
                in 0x0B80..0x0BFF, // Tamil
                in 0x0C00..0x0C7F, // Telugu
                in 0x0400..0x04FF, // Cyrillic
                in 0x4E00..0x9FFF, // CJK
                in 0x3040..0x30FF, // Hiragana/Katakana
                in 0xAC00..0xD7AF, // Hangul
                in 0x0E00..0x0E7F, // Thai
                in 0x10A0..0x10FF -> other++ // Georgian
                in 0x0041..0x007A, in 0x00C0..0x024F -> latin++
            }
        }
        val total = latin + other
        if (total == 0) return false
        return other * 100 / total >= 25
    }
}
