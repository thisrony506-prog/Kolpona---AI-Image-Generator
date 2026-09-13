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
    val CHAT_MODELS: List<String> = listOf(
        "@cf/meta/llama-3.2-3b-instruct",
        "@cf/meta/llama-3.1-8b-instruct"
    )
    const val USER_AGENT = "Kolpona/1.11.1 (Android)"

    val accountId: String
        get() = BuildConfig.CLOUDFLARE_ACCOUNT_ID.trim()

    val apiToken: String
        get() = BuildConfig.CLOUDFLARE_API_TOKEN.trim()

    val isConfigured: Boolean
        get() = accountId.isNotBlank() && apiToken.isNotBlank()

    fun runUrl(model: String = MODEL): String =
        "https://$HOST/client/v4/accounts/$accountId/ai/run/$model"
}
