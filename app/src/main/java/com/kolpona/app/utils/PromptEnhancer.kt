package com.kolpona.app.utils

import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.domain.model.MediaKind

/**
 * Keeps the user's wording first so the result matches the prompt.
 * Category is a light visual hint. Video mode adds motion language when missing.
 */
class PromptEnhancer {
    fun enhance(
        prompt: String,
        style: ImageStyle,
        enabled: Boolean,
        mediaType: MediaKind = MediaKind.IMAGE
    ): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val motion = if (mediaType == MediaKind.VIDEO) motionHint(cleaned) else ""
        if (!enabled) return cleaned + motion
        val category = if (style == ImageStyle.ALL) "" else " Visual look: ${style.promptSuffix}."
        return "$cleaned. Keep this subject exactly.$category$motion"
    }

    private fun motionHint(prompt: String): String {
        val lower = prompt.lowercase()
        val hasMotion = MOTION_WORDS.any { lower.contains(it) }
        return if (hasMotion) {
            " Smooth continuous motion, cinematic camera movement."
        } else {
            " Smooth motion, cinematic camera movement, gentle movement, 5 second video."
        }
    }

    companion object {
        private val MOTION_WORDS = listOf(
            "walk", "run", "mov", "flow", "fly", "rotat", "drift", "drone",
            "zoom", "camera", "panning", "orbit", "sway"
        )
    }
}
