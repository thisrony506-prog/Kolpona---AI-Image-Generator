package com.kolpona.app.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Pollinations bearer token when a key is configured.
 * The key is never written to logs, URLs, or error messages.
 */
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = PollinationsConfig.apiKey
        val request = if (key.isNotBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $key")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
