package com.kolpona.app.utils

import com.kolpona.app.domain.model.ImageStyle

/**
 * Keeps the user's wording first so the image matches the prompt.
 * Style is a light hint only — never a rewrite.
 */
class PromptEnhancer {
    fun enhance(prompt: String, style: ImageStyle, enabled: Boolean): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        if (!enabled) return cleaned
        return "$cleaned. Style: ${style.promptSuffix}."
    }
}
