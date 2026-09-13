package com.kolpona.ai.data.api

import com.kolpona.ai.BuildConfig

/**
 * Hugging Face Inference configuration.
 * The API token is injected at build time from CI / local.properties. Never log it.
 * Keys shared in chat are treated as exposed; use newly generated CI secrets only.
 */
object HuggingFaceConfig {
    const val ROUTER_BASE = "https://router.huggingface.co/hf-inference/models"
    const val INFERENCE_BASE = "https://api-inference.huggingface.co/models"

    const val IMAGE_MODEL_DEV = "black-forest-labs/FLUX.1-dev"
    const val VIDEO_MODEL = "THUDM/CogVideoX-5b"
    val VIDEO_MODELS: List<String> = listOf(
        "THUDM/CogVideoX-5b",
        "Lightricks/LTX-Video",
        "Wan-AI/Wan2.1-T2V-1.3B"
    )
    const val USER_AGENT = "Kolpona/1.10.0 (Android)"

    val apiKey: String
        get() = BuildConfig.HUGGINGFACE_API_KEY

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()
}
