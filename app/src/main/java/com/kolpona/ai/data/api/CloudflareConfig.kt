package com.kolpona.ai.data.api

import com.kolpona.ai.BuildConfig

/**
 * Cloudflare Workers AI configuration.
 * Account id and token are injected at build time from CI / local.properties.
 * Never log, display, or persist them.
 */
object CloudflareConfig {
    const val HOST = "api.cloudflare.com"
    const val MODEL = "@cf/black-forest-labs/flux-1-schnell"
    const val USER_AGENT = "Kolpona/1.10.0 (Android)"

    val accountId: String
        get() = BuildConfig.CLOUDFLARE_ACCOUNT_ID.trim()

    val apiToken: String
        get() = BuildConfig.CLOUDFLARE_API_TOKEN.trim()

    val isConfigured: Boolean
        get() = accountId.isNotBlank() && apiToken.isNotBlank()

    fun runUrl(): String =
        "https://$HOST/client/v4/accounts/$accountId/ai/run/$MODEL"
}
