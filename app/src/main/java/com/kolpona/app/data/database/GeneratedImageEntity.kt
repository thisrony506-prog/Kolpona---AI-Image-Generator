package com.kolpona.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kolpona.app.domain.model.GeneratedImage

@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey val id: String,
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
) {
    fun toModel(): GeneratedImage = GeneratedImage(
        id = id,
        prompt = prompt,
        enhancedPrompt = enhancedPrompt,
        styleId = styleId,
        aspectRatioId = aspectRatioId,
        model = model,
        width = width,
        height = height,
        localPath = localPath,
        createdAtEpochMs = createdAtEpochMs,
        creditsUsed = creditsUsed
    )

    companion object {
        fun from(model: GeneratedImage): GeneratedImageEntity = GeneratedImageEntity(
            id = model.id,
            prompt = model.prompt,
            enhancedPrompt = model.enhancedPrompt,
            styleId = model.styleId,
            aspectRatioId = model.aspectRatioId,
            model = model.model,
            width = model.width,
            height = model.height,
            localPath = model.localPath,
            createdAtEpochMs = model.createdAtEpochMs,
            creditsUsed = model.creditsUsed
        )
    }
}
