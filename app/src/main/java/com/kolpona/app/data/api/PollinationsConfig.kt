package com.kolpona.app.data.api

import com.kolpona.app.BuildConfig

/**
 * Isolated Pollinations configuration.
 *
 * The API key is injected at build time from local.properties / CI secrets via BuildConfig.
 * Never log, display, or persist [apiKey].
 */
object PollinationsConfig {
    const val API_BASE_URL = "https://gen.pollinations.ai"
    const val IMAGE_PATH = "image"
    const val VIDEO_PATH = "video"
    const val FALLBACK_BASE_URL = "https://image.pollinations.ai"
    const val FALLBACK_PATH = "prompt"
    const val VIDEO_FALLBACK_BASE_URL = "https://video.pollinations.ai"
    const val DEFAULT_MODEL = "flux"
    const val VIDEO_MODEL = "seedance"
    val VIDEO_MODELS: List<String> = listOf("seedance", "veo", "wan")
    const val USER_AGENT = "Kolpona/1.6.1 (Android)"

    val apiKey: String
        get() = BuildConfig.POLLINATIONS_API_KEY
}
