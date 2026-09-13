package com.kolpona.app.data.api

import com.kolpona.app.BuildConfig

/**
 * Hugging Face Inference configuration.
 * The API token is injected at build time from CI / local.properties. Never log it.
 * Keys shared in chat are treated as exposed; use newly generated CI secrets only.
 */
object HuggingFaceConfig {
    const val ROUTER_BASE = "https://router.huggingface.co/hf-inference/models"
    const val INFERENCE_BASE = "https://api-inference.huggingface.co/models"

    const val IMAGE_MODEL_PRIMARY = "black-forest-labs/FLUX.1-schnell"
    const val IMAGE_MODEL_FALLBACK = "stabilityai/sdxl-turbo"
    const val VIDEO_MODEL = "ali-vilab/text-to-video-ms-1.7b"

    val apiKey: String
        get() = BuildConfig.HUGGINGFACE_API_KEY

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()
}
