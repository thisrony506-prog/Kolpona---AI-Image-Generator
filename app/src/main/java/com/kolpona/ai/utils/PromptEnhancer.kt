package com.kolpona.ai.utils

import com.kolpona.ai.data.api.TextNormalizeService
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.prompt.LanguageScripts
import com.kolpona.ai.prompt.OptimizedGeneration
import com.kolpona.ai.prompt.PromptTemplates
import com.kolpona.ai.prompt.SceneIntent
import com.kolpona.ai.prompt.SceneKind

/**
 * Understand the user first, expand short clothing requests, then apply master quality.
 * Original chat text is never replaced.
 */
class PromptEnhancer(
    private val normalizer: TextNormalizeService? = null
) {
    suspend fun optimize(
        prompt: String,
        style: ImageStyle,
        enabled: Boolean,
        mediaType: MediaKind
    ): OptimizedGeneration {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val englishEnough = LanguageScripts.looksLikeEnglish(cleaned) &&
            LanguageScripts.nonLatinRatio(cleaned) < 0.08f
        val understood = if (englishEnough) {
            cleaned
        } else {
            try {
                normalizer?.understand(cleaned) ?: cleaned
            } catch (_: Exception) {
                cleaned
            }
        }
        val intent = SceneIntent.extract(cleaned, understood)
        val withText = preserveRequestedText(understood, intent)
        val subject = expandSubject(withText, intent, mediaType)
        val body = PromptTemplates.apply(subject, intent.kind, style, mediaType)
        return OptimizedGeneration(body, PromptTemplates.negative(intent.kind, style), intent.kind)
    }

    fun enhance(
        prompt: String,
        style: ImageStyle,
        @Suppress("UNUSED_PARAMETER") enabled: Boolean,
        mediaType: MediaKind = MediaKind.IMAGE
    ): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val intent = SceneIntent.extract(cleaned, cleaned)
        val subject = expandSubject(cleaned, intent, mediaType)
        return PromptTemplates.apply(subject, intent.kind, style, mediaType)
    }

    private fun expandSubject(prompt: String, intent: SceneIntent, media: MediaKind): String {
        val clothing = intent.kind == SceneKind.FASHION || PromptTemplates.looksLikeClothing(prompt)
        if (!clothing) return prompt
        val words = prompt.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 4) return prompt
        val lower = prompt.lowercase()
        val alreadyHasSubject = SUBJECT_WORDS.any { lower.contains(it) }
        if (alreadyHasSubject) return prompt
        return if (media == MediaKind.VIDEO) {
            "a model wearing elegant $prompt, full body shot, fashion model walking on runway"
        } else {
            "a fashion model wearing $prompt, full body shot"
        }
    }

    private fun preserveRequestedText(prompt: String, intent: SceneIntent): String {
        if (intent.quotedText.isEmpty()) return prompt
        val clause = intent.quotedText.joinToString("; ") { "\"$it\"" }
        if (prompt.contains(clause)) return prompt
        return "$prompt, the text in the image must appear exactly as $clause"
    }

    companion object {
        private val SUBJECT_WORDS = listOf(
            "model", "wearing", "woman", "man", "girl", "boy", "person", "people",
            "walk", "runway", "catwalk", "মাইয়া", "মেয়ে", "ছেলে"
        )
    }
}
