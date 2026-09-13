package com.kolpona.ai.data.api

import com.kolpona.ai.domain.model.GenerationError
import com.kolpona.ai.prompt.LanguageScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class CloudflareApiService(
    private val client: OkHttpClient
) {
    val isConfigured: Boolean get() = CloudflareConfig.isConfigured

    private val chatClient: OkHttpClient = client.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

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

    suspend fun rewriteToEnglish(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val system = "Rewrite the user's request as a faithful English prompt for an image or video generator. " +
            "Keep the exact meaning, subjects, clothing, places, colors, camera, and actions. " +
            "Keep cultural names such as sari, lungi, panjabi, kurta, rickshaw, and city names. " +
            "Do not add quality slogans such as 8k, ultra realistic, masterpiece, or highly detailed. " +
            "Output only the rewritten prompt."
        val media = "application/json; charset=utf-8".toMediaType()
        for (model in CloudflareConfig.CHAT_MODELS) {
            val body = JSONObject()
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", prompt.take(800)))
                )
                .put("max_tokens", 220)
                .toString()
            val request = Request.Builder()
                .url(CloudflareConfig.runUrl(model))
                .post(body.toRequestBody(media))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${CloudflareConfig.apiToken}")
                .header("User-Agent", CloudflareConfig.USER_AGENT)
                .build()
            val text = runCatching {
                chatClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull() ?: continue
            val rewritten = MediaPayload.chatText(text).orEmpty().trim().trim('"')
            if (rewritten.length >= 3 &&
                LanguageScripts.nonLatinRatio(rewritten) < 0.25f &&
                !rewritten.equals(prompt.trim(), ignoreCase = true)
            ) {
                return@withContext rewritten.take(1400)
            }
        }
        null
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
