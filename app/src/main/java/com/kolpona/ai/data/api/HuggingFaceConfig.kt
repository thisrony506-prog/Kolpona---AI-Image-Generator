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
    const val ROUTER_HOST = "https://router.huggingface.co"
    const val CHAT_URL = "https://router.huggingface.co/v1/chat/completions"

    const val IMAGE_MODEL_KLEIN = "black-forest-labs/FLUX.2-klein"
    const val IMAGE_MODEL_DEV = "black-forest-labs/FLUX.1-dev"
    const val VIDEO_MODEL = "Wan-AI/Wan2.2-TI2V-5B"
    const val IMAGE_STEPS = 50
    const val IMAGE_GUIDANCE = 8.5
    const val VIDEO_STEPS = 40
    const val VIDEO_FRAMES = 81
    const val VIDEO_FPS = 16
    const val VIDEO_GUIDANCE = 7.5
    const val VIDEO_MOTION_BUCKET = 127
    val VIDEO_MODELS: List<String> = listOf(
        "Wan-AI/Wan2.2-TI2V-5B",
        "Wan-AI/Wan2.1-T2V-1.3B",
        "Lightricks/LTX-Video-0.9.8-13B-distilled",
        "Lightricks/LTX-Video",
        "THUDM/CogVideoX-5b"
    )
    val VIDEO_PROVIDERS: List<String> = listOf(
        "fal-ai",
        "replicate",
        "together",
        "novita",
        "wavespeedai"
    )
    val CHAT_MODELS: List<String> = listOf(
        "meta-llama/Llama-3.2-3B-Instruct",
        "HuggingFaceTB/SmolLM3-3B",
        "Qwen/Qwen2.5-3B-Instruct"
    )
    const val TRANSLATE_MODEL = "facebook/nllb-200-distilled-600M"
    const val USER_AGENT = "Kolpona/1.12.0 (Android)"

    val apiKey: String
        get() = BuildConfig.HUGGINGFACE_API_KEY

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()
}
