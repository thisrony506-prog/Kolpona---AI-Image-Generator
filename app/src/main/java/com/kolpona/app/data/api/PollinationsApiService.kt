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
 * Tries the authenticated gen.host first, then the public image host.
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
        val encodedPrompt = URLEncoder.encode(prompt.take(1500), StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val primary = buildUrl(
            PollinationsConfig.API_BASE_URL,
            PollinationsConfig.IMAGE_PATH,
            encodedPrompt,
            model,
            width,
            height,
            seed
        )
        val fallback = buildUrl(
            PollinationsConfig.FALLBACK_BASE_URL,
            PollinationsConfig.FALLBACK_PATH,
            encodedPrompt,
            model,
            width,
            height,
            seed
        )
        try {
            execute(primary)
        } catch (first: GenerationException) {
            if (first.error == GenerationError.NETWORK ||
                first.error == GenerationError.TIMEOUT ||
                first.error == GenerationError.RATE_LIMIT
            ) {
                throw first
            }
            try {
                execute(fallback)
            } catch (second: GenerationException) {
                throw first
            }
        }
    }

    private fun buildUrl(
        base: String,
        path: String,
        encodedPrompt: String,
        model: String,
        width: Int,
        height: Int,
        seed: Int?
    ): String {
        val builder = "$base/$path/$encodedPrompt"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("model", model)
            .addQueryParameter("width", width.toString())
            .addQueryParameter("height", height.toString())
            .addQueryParameter("nologo", "true")
            .addQueryParameter("private", "true")
            .addQueryParameter("referrer", "kolpona")
        if (seed != null) builder.addQueryParameter("seed", seed.toString())
        return builder.build().toString()
    }

    private fun execute(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "image/*,*/*")
            .header("User-Agent", "Kolpona/1.1.0 (Android)")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val body = response.body ?: throw GenerationException(GenerationError.EMPTY_RESPONSE)
                        val bytes = body.bytes()
                        if (!looksLikeImage(bytes)) {
                            throw GenerationException(GenerationError.EMPTY_RESPONSE)
                        }
                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        if (contentType.contains("json") || contentType.contains("text/html")) {
                            throw GenerationException(GenerationError.API)
                        }
                        return bytes
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
        } catch (_: SocketTimeoutException) {
            throw GenerationException(GenerationError.TIMEOUT)
        } catch (_: UnknownHostException) {
            throw GenerationException(GenerationError.NETWORK)
        } catch (_: IOException) {
            throw GenerationException(GenerationError.NETWORK)
        } catch (_: Exception) {
            throw GenerationException(GenerationError.UNKNOWN)
        }
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 24) return false
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte()
        val jpg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val gif = bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
        val webp = bytes.size > 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[8] == 'W'.code.toByte()
        return png || jpg || gif || webp
    }
}
