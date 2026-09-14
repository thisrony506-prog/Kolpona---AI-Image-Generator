package com.kolpona.ai.data.repository

import com.kolpona.ai.data.api.GenerationException
import com.kolpona.ai.data.api.GenerationRouter
import com.kolpona.ai.data.api.MediaRequest
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.manager.CreditManager
import com.kolpona.ai.domain.model.GeneratedImage
import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.domain.model.GenerationInput
import com.kolpona.ai.domain.model.GenerationOutcome
import com.kolpona.ai.domain.model.MediaKind
import com.kolpona.ai.notify.KolponaNotifier
import com.kolpona.ai.utils.ImageFileStore
import com.kolpona.ai.utils.NetworkMonitor
import com.kolpona.ai.utils.PromptEnhancer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class GenerateImageUseCase(
    private val router: GenerationRouter,
    private val history: HistoryRepository,
    private val credits: CreditManager,
    private val files: ImageFileStore,
    private val enhancer: PromptEnhancer,
    private val networkMonitor: NetworkMonitor,
    private val notifier: KolponaNotifier
) {
    private val mutex = Mutex()

    suspend operator fun invoke(input: GenerationInput): GenerationOutcome = mutex.withLock {
        try {
            runLocked(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GenerationException) {
            GenerationOutcome.Failure(mapError(input.mediaType, e.error))
        } catch (_: Throwable) {
            GenerationOutcome.Failure(fallbackError(input.mediaType))
        }
    }

    private suspend fun runLocked(input: GenerationInput): GenerationOutcome {
        val prompt = input.prompt.trim()
        if (prompt.isEmpty()) {
            return GenerationOutcome.Failure(GenerationError.EMPTY_PROMPT)
        }
        credits.refreshDailyCredits()
        if (!credits.canGenerate()) {
            return GenerationOutcome.Failure(GenerationError.INSUFFICIENT_CREDITS)
        }
        if (!networkMonitor.isOnline()) {
            return GenerationOutcome.Failure(GenerationError.NETWORK)
        }

        val generationId = UUID.randomUUID().toString()
        val optimized = enhancer.optimize(prompt, input.style, input.enhance, input.mediaType)
        val (width, height) = if (input.mediaType == MediaKind.VIDEO) {
            input.aspectRatio.videoDimensions()
        } else {
            input.aspectRatio.dimensions(input.quality)
        }

        val result = router.generate(
            MediaRequest(
                prompt = optimized.prompt,
                mediaType = input.mediaType,
                width = width,
                height = height,
                negativePrompt = optimized.negativePrompt,
                scene = optimized.kind,
                quality = input.quality
            )
        )

        val extension = if (input.mediaType == MediaKind.VIDEO) "mp4" else "jpg"
        val path = try {
            files.save(generationId, result.bytes, extension)
        } catch (_: Throwable) {
            return GenerationOutcome.Failure(fallbackError(input.mediaType))
        }

        val image = GeneratedImage(
            id = generationId,
            prompt = prompt,
            enhancedPrompt = optimized.prompt,
            styleId = input.style.id,
            aspectRatioId = input.aspectRatio.id,
            model = result.model,
            width = width,
            height = height,
            localPath = path,
            createdAtEpochMs = System.currentTimeMillis(),
            creditsUsed = CreditConfig.GENERATION_COST,
            mediaType = input.mediaType.id,
            durationMs = if (input.mediaType == MediaKind.VIDEO) 5_000L else 0L
        )

        history.insert(image)
        credits.deductForSuccessfulGeneration(generationId)
        notifier.notifyCreationReady(image.isVideo)
        GenerationOutcome.Success(image)
    }

    private fun mapError(kind: MediaKind, error: GenerationError): GenerationError {
        return if (kind == MediaKind.VIDEO && error == GenerationError.UNKNOWN) {
            GenerationError.VIDEO_UNAVAILABLE
        } else {
            error
        }
    }

    private fun fallbackError(kind: MediaKind): GenerationError {
        return if (kind == MediaKind.VIDEO) GenerationError.VIDEO_UNAVAILABLE else GenerationError.UNKNOWN
    }
}
