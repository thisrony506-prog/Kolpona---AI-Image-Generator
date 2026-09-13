package com.kolpona.app.data.repository

import com.kolpona.app.data.api.GenerationException
import com.kolpona.app.data.api.PollinationsApiService
import com.kolpona.app.data.api.PollinationsConfig
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.manager.CreditManager
import com.kolpona.app.domain.model.GeneratedImage
import com.kolpona.app.domain.model.GenerationError
import com.kolpona.app.domain.model.GenerationInput
import com.kolpona.app.domain.model.GenerationOutcome
import com.kolpona.app.utils.ImageFileStore
import com.kolpona.app.utils.NetworkMonitor
import com.kolpona.app.utils.PromptEnhancer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.random.Random

/**
 * Single pipeline for image generation.
 * Credits are deducted only after a successful save. The mutex plus generation id
 * guarantee exactly one -25 transaction per completed image.
 */
class GenerateImageUseCase(
    private val api: PollinationsApiService,
    private val history: HistoryRepository,
    private val credits: CreditManager,
    private val files: ImageFileStore,
    private val enhancer: PromptEnhancer,
    private val networkMonitor: NetworkMonitor
) {
    private val mutex = Mutex()

    suspend operator fun invoke(input: GenerationInput): GenerationOutcome = mutex.withLock {
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
        val enhanced = enhancer.enhance(prompt, input.style, input.enhance)
        val (width, height) = input.aspectRatio.dimensions(input.quality)
        val model = input.modelId.ifBlank { PollinationsConfig.DEFAULT_MODEL }

        val bytes = try {
            api.generateImage(
                prompt = enhanced,
                model = model,
                width = width,
                height = height,
                seed = Random.nextInt(1, Int.MAX_VALUE)
            )
        } catch (e: GenerationException) {
            return GenerationOutcome.Failure(e.error)
        }

        val path = try {
            files.save(generationId, bytes)
        } catch (_: Exception) {
            return GenerationOutcome.Failure(GenerationError.UNKNOWN)
        }

        val image = GeneratedImage(
            id = generationId,
            prompt = prompt,
            enhancedPrompt = enhanced,
            styleId = input.style.id,
            aspectRatioId = input.aspectRatio.id,
            model = model,
            width = width,
            height = height,
            localPath = path,
            createdAtEpochMs = System.currentTimeMillis(),
            creditsUsed = CreditConfig.GENERATION_COST
        )

        history.insert(image)
        credits.deductForSuccessfulGeneration(generationId)
        GenerationOutcome.Success(image)
    }
}
