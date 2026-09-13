package com.kolpona.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the correct bearer token per host. Keys are never written to logs or URLs.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host.lowercase()
        val token = when {
            host.contains("huggingface.co") -> HuggingFaceConfig.apiKey
            host.contains("pollinations.ai") -> PollinationsConfig.apiKey
            else -> ""
        }
        val request = if (token.isNotBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
