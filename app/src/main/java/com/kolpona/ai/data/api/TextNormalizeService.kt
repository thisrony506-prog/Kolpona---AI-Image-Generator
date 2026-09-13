package com.kolpona.ai.data.api

import com.kolpona.ai.prompt.CulturalTerms
import com.kolpona.ai.prompt.DialectLexicon
import com.kolpona.ai.prompt.LanguageScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Language + dialect understanding. Never shown in the UI.
 * Local lexicon first; model polish when the text still needs it.
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
        chatCompletions(original, rewritten)?.let { return it }
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
            .header("Accept", "text/plain,application/json")
            .header("User-Agent", PollinationsConfig.USER_AGENT)
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

    private fun chatCompletions(original: String, rewritten: String): String? {
        val json = """{"model":"openai","private":true,"messages":[""" +
            """{"role":"system","content":"${escape(SYSTEM_RULES)}"},""" +
            """{"role":"user","content":"${escape("Original:\n${original.take(700)}\n\nDraft:\n${rewritten.take(700)}")}"}""" +
            """]}"""
        val media = "application/json; charset=utf-8".toMediaType()
        val urls = listOf(
            "https://gen.pollinations.ai/v1/chat/completions",
            "https://text.pollinations.ai/openai"
        )
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody(media))
                .header("Accept", "application/json,text/plain")
                .header("User-Agent", PollinationsConfig.USER_AGENT)
                .build()
            val parsed = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parseCompletion(response.body?.string().orEmpty())
                }
            } catch (_: Exception) {
                null
            }
            if (!parsed.isNullOrBlank()) return parsed
        }
        return null
    }

    private fun parseCompletion(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        runCatching {
            val json = JSONObject(text)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            sanitizeModelOutput(content)?.let { return it }
        }
        return sanitizeModelOutput(text)
    }

    private fun fallbackGet(instruction: String): String? {
        val encoded = java.net.URLEncoder.encode(instruction.take(1100), Charsets.UTF_8.name())
            .replace("+", "%20")
        val request = Request.Builder()
            .url("https://text.pollinations.ai/$encoded?model=openai")
            .get()
            .header("Accept", "text/plain")
            .header("User-Agent", PollinationsConfig.USER_AGENT)
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
            "Rewrite the user's image or video request as ONE English generation prompt. " +
                "Keep every subject, count, gender, age, clothing, color, place, culture, action, camera move, mood, and time of day. " +
                "Keep South Asian clothing names (sari, lungi, panjabi, salwar kameez) and place names (Khulna, Dhaka, Bangladesh, Bengal). " +
                "Do not replace a Bangladesh village with a Western countryside. " +
                "Do not translate text that must appear in the image; keep that wording in quotes. " +
                "Do not add a new subject, clothing, color, or motion the user did not ask for. " +
                "Do not change red to black, night to day, sitting to walking, or region. " +
                "If the request is already clear English, return it unchanged. " +
                "Reply with only the prompt."
    }
}

object LanguageDetector {
    fun needsEnglish(text: String): Boolean =
        LanguageScripts.nonLatinRatio(text) >= 0.25f || LanguageScripts.isMixed(text)
}
