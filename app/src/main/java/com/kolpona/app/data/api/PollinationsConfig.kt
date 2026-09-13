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
    const val FALLBACK_BASE_URL = "https://image.pollinations.ai"
    const val FALLBACK_PATH = "prompt"
    const val DEFAULT_MODEL = "flux"

    val apiKey: String
        get() = BuildConfig.POLLINATIONS_API_KEY
}
