package com.kolpona.ai.data.api

import com.kolpona.ai.domain.model.GenerationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HuggingFaceApiService(
    private val client: OkHttpClient
) {
    val isConfigured: Boolean get() = HuggingFaceConfig.isConfigured

    suspend fun generateImage(
        prompt: String,
        width: Int,
        height: Int,
        negativePrompt: String? = null,
        model: String = HuggingFaceConfig.IMAGE_MODEL_PRIMARY
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.API)
        val (w, h) = fit(width, height)
        val supportsNegative = model.contains("sdxl", ignoreCase = true)
        postModel(
            model = model,
            prompt = prompt,
            expectVideo = false,
            width = w,
            height = h,
            negativePrompt = negativePrompt.takeIf { supportsNegative },
            steps = if (model.contains("FLUX.1-dev", ignoreCase = true)) 28 else 4
        )
    }

    suspend fun generateVideo(
        prompt: String,
        model: String = HuggingFaceConfig.VIDEO_MODEL
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        postModel(
            model = model,
            prompt = prompt,
            expectVideo = true,
            width = null,
            height = null,
            negativePrompt = null,
            steps = null
        )
    }

    private fun postModel(
        model: String,
        prompt: String,
        expectVideo: Boolean,
        width: Int?,
        height: Int?,
        negativePrompt: String?,
        steps: Int?
    ): ByteArray {
        val rich = buildInputJson(prompt, width, height, negativePrompt, steps)
        val simple = buildInputJson(prompt, null, null, null, null)
        val media = "application/json; charset=utf-8".toMediaType()
        val urls = listOf(
            "${HuggingFaceConfig.ROUTER_BASE}/$model",
            "${HuggingFaceConfig.INFERENCE_BASE}/$model"
        )
        var last: GenerationException? = null
        for (url in urls) {
            for (body in listOf(rich, simple).distinct()) {
                try {
                    return execute(url, body, media, expectVideo)
                } catch (e: GenerationException) {
                    last = e
                }
            }
        }
        throw last ?: GenerationException(GenerationError.API)
    }

    private fun execute(
        url: String,
        bodyJson: String,
        media: okhttp3.MediaType,
        expectVideo: Boolean
    ): ByteArray {
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(media))
            .header("Accept", if (expectVideo) "video/mp4,application/json" else "image/jpeg,image/png,application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", HuggingFaceConfig.USER_AGENT)
            .header("X-Wait-For-Model", "true")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                when (response.code) {
                    in 200..299 -> {
                        if (expectVideo && MediaPayload.looksLikeVideo(bytes)) return bytes
                        if (!expectVideo && MediaPayload.looksLikeImage(bytes)) return bytes
                        val asText = runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
                        val nested = MediaPayload.extractHttpUrl(asText)
                        if (!nested.isNullOrBlank()) {
                            return executeGet(nested, expectVideo)
                        }
                        throw GenerationException(
                            if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
                        )
                    }
                    503 -> throw GenerationException(GenerationError.SERVER)
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
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.TIMEOUT
            )
        } catch (_: UnknownHostException) {
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.NETWORK
            )
        } catch (_: IOException) {
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.SERVER
            )
        } catch (_: Exception) {
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.UNKNOWN
            )
        }
    }

    private fun buildInputJson(
        prompt: String,
        width: Int?,
        height: Int?,
        negativePrompt: String?,
        steps: Int?
    ): String {
        val quoted = jsonEscape(prompt.take(1400))
        val params = buildList {
            if (width != null && height != null) {
                add("\"width\":$width")
                add("\"height\":$height")
            }
            if (!negativePrompt.isNullOrBlank()) {
                add("\"negative_prompt\":\"${jsonEscape(negativePrompt.take(500))}\"")
            }
            if (steps != null) add("\"num_inference_steps\":$steps")
        }
        return if (params.isEmpty()) {
            """{"inputs":"$quoted"}"""
        } else {
            """{"inputs":"$quoted","parameters":{${params.joinToString(",")}}}"""
        }
    }

    private fun fit(width: Int, height: Int): Pair<Int, Int> {
        val maxSide = 1024
        val scale = maxOf(width, height).toFloat() / maxSide
        val w = if (scale > 1f) (width / scale).toInt() else width
        val h = if (scale > 1f) (height / scale).toInt() else height
        return align(w.coerceAtLeast(512)) to align(h.coerceAtLeast(512))
    }

    private fun align(value: Int): Int = value - (value % 8)

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun executeGet(url: String, expectVideo: Boolean): ByteArray {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", HuggingFaceConfig.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw GenerationException(
                    if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
                )
            }
            if (expectVideo && MediaPayload.looksLikeVideo(bytes)) return bytes
            if (!expectVideo && MediaPayload.looksLikeImage(bytes)) return bytes
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
            )
        }
    }
}
