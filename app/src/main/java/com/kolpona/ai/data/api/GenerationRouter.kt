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
 * Image: race Hugging Face FLUX.1-dev and Cloudflare FLUX.1-schnell.
 * The first valid image wins. Video: Hugging Face only, never faked.
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
        if (!huggingFace.isConfigured) {
            throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
        var last: GenerationException? = null
        for (model in HuggingFaceConfig.VIDEO_MODELS) {
            try {
                val bytes = huggingFace.generateVideo(
                    prompt = request.prompt,
                    model = model,
                    width = request.width,
                    height = request.height,
                    negativePrompt = request.negativePrompt
                )
                if (qualityOk(bytes, MediaKind.VIDEO)) {
                    return MediaBytes(bytes, model)
                }
                last = GenerationException(GenerationError.VIDEO_UNAVAILABLE)
            } catch (e: GenerationException) {
                last = e
                if (e.error == GenerationError.API || e.error == GenerationError.RATE_LIMIT) break
            }
        }
        throw last ?: GenerationException(GenerationError.VIDEO_UNAVAILABLE)
    }

    private fun qualityOk(bytes: ByteArray, kind: MediaKind): Boolean =
        if (kind == MediaKind.VIDEO) {
            bytes.size >= 4_000 && MediaPayload.looksLikeVideo(bytes)
        } else {
            bytes.size >= 8_000 && MediaPayload.looksLikeImage(bytes)
        }
}
