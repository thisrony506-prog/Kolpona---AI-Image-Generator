package com.kolpona.app.data.api

import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.MediaKind

data class MediaRequest(
    val prompt: String,
    val mediaType: MediaKind,
    val width: Int,
    val height: Int
)

data class MediaBytes(
    val bytes: ByteArray,
    val model: String
)

/**
 * Picks a real image-capable or video-capable provider, then falls back.
 * Never uses an image endpoint to fake video.
 */
class GenerationRouter(
    private val huggingFace: HuggingFaceApiService,
    private val pollinations: PollinationsApiService
) {
    suspend fun generate(request: MediaRequest): MediaBytes {
        val chain = providersFor(request.mediaType)
        if (chain.isEmpty()) {
            throw GenerationException(
                if (request.mediaType == MediaKind.VIDEO) GenerationError.VIDEO_UNAVAILABLE
                else GenerationError.API
            )
        }
        var last: GenerationException? = null
        for (provider in chain) {
            try {
                return provider.invoke(request)
            } catch (e: GenerationException) {
                last = e
                if (e.error == GenerationError.INVALID_PROMPT ||
                    e.error == GenerationError.NETWORK ||
                    e.error == GenerationError.TIMEOUT ||
                    e.error == GenerationError.RATE_LIMIT
                ) {
                    continue
                }
            }
        }
        throw last ?: GenerationException(GenerationError.API)
    }

    private fun providersFor(kind: MediaKind): List<suspend (MediaRequest) -> MediaBytes> {
        return when (kind) {
            MediaKind.IMAGE -> buildList {
                if (huggingFace.isConfigured) {
                    add { req ->
                        MediaBytes(
                            huggingFace.generateImage(req.prompt, req.width, req.height),
                            HuggingFaceConfig.IMAGE_MODEL_PRIMARY
                        )
                    }
                }
                add { req ->
                    MediaBytes(
                        pollinations.generateImage(
                            prompt = req.prompt,
                            model = PollinationsConfig.DEFAULT_MODEL,
                            width = req.width,
                            height = req.height
                        ),
                        PollinationsConfig.DEFAULT_MODEL
                    )
                }
            }
            MediaKind.VIDEO -> buildList {
                add { req ->
                    MediaBytes(
                        pollinations.generateVideo(req.prompt, req.width, req.height),
                        PollinationsConfig.VIDEO_MODEL
                    )
                }
                if (huggingFace.isConfigured) {
                    add { req ->
                        MediaBytes(
                            huggingFace.generateVideo(req.prompt),
                            HuggingFaceConfig.VIDEO_MODEL
                        )
                    }
                }
            }
        }
    }
}
