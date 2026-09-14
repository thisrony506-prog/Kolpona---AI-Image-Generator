package com.kolpona.ai.domain.model

enum class MediaKind(val id: String) {
    IMAGE("image"),
    VIDEO("video");

    companion object {
        fun fromId(id: String): MediaKind =
            entries.find { it.id == id } ?: IMAGE
    }
}

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
    val creditsUsed: Int,
    val mediaType: String = MediaKind.IMAGE.id,
    val durationMs: Long = 0L
) {
    val isVideo: Boolean get() = mediaType == MediaKind.VIDEO.id
}

data class GenerationInput(
    val prompt: String,
    val style: ImageStyle,
    val aspectRatio: AspectRatio,
    val quality: ImageQuality,
    val modelId: String,
    val enhance: Boolean,
    val mediaType: MediaKind = MediaKind.IMAGE
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
    VIDEO_UNAVAILABLE,
    INVALID_TOKEN,
    PERMISSION_DENIED,
    MODEL_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    UNKNOWN
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromId(id: String): ThemeMode =
            entries.find { it.name == id } ?: SYSTEM
    }
}
