package com.kolpona.app.utils

import com.kolpona.app.data.api.TextNormalizeService
import com.kolpona.app.domain.model.ImageStyle
import com.kolpona.app.domain.model.MediaKind
import com.kolpona.app.prompt.OptimizedGeneration
import com.kolpona.app.prompt.SceneIntent
import com.kolpona.app.prompt.SceneKind

/**
 * Understand the user first, then add only relevant quality or motion.
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
        val understood = try {
            normalizer?.understand(cleaned) ?: cleaned
        } catch (_: Exception) {
            cleaned
        }
        val intent = SceneIntent.extract(cleaned, understood)
        val withText = preserveRequestedText(understood, intent)
        if (!enabled) {
            val body = if (mediaType == MediaKind.VIDEO) withText + motionFor(intent, withText) else withText
            return OptimizedGeneration(body, negativeFor(intent, style), intent.kind)
        }
        val enhanced = if (mediaType == MediaKind.VIDEO) {
            enhanceVideo(withText, style, intent)
        } else {
            enhanceImage(withText, style, intent)
        }
        return OptimizedGeneration(enhanced, negativeFor(intent, style), intent.kind)
    }

    fun enhance(
        prompt: String,
        style: ImageStyle,
        enabled: Boolean,
        mediaType: MediaKind = MediaKind.IMAGE
    ): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val intent = SceneIntent.extract(cleaned, cleaned)
        if (!enabled) {
            return if (mediaType == MediaKind.VIDEO) cleaned + motionFor(intent, cleaned) else cleaned
        }
        return if (mediaType == MediaKind.VIDEO) enhanceVideo(cleaned, style, intent)
        else enhanceImage(cleaned, style, intent)
    }

    private fun enhanceImage(prompt: String, style: ImageStyle, intent: SceneIntent): String {
        val extras = buildList {
            if (style != ImageStyle.ALL && style != ImageStyle.ANIME) {
                add(style.promptSuffix)
            }
            when {
                style == ImageStyle.ANIME || intent.kind == SceneKind.ANIME ->
                    add("anime style, clean lineart, vibrant color")
                intent.kind == SceneKind.PRODUCT || style == ImageStyle.PRODUCT ->
                    add("professional product photography, studio lighting, accurate product geometry, realistic materials, sharp commercial detail")
                intent.kind == SceneKind.FASHION || style == ImageStyle.FASHION ->
                    add("fashion photography, realistic fabric texture, natural drape, editorial lighting")
                intent.kind == SceneKind.PERSON || style == ImageStyle.PEOPLE ->
                    add("realistic skin texture, natural facial features, realistic hair, natural lighting, realistic shadows, sharp subject, coherent composition")
                intent.kind == SceneKind.LANDSCAPE || style == ImageStyle.NATURE ->
                    add("realistic atmosphere, depth, natural lighting, detailed environment")
                style == ImageStyle.CINEMATIC || intent.kind == SceneKind.CINEMATIC ->
                    add("cinematic composition, film-like framing, atmospheric perspective, controlled color grading")
                intent.kind == SceneKind.FOOD || style == ImageStyle.FOOD ->
                    add("food photography, appetizing, shallow depth of field")
                else -> {
                    if (!prompt.contains("light", ignoreCase = true)) add("natural lighting")
                    if (!prompt.contains("detail", ignoreCase = true)) add("highly detailed")
                }
            }
            if (intent.culturalRegion) {
                add("authentic South Asian visual details, culturally accurate clothing and environment")
            }
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return merge(prompt, extras)
    }

    private fun enhanceVideo(prompt: String, style: ImageStyle, intent: SceneIntent): String {
        val look = when {
            style == ImageStyle.ANIME -> "anime cinematography"
            style != ImageStyle.ALL -> style.promptSuffix
            else -> "cinematic cinematography, realistic lighting"
        }
        return merge(prompt, listOf(look, motionFor(intent, prompt).trim().trim(',')))
    }

    private fun motionFor(intent: SceneIntent, prompt: String): String {
        val lower = prompt.lowercase()
        val alreadyHasMotion = MOTION_WORDS.any { lower.contains(it) } &&
            (intent.walking || intent.running || intent.flying || intent.flowingWater || intent.userCamera != null)
        val motion = when {
            intent.userCamera != null && (intent.sitting || intent.standing) ->
                "subtle natural breathing and slight fabric or hair movement, ${intent.userCamera}"
            intent.sitting ->
                "sitting calmly, subtle natural breathing and small head movement, realistic micro-motion"
            intent.standing && !intent.walking ->
                "standing naturally, subtle breathing and fabric movement"
            intent.walking ->
                "walking with natural body movement"
            intent.running ->
                "running with realistic body movement"
            intent.flying ->
                "flying, gentle camera tracking"
            intent.flowingWater ->
                "water continuously flowing, drifting mist, subtle vegetation movement"
            intent.landscape && intent.raining ->
                "falling rain, clouds drifting, gentle environmental movement"
            intent.landscape ->
                "clouds slowly moving, subtle wind through vegetation, smooth cinematic drone shot"
            intent.raining ->
                "rain falling naturally, wet surfaces, subtle ambient movement"
            alreadyHasMotion -> ""
            else -> "subtle natural movement"
        }
        val camera = intent.userCamera?.takeIf { !motion.contains(it) } ?: ""
        return listOf(motion, camera, "realistic motion, 5 second video")
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .let { ", $it" }
    }

    private fun negativeFor(intent: SceneIntent, style: ImageStyle): String? {
        if (style == ImageStyle.ANIME || intent.kind == SceneKind.ANIME || intent.kind == SceneKind.ABSTRACT) {
            return "blurry, low resolution, extra limbs, text artifacts, watermark"
        }
        return when (intent.kind) {
            SceneKind.PERSON, SceneKind.FASHION ->
                "blurry, low resolution, distorted face, malformed hands, extra fingers, duplicated body parts, unnatural anatomy, distorted eyes, duplicate objects, artifacts, watermark, text artifacts"
            SceneKind.PRODUCT ->
                "distorted geometry, duplicate product, warped edges, unrealistic material, text artifacts, watermark, blurry"
            SceneKind.ANIMAL ->
                "blurry, extra legs, distorted face, unnatural anatomy, watermark"
            else ->
                "blurry, low resolution, artifacts, watermark, duplicate objects, distorted"
        }
    }

    private fun preserveRequestedText(prompt: String, intent: SceneIntent): String {
        if (intent.quotedText.isEmpty()) return prompt
        val clause = intent.quotedText.joinToString("; ") { "\"$it\"" }
        if (prompt.contains(clause)) return prompt
        return "$prompt, the text in the image must appear exactly as $clause"
    }

    private fun merge(prompt: String, extras: List<String>): String {
        val extra = extras.map { it.trim().trim(',') }.filter { it.isNotBlank() }
            .filterNot { piece -> prompt.contains(piece, ignoreCase = true) }
        return if (extra.isEmpty()) prompt else "$prompt, ${extra.joinToString(", ")}"
    }

    companion object {
        private val MOTION_WORDS = listOf(
            "walk", "run", "mov", "flow", "fly", "rotat", "drift", "drone",
            "zoom", "camera", "panning", "orbit", "sway", "breath"
        )
    }
}
