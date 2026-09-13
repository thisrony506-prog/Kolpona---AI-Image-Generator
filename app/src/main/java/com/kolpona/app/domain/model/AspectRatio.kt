package com.kolpona.app.domain.model

import androidx.annotation.StringRes
import com.kolpona.app.R

enum class AspectRatio(
    val id: String,
    @StringRes val labelRes: Int,
    val shortLabel: String,
    private val baseWidth: Int,
    private val baseHeight: Int
) {
    SQUARE("1:1", R.string.ratio_square, "1:1", 1024, 1024),
    PORTRAIT("4:5", R.string.ratio_portrait, "4:5", 832, 1040),
    LANDSCAPE("16:9", R.string.ratio_landscape, "16:9", 1280, 720);

    fun dimensions(quality: ImageQuality): Pair<Int, Int> {
        val w = ((baseWidth * quality.scale).toInt()).coerceAtLeast(512)
        val h = ((baseHeight * quality.scale).toInt()).coerceAtLeast(512)
        return align(w) to align(h)
    }

    private fun align(value: Int): Int = value - (value % 8)

    companion object {
        fun fromId(id: String): AspectRatio =
            entries.find { it.id == id } ?: SQUARE
    }
}

enum class ImageQuality(val id: String, val scale: Float, @StringRes val labelRes: Int) {
    STANDARD("standard", 0.75f, R.string.quality_standard),
    HIGH("high", 1.0f, R.string.quality_high);

    companion object {
        fun fromId(id: String): ImageQuality =
            entries.find { it.id == id } ?: HIGH
    }
}

data class ImageModelOption(
    val id: String,
    val displayName: String
)

object ImageModels {
    val available: List<ImageModelOption> = listOf(
        ImageModelOption(id = com.kolpona.app.data.api.PollinationsConfig.DEFAULT_MODEL, displayName = "Flux")
    )

    fun default(): ImageModelOption = available.first()

    fun fromId(id: String): ImageModelOption =
        available.find { it.id == id } ?: default()
}
