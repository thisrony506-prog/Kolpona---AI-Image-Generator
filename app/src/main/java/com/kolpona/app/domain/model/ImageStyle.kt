package com.kolpona.app.domain.model

import androidx.annotation.StringRes
import com.kolpona.app.R

enum class ImageStyle(
    val id: String,
    @StringRes val labelRes: Int,
    val promptSuffix: String
) {
    REALISTIC(
        "realistic",
        R.string.style_realistic,
        "photorealistic, natural lighting, highly detailed photography, sharp focus"
    ),
    ANIME(
        "anime",
        R.string.style_anime,
        "anime style, vibrant colors, clean lineart, expressive, studio animation still"
    ),
    CINEMATIC(
        "cinematic",
        R.string.style_cinematic,
        "cinematic lighting, film still, dramatic atmosphere, shallow depth of field"
    ),
    DIGITAL_ART(
        "digital_art",
        R.string.style_digital_art,
        "digital painting, detailed illustration, artstation, rich color"
    ),
    THREE_D(
        "3d",
        R.string.style_3d,
        "3d render, octane render, physically based rendering, detailed materials"
    ),
    FANTASY(
        "fantasy",
        R.string.style_fantasy,
        "epic fantasy, magical atmosphere, ethereal light, ornate detail"
    ),
    MINIMAL(
        "minimal",
        R.string.style_minimal,
        "minimalist, clean composition, negative space, simple elegant forms"
    ),
    PORTRAIT(
        "portrait",
        R.string.style_portrait,
        "portrait, studio lighting, sharp facial detail, shallow bokeh"
    ),
    ILLUSTRATION(
        "illustration",
        R.string.style_illustration,
        "hand-drawn illustration, detailed artwork, refined line and color"
    );

    companion object {
        fun fromId(id: String): ImageStyle =
            entries.find { it.id == id } ?: REALISTIC
    }
}
