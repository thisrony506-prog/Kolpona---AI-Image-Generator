package com.kolpona.ai.prompt

import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.MediaKind

/**
 * Master prompts auto-appended to the understood user request.
 * Chat still shows the original typed text.
 */
object PromptTemplates {
    const val PHOTO =
        "ultra photorealistic, 8k uhd, DSLR photo, 85mm lens, f1.8, soft natural lighting, cinematic, " +
            "sharp focus, highly detailed skin texture, fabric detail, masterpiece, best quality, " +
            "natural colors, volumetric light"

    const val VIDEO =
        "cinematic 5 second video, 24fps, smooth natural camera movement, realistic physics, " +
            "fabric and hair moving naturally, photorealistic lighting, sharp focus, high detail, " +
            "no flicker, no distortion, fluid motion, shallow depth of field"

    const val FASHION =
        "fashion commercial, Vogue magazine style, full body, studio lighting, fabric detail, " +
            "catwalk, 8k, photorealistic, masterpiece"

    const val NEGATIVE =
        "blurry, low quality, distorted, cartoon, anime, painting, ugly, deformed, extra limbs, " +
            "bad anatomy, watermark, text"

    const val NEGATIVE_ANIME =
        "blurry, low quality, extra limbs, bad anatomy, watermark, text, photorealistic"

    private val clothing = listOf(
        "pant", "pants", "shirt", "shoe", "shoes", "dress", "sari", "lungi", "panjabi",
        "kameez", "kurta", "jacket", "jeans", "clothing", "wear", "fabric", "suit",
        "hoodie", "saree", "শাড়ি", "লুঙ্গি", "পাঞ্জাবি", "জামা"
    )

    fun apply(
        subject: String,
        kind: SceneKind,
        style: ImageStyle,
        media: MediaKind
    ): String {
        val anime = style == ImageStyle.ANIME || kind == SceneKind.ANIME
        val fashion = !anime && (kind == SceneKind.FASHION || style == ImageStyle.FASHION)
        val extras = buildList {
            if (fashion) add(FASHION)
            if (anime) {
                add("anime style, clean lineart, vibrant color")
            } else if (media == MediaKind.VIDEO) {
                add(VIDEO)
                if (fashion) add("fashion commercial, Vogue style")
            } else {
                add(PHOTO)
            }
        }
        return merge(subject, extras)
    }

    fun negative(kind: SceneKind, style: ImageStyle): String =
        if (style == ImageStyle.ANIME || kind == SceneKind.ANIME) NEGATIVE_ANIME else NEGATIVE

    fun looksLikeClothing(text: String): Boolean {
        val lower = text.lowercase()
        return clothing.any { lower.contains(it) }
    }

    private fun merge(prompt: String, extras: List<String>): String {
        val extra = extras.map { it.trim().trim(',') }.filter { it.isNotBlank() }
            .filterNot { piece -> prompt.contains(piece, ignoreCase = true) }
        return if (extra.isEmpty()) prompt else "$prompt, ${extra.joinToString(", ")}"
    }
}
