package com.kolpona.ai.data.api

import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.prompt.SceneKind

data class MediaRequest(
    val prompt: String,
    val mediaType: MediaKind,
    val width: Int,
    val height: Int,
    val negativePrompt: String? = null,
    val scene: SceneKind = SceneKind.GENERAL,
    val quality: ImageQuality = ImageQuality.HIGH
)

data class MediaBytes(
    val bytes: ByteArray,
    val model: String
)

private data class RoutedModel(
    val id: String,
    val media: MediaKind,
    val promptAdherence: Int,
    val photoreal: Int,
    val supportsNegative: Boolean,
    val generate: suspend (MediaRequest) -> ByteArray
)

/**
 * Capability-based router. Image models never generate video.
 * Order is scored for the request, then failed attempts fall through.
 */
class GenerationRouter(
    private val huggingFace: HuggingFaceApiService,
    private val pollinations: PollinationsApiService
) {
    suspend fun generate(request: MediaRequest): MediaBytes {
        val chain = rank(request)
        if (chain.isEmpty()) {
            throw GenerationException(
                if (request.mediaType == MediaKind.VIDEO) GenerationError.VIDEO_UNAVAILABLE
                else GenerationError.API
            )
        }
        var last: GenerationException? = null
        for (model in chain) {
            try {
                val bytes = model.generate(request)
                if (!qualityOk(bytes, request.mediaType)) {
                    last = GenerationException(
                        if (request.mediaType == MediaKind.VIDEO) GenerationError.VIDEO_UNAVAILABLE
                        else GenerationError.EMPTY_RESPONSE
                    )
                    continue
                }
                return MediaBytes(bytes, model.id)
            } catch (e: GenerationException) {
                last = e
            }
        }
        throw last ?: GenerationException(GenerationError.API)
    }

    private fun rank(request: MediaRequest): List<RoutedModel> {
        val wantsPeople = request.scene == SceneKind.PERSON ||
            request.scene == SceneKind.FASHION ||
            request.scene == SceneKind.PRODUCT
        return catalog()
            .filter { it.media == request.mediaType }
            .sortedByDescending { model ->
                var score = model.promptAdherence * 2 + model.photoreal
                if (wantsPeople) score += model.photoreal
                if (request.negativePrompt != null && model.supportsNegative) score += 1
                score
            }
    }

    private fun catalog(): List<RoutedModel> = buildList {
        add(
            RoutedModel(
                id = PollinationsConfig.DEFAULT_MODEL,
                media = MediaKind.IMAGE,
                promptAdherence = 5,
                photoreal = 5,
                supportsNegative = false
            ) { req ->
                pollinations.generateImage(
                    prompt = req.prompt,
                    model = if (req.quality == ImageQuality.STANDARD) {
                        PollinationsConfig.FAST_MODEL
                    } else {
                        PollinationsConfig.DEFAULT_MODEL
                    },
                    width = req.width,
                    height = req.height
                )
            }
        )
        if (huggingFace.isConfigured) {
            add(
                RoutedModel(
                    id = HuggingFaceConfig.IMAGE_MODEL_PRIMARY,
                    media = MediaKind.IMAGE,
                    promptAdherence = 4,
                    photoreal = 5,
                    supportsNegative = false
                ) { req ->
                    huggingFace.generateImage(
                        prompt = req.prompt,
                        width = req.width,
                        height = req.height,
                        negativePrompt = null,
                        model = HuggingFaceConfig.IMAGE_MODEL_PRIMARY
                    )
                }
            )
            add(
                RoutedModel(
                    id = HuggingFaceConfig.IMAGE_MODEL_DEV,
                    media = MediaKind.IMAGE,
                    promptAdherence = 4,
                    photoreal = 5,
                    supportsNegative = false
                ) { req ->
                    huggingFace.generateImage(
                        prompt = req.prompt,
                        width = req.width,
                        height = req.height,
                        negativePrompt = null,
                        model = HuggingFaceConfig.IMAGE_MODEL_DEV
                    )
                }
            )
            add(
                RoutedModel(
                    id = HuggingFaceConfig.IMAGE_MODEL_FALLBACK,
                    media = MediaKind.IMAGE,
                    promptAdherence = 3,
                    photoreal = 4,
                    supportsNegative = true
                ) { req ->
                    huggingFace.generateImage(
                        prompt = req.prompt,
                        width = req.width,
                        height = req.height,
                        negativePrompt = req.negativePrompt,
                        model = HuggingFaceConfig.IMAGE_MODEL_FALLBACK
                    )
                }
            )
        }
        PollinationsConfig.VIDEO_MODELS.forEachIndexed { index, model ->
            add(
                RoutedModel(
                    id = model,
                    media = MediaKind.VIDEO,
                    promptAdherence = 5 - index,
                    photoreal = 5,
                    supportsNegative = false
                ) { req ->
                    pollinations.generateVideo(req.prompt, req.width, req.height, model)
                }
            )
        }
        if (huggingFace.isConfigured) {
            HuggingFaceConfig.VIDEO_MODELS.forEachIndexed { index, model ->
                add(
                    RoutedModel(
                        id = model,
                        media = MediaKind.VIDEO,
                        promptAdherence = 2 - index,
                        photoreal = 3,
                        supportsNegative = false
                    ) { req ->
                        huggingFace.generateVideo(req.prompt, model)
                    }
                )
            }
        }
    }

    private fun qualityOk(bytes: ByteArray, kind: MediaKind): Boolean =
        if (kind == MediaKind.VIDEO) {
            bytes.size >= 4_000 && MediaPayload.looksLikeVideo(bytes)
        } else {
            bytes.size >= 8_000
        }
}
