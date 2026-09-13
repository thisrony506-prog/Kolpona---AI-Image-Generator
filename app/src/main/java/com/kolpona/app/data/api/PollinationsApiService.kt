package com.kolpona.app.data.api

import com.kolpona.app.domain.model.GenerationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class GenerationException(val error: GenerationError) : Exception()

/**
 * Centralized Pollinations image client.
 * UI layers must never call the network directly.
 */
class PollinationsApiService(
    private val client: OkHttpClient
) {
    suspend fun generateImage(
        prompt: String,
        model: String,
        width: Int,
        height: Int,
        seed: Int? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val urlBuilder = "${PollinationsConfig.API_BASE_URL}/${PollinationsConfig.IMAGE_PATH}/$encodedPrompt"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("model", model)
            .addQueryParameter("width", width.toString())
            .addQueryParameter("height", height.toString())
            .addQueryParameter("nologo", "true")
            .addQueryParameter("private", "true")
        if (seed != null) {
            urlBuilder.addQueryParameter("seed", seed.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .header("Accept", "image/*")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val body = response.body ?: throw GenerationException(GenerationError.EMPTY_RESPONSE)
                        val bytes = body.bytes()
                        if (bytes.size < 128) {
                            throw GenerationException(GenerationError.EMPTY_RESPONSE)
                        }
                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        if (contentType.contains("json") || contentType.contains("text/plain")) {
                            throw GenerationException(GenerationError.API)
                        }
                        bytes
                    }
                    400, 422 -> throw GenerationException(GenerationError.INVALID_PROMPT)
                    401, 403 -> throw GenerationException(GenerationError.API)
                    429 -> throw GenerationException(GenerationError.RATE_LIMIT)
                    in 500..599 -> throw GenerationException(GenerationError.SERVER)
                    else -> throw GenerationException(GenerationError.API)
                }
            }
        } catch (e: GenerationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw GenerationException(GenerationError.TIMEOUT)
        } catch (e: UnknownHostException) {
            throw GenerationException(GenerationError.NETWORK)
        } catch (e: IOException) {
            throw GenerationException(GenerationError.NETWORK)
        } catch (e: Exception) {
            throw GenerationException(GenerationError.UNKNOWN)
        }
    }
}
