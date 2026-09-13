package com.kolpona.app.utils

import com.kolpona.app.data.api.TextNormalizeService
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.domain.model.MediaKind

/**
 * Internal prompt optimizer. Translates when needed, then adds only relevant
 * visual or motion detail. The original user text stays in the chat.
 */
class PromptEnhancer(
    private val normalizer: TextNormalizeService? = null
) {
    suspend fun optimize(
        prompt: String,
        style: ImageStyle,
        enabled: Boolean,
        mediaType: MediaKind
    ): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val english = try {
            normalizer?.toEnglishIfNeeded(cleaned) ?: cleaned
        } catch (_: Exception) {
            cleaned
        }
        if (!enabled) {
            return if (mediaType == MediaKind.VIDEO) english + motionHint(english) else english
        }
        return if (mediaType == MediaKind.VIDEO) {
            enhanceVideo(english, style)
        } else {
            enhanceImage(english, style)
        }
    }

    fun enhance(
        prompt: String,
        style: ImageStyle,
        enabled: Boolean,
        mediaType: MediaKind = MediaKind.IMAGE
    ): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        if (!enabled) {
            return if (mediaType == MediaKind.VIDEO) cleaned + motionHint(cleaned) else cleaned
        }
        return if (mediaType == MediaKind.VIDEO) enhanceVideo(cleaned, style)
        else enhanceImage(cleaned, style)
    }

    private fun enhanceImage(prompt: String, style: ImageStyle): String {
        val lower = prompt.lowercase()
        val extras = buildList {
            if (style != ImageStyle.ALL) add(style.promptSuffix)
            if (looksLikeProduct(lower) || style == ImageStyle.PRODUCT || style == ImageStyle.FASHION) {
                add("professional product photography, studio lighting, realistic fabric or material texture, sharp details")
            } else if (style == ImageStyle.ANIME) {
                add("clean lineart, vibrant color")
            } else {
                if (!lower.contains("light")) add("cinematic lighting")
                if (!lower.contains("detail")) add("highly detailed")
                if (!lower.contains("real") && style != ImageStyle.ABSTRACT) add("realistic textures")
            }
        }.distinct()
        return "$prompt, ${extras.joinToString(", ")}"
    }

    private fun enhanceVideo(prompt: String, style: ImageStyle): String {
        val motion = motionHint(prompt)
        val look = if (style == ImageStyle.ALL) "cinematic cinematography" else style.promptSuffix
        return "$prompt, $look$motion"
    }

    private fun motionHint(prompt: String): String {
        val lower = prompt.lowercase()
        val specific = when {
            containsAny(lower, "waterfall", "জলপ্রপাত", "water") ->
                " waterfall flowing continuously, realistic water motion, mist drifting"
            containsAny(lower, "cat", "বিড়াল", "kitten") ->
                " walking slowly, natural body movement, realistic fur movement"
            containsAny(lower, "cloud", "mountain", "মেঘ", "পাহাড়") ->
                " moving clouds, slow drone shot"
            containsAny(lower, "bird", "fly", "dragon") ->
                " flying, gentle camera tracking"
            containsAny(lower, "car", "vehicle", "train") ->
                " moving forward, tracking shot"
            MOTION_WORDS.any { lower.contains(it) } -> ""
            else -> " natural movement"
        }
        return "$specific, smooth cinematic camera movement, realistic motion, 5 second video"
    }

    private fun looksLikeProduct(lower: String): Boolean =
        containsAny(lower, "pant", "shirt", "shoe", "watch", "bag", "product", "fabric")

    private fun containsAny(text: String, vararg words: String): Boolean =
        words.any { text.contains(it) }

    companion object {
        private val MOTION_WORDS = listOf(
            "walk", "run", "mov", "flow", "fly", "rotat", "drift", "drone",
            "zoom", "camera", "panning", "orbit", "sway"
        )
    }
}
