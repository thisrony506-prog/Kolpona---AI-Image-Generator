package com.kolpona.ai.domain.model

import androidx.annotation.StringRes
import com.kolpona.ai.R

enum class ImageStyle(
    val id: String,
    @StringRes val labelRes: Int,
    val promptSuffix: String
) {
    ALL("all", R.string.style_all, "high quality, detailed"),
    PEOPLE("people", R.string.style_people, "people, natural portraits, realistic skin, expressive faces"),
    ANIMALS("animals", R.string.style_animals, "animals, detailed fur or feathers, natural anatomy"),
    NATURE("nature", R.string.style_nature, "nature landscape, realistic atmosphere, organic detail"),
    FANTASY("fantasy", R.string.style_fantasy, "epic fantasy, magical atmosphere, ornate detail"),
    ARCHITECTURE("architecture", R.string.style_architecture, "architecture, precise structure, cinematic building photography"),
    CINEMATIC("cinematic", R.string.style_cinematic, "cinematic lighting, film still, dramatic atmosphere"),
    ANIME("anime", R.string.style_anime, "anime style, vibrant colors, clean lineart"),
    PRODUCT("product", R.string.style_product, "product photography, studio lighting, sharp materials"),
    FASHION("fashion", R.string.style_fashion, "fashion photography, detailed fabric, editorial lighting"),
    FOOD("food", R.string.style_food, "food photography, appetizing, shallow depth of field"),
    VEHICLES("vehicles", R.string.style_vehicles, "vehicles, detailed bodywork, dynamic automotive photography"),
    ABSTRACT("abstract", R.string.style_abstract, "abstract composition, artistic forms, bold color");

    companion object {
        fun fromId(id: String): ImageStyle = when (id) {
            "realistic", "portrait" -> PEOPLE
            "digital_art", "illustration", "3d", "minimal" -> ABSTRACT
            else -> entries.find { it.id == id } ?: ALL
        }
    }
}
