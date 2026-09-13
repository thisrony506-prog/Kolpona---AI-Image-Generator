package com.kolpona.app.utils

import com.kolpona.app.domain.model.ImageStyle

/**
 * Local prompt enrichment. Does not call any paid AI service.
 */
class PromptEnhancer {
    fun enhance(prompt: String, style: ImageStyle, enabled: Boolean): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val stylePart = style.promptSuffix
        return if (enabled) {
            "$cleaned, $stylePart, high quality, coherent composition"
        } else {
            "$cleaned, $stylePart"
        }
    }
}
