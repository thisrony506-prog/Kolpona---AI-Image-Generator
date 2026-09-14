package com.kolpona.ai.data.api

import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.domain.model.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class MediaRequest(
    val prompt: String,
    val mediaType: MediaKind,
    val width: Int,
    val height: Int,
    val negativePrompt: String? = null,
    val scene: com.kolpona.ai.prompt.SceneKind = com.kolpona.ai.prompt.SceneKind.GENERAL,
    val quality: com.kolpona.ai.domain.model.ImageQuality = com.kolpona.ai.domain.model.ImageQuality.HIGH
)

data class MediaBytes(
    val bytes: ByteArray,
    val model: String
)

/**
 * Image: race Hugging Face FLUX and Cloudflare FLUX.1-schnell.
 * Video: Cloudflare first (same path as working photos), then Hugging Face. Never faked.
 */
class GenerationRouter(
    private val huggingFace: HuggingFaceApiService,
    private val cloudflare: CloudflareApiService
) {
    suspend fun generate(request: MediaRequest): MediaBytes {
        return if (request.mediaType == MediaKind.VIDEO) {
            generateVideo(request)
        } else {
            generateImage(request)
        }
    }

    private suspend fun generateImage(request: MediaRequest): MediaBytes = coroutineScope {
        val winner = CompletableDeferred<MediaBytes>()
        val lastError = AtomicReference<GenerationException?>(null)
        val remaining = AtomicInteger(0)
        val jobs = mutableListOf<Job>()

        fun start(id: String, enabled: Boolean, block: suspend () -> ByteArray) {
            if (!enabled) return
            remaining.incrementAndGet()
            jobs += launch {
                try {
                    val bytes = block()
                    if (!qualityOk(bytes, MediaKind.IMAGE)) {
                        throw GenerationException(GenerationError.EMPTY_RESPONSE)
                    }
                    winner.complete(MediaBytes(bytes, id))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GenerationException) {
                    lastError.set(e)
                } catch (_: Exception) {
                    lastError.set(GenerationException(GenerationError.UNKNOWN))
                } finally {
                    if (remaining.decrementAndGet() == 0 && !winner.isCompleted) {
                        winner.completeExceptionally(
                            lastError.get() ?: GenerationException(GenerationError.API)
                        )
                    }
                }
            }
        }

        start("flux-2-klein", huggingFace.isConfigured) {
            huggingFace.generateImage(
                prompt = request.prompt,
                width = request.width,
                height = request.height,
                negativePrompt = request.negativePrompt,
                model = HuggingFaceConfig.IMAGE_MODEL_KLEIN
            )
        }
        start("flux-1-dev", huggingFace.isConfigured) {
            huggingFace.generateImage(
                prompt = request.prompt,
                width = request.width,
                height = request.height,
                negativePrompt = request.negativePrompt,
                model = HuggingFaceConfig.IMAGE_MODEL_DEV
            )
        }
        start("flux-1-schnell", cloudflare.isConfigured) {
            cloudflare.generateImage(request.prompt, request.width, request.height)
        }
        if (jobs.isEmpty()) {
            throw GenerationException(GenerationError.API)
        }
        try {
            winner.await()
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    private suspend fun generateVideo(request: MediaRequest): MediaBytes {
        var last: GenerationException? = null
        if (cloudflare.isConfigured) {
            try {
                val bytes = cloudflare.generateVideo(request.prompt, request.width, request.height)
                if (qualityOk(bytes, MediaKind.VIDEO)) {
                    return MediaBytes(bytes, "cloudflare-video")
                }
                last = GenerationException(GenerationError.VIDEO_UNAVAILABLE)
            } catch (e: GenerationException) {
                last = e
            }
        }
        if (huggingFace.isConfigured) {
            try {
                val bytes = huggingFace.generateVideo(
                    prompt = request.prompt,
                    model = HuggingFaceConfig.VIDEO_MODEL,
                    width = request.width,
                    height = request.height,
                    negativePrompt = request.negativePrompt
                )
                if (qualityOk(bytes, MediaKind.VIDEO)) {
                    return MediaBytes(bytes, HuggingFaceConfig.VIDEO_MODEL)
                }
                last = GenerationException(GenerationError.VIDEO_UNAVAILABLE)
            } catch (e: GenerationException) {
                last = e
            }
        }
        val frame = try {
            generateImage(request)
        } catch (e: GenerationException) {
            throw videoError(last ?: e)
        }
        if (cloudflare.isConfigured) {
            try {
                val animated = cloudflare.generateVideoFromImage(
                    request.prompt,
                    frame.bytes,
                    request.width,
                    request.height
                )
                if (qualityOk(animated, MediaKind.VIDEO)) {
                    return MediaBytes(animated, "cloudflare-i2v")
                }
            } catch (e: GenerationException) {
                last = e
            }
        }
        if (huggingFace.isConfigured) {
            try {
                val animated = huggingFace.generateVideoFromImage(request.prompt, frame.bytes)
                if (qualityOk(animated, MediaKind.VIDEO)) {
                    return MediaBytes(animated, "ltx-i2v")
                }
            } catch (e: GenerationException) {
                last = e
            }
        }
        throw videoError(last)
    }

    private fun videoError(last: GenerationException?): GenerationException {
        return when (last?.error) {
            GenerationError.NETWORK, GenerationError.RATE_LIMIT ->
                last ?: GenerationException(GenerationError.VIDEO_UNAVAILABLE)
            else -> GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
    }

    private fun qualityOk(bytes: ByteArray, kind: MediaKind): Boolean =
        if (kind == MediaKind.VIDEO) {
            bytes.size >= 4_000 && MediaPayload.looksLikeVideo(bytes)
        } else {
            bytes.size >= 8_000 && MediaPayload.looksLikeImage(bytes)
        }
}
