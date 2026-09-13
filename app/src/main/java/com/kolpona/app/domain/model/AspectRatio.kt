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
    PORTRAIT_4_5("4:5", R.string.ratio_tall, "4:5", 819, 1024),
    LANDSCAPE("16:9", R.string.ratio_landscape, "16:9", 1280, 720),
    PORTRAIT_9_16("9:16", R.string.ratio_story, "9:16", 720, 1280),
    PORTRAIT("3:4", R.string.ratio_portrait, "3:4", 768, 1024);

    fun dimensions(quality: ImageQuality): Pair<Int, Int> {
        val w = ((baseWidth * quality.scale).toInt()).coerceAtLeast(512)
        val h = ((baseHeight * quality.scale).toInt()).coerceAtLeast(512)
        return align(w) to align(h)
    }

    fun videoDimensions(): Pair<Int, Int> {
        val (w, h) = dimensions(ImageQuality.HIGH)
        val maxSide = 768
        val longSide = maxOf(w, h).toFloat().coerceAtLeast(1f)
        val scale = if (longSide > maxSide) maxSide / longSide else 1f
        return align((w * scale).toInt().coerceAtLeast(256)) to
            align((h * scale).toInt().coerceAtLeast(256))
    }

    private fun align(value: Int): Int = value - (value % 8)

    companion object {
        fun fromId(id: String): AspectRatio = when (id) {
            "4:3" -> SQUARE
            else -> entries.find { it.id == id } ?: SQUARE
        }
    }
}

enum class ImageQuality(val id: String, val scale: Float, @StringRes val labelRes: Int) {
    STANDARD("standard", 0.75f, R.string.quality_standard),
    HIGH("high", 1.0f, R.string.quality_high),
    ULTRA("ultra", 1.25f, R.string.quality_ultra);

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
