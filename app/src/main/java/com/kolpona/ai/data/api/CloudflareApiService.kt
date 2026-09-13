package com.kolpona.ai.data.api

import com.kolpona.ai.domain.model.GenerationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class CloudflareApiService(
    private val client: OkHttpClient
) {
    val isConfigured: Boolean get() = CloudflareConfig.isConfigured

    suspend fun generateImage(
        prompt: String,
        @Suppress("UNUSED_PARAMETER") width: Int,
        @Suppress("UNUSED_PARAMETER") height: Int
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.API)
        val body = JSONObject()
            .put("prompt", prompt.take(1400))
            .put("steps", 4)
            .toString()
        val media = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(CloudflareConfig.runUrl())
            .post(body.toRequestBody(media))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${CloudflareConfig.apiToken}")
            .header("User-Agent", CloudflareConfig.USER_AGENT)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                when (response.code) {
                    in 200..299 -> parseImage(bytes)
                    429 -> throw GenerationException(GenerationError.RATE_LIMIT)
                    400, 422 -> throw GenerationException(GenerationError.INVALID_PROMPT)
                    401, 403 -> throw GenerationException(GenerationError.API)
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
            throw GenerationException(GenerationError.SERVER)
        } catch (_: Exception) {
            throw GenerationException(GenerationError.UNKNOWN)
        }
    }

    private fun parseImage(bytes: ByteArray): ByteArray {
        if (MediaPayload.looksLikeImage(bytes)) return bytes
        val text = runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
        MediaPayload.decodeEmbeddedImage(text)?.let { decoded ->
            if (MediaPayload.looksLikeImage(decoded)) return decoded
        }
        throw GenerationException(GenerationError.EMPTY_RESPONSE)
    }
}
