package com.kolpona.app.data.api

import com.kolpona.app.prompt.CulturalTerms
import com.kolpona.app.prompt.DialectLexicon
import com.kolpona.app.prompt.LanguageScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Language + dialect understanding. Never shown in the UI.
 * Local lexicon first; model polish only when the text still needs it.
 */
class TextNormalizeService(
    private val client: OkHttpClient
) {
    suspend fun understand(prompt: String): String = withContext(Dispatchers.IO) {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val rewritten = DialectLexicon.rewrite(cleaned)
        val polished = if (DialectLexicon.stillNeedsModel(cleaned, rewritten)) {
            polishWithModel(cleaned, rewritten) ?: rewritten
        } else {
            rewritten.ifBlank { cleaned }
        }
        CulturalTerms.ensure(polished.ifBlank { cleaned }, cleaned, rewritten)
    }

    private fun polishWithModel(original: String, rewritten: String): String? {
        val instruction = SYSTEM_RULES +
            "\nOriginal:\n${original.take(800)}\n\nDraft:\n${rewritten.take(800)}"
        val json = """{"model":"openai","private":true,"messages":[""" +
            """{"role":"system","content":"${escape(SYSTEM_RULES)}"},""" +
            """{"role":"user","content":"${escape("Original: ${original.take(700)}\nDraft: ${rewritten.take(700)}")}"}""" +
            """]}"""
        val media = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("https://text.pollinations.ai/")
            .post(json.toRequestBody(media))
            .header("Accept", "text/plain")
            .header("User-Agent", "Kolpona/1.4.0 (Android)")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use fallbackGet(instruction)
                val text = response.body?.string()?.trim().orEmpty()
                sanitizeModelOutput(text) ?: fallbackGet(instruction)
            }
        } catch (_: Exception) {
            fallbackGet(instruction)
        }
    }

    private fun fallbackGet(instruction: String): String? {
        val encoded = java.net.URLEncoder.encode(instruction.take(1100), Charsets.UTF_8.name())
            .replace("+", "%20")
        val request = Request.Builder()
            .url("https://text.pollinations.ai/$encoded?model=openai")
            .get()
            .header("Accept", "text/plain")
            .header("User-Agent", "Kolpona/1.4.0 (Android)")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                sanitizeModelOutput(response.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeModelOutput(raw: String): String? {
        val text = raw.trim().trim('"')
        if (text.isBlank() || text.startsWith("<") || text.length > 1800) return null
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (line.startsWith("{") || line.contains("\"error\"")) return null
        return line
    }

    private fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    companion object {
        private const val SYSTEM_RULES =
            "Convert the user's image/video request into ONE English generation prompt. " +
                "Keep subject, gender, age, clothing, colors, location, action, time, mood, and culture. " +
                "Keep South Asian clothing names (sari, lungi, panjabi, salwar kameez) and place names " +
                "(Khulna, Dhaka, Bangladesh, Bengal). Do not replace a Bangladesh village with a Western countryside. " +
                "Do not translate text that must appear in the image; keep that wording in quotes. " +
                "Do not add a new subject. Do not change colors, clothing, night/day, or region. " +
                "Reply with only the prompt."
    }
}

object LanguageDetector {
    fun needsEnglish(text: String): Boolean =
        LanguageScripts.nonLatinRatio(text) >= 0.25f || LanguageScripts.isMixed(text)
}
