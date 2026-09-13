package com.kolpona.app.data.api

import com.kolpona.app.BuildConfig

/**
 * Isolated Pollinations configuration.
 *
 * API_BASE_URL and DEFAULT_MODEL live here so the rest of the app never hardcodes them.
 * The API key is injected at build time from local.properties / CI secrets via BuildConfig.
 * Never log, display, or persist [apiKey].
 *
 * To replace the credential later:
 * 1. Put the new key in local.properties as POLLINATIONS_API_KEY=...
 *    or set the GitHub Actions secret POLLINATIONS_API_KEY.
 * 2. Rebuild. No UI or repository changes are required.
 *
 * A future backend proxy can replace [PollinationsApiService] without touching screens.
 */
object PollinationsConfig {
    const val API_BASE_URL = "https://gen.pollinations.ai"
    const val DEFAULT_MODEL = "flux"
    const val IMAGE_PATH = "image"

    val apiKey: String
        get() = BuildConfig.POLLINATIONS_API_KEY
}
