package com.kolpona.app.domain.model

data class GeneratedImage(
    val id: String,
    val prompt: String,
    val enhancedPrompt: String,
    val styleId: String,
    val aspectRatioId: String,
    val model: String,
    val width: Int,
    val height: Int,
    val localPath: String,
    val createdAtEpochMs: Long,
    val creditsUsed: Int
)

data class GenerationInput(
    val prompt: String,
    val style: ImageStyle,
    val aspectRatio: AspectRatio,
    val quality: ImageQuality,
    val modelId: String,
    val enhance: Boolean
)

sealed class GenerationOutcome {
    data class Success(val image: GeneratedImage) : GenerationOutcome()
    data class Failure(val error: GenerationError) : GenerationOutcome()
}

enum class GenerationError {
    EMPTY_PROMPT,
    INSUFFICIENT_CREDITS,
    NETWORK,
    TIMEOUT,
    SERVER,
    RATE_LIMIT,
    EMPTY_RESPONSE,
    INVALID_PROMPT,
    API,
    UNKNOWN
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromId(id: String): ThemeMode =
            entries.find { it.name == id } ?: SYSTEM
    }
}
