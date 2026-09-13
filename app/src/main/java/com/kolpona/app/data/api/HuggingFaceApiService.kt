package com.kolpona.app.data.api

import com.kolpona.app.domain.model.GenerationError
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

    suspend fun generateImage(prompt: String, width: Int, height: Int): ByteArray =
        withContext(Dispatchers.IO) {
            if (!isConfigured) throw GenerationException(GenerationError.API)
            val models = listOf(
                HuggingFaceConfig.IMAGE_MODEL_PRIMARY,
                HuggingFaceConfig.IMAGE_MODEL_FALLBACK
            )
            var last: GenerationException? = null
            for (model in models) {
                try {
                    return@withContext postModel(
                        model = model,
                        prompt = prompt,
                        expectVideo = false,
                        width = width,
                        height = height
                    )
                } catch (e: GenerationException) {
                    last = e
                    if (e.error == GenerationError.INVALID_PROMPT ||
                        e.error == GenerationError.NETWORK ||
                        e.error == GenerationError.TIMEOUT
                    ) {
                        throw e
                    }
                }
            }
            throw last ?: GenerationException(GenerationError.API)
        }

    suspend fun generateVideo(prompt: String): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        postModel(
            model = HuggingFaceConfig.VIDEO_MODEL,
            prompt = prompt,
            expectVideo = true,
            width = null,
            height = null
        )
    }

    private fun postModel(
        model: String,
        prompt: String,
        expectVideo: Boolean,
        width: Int?,
        height: Int?
    ): ByteArray {
        val bodyJson = buildInputJson(prompt, width, height)
        val media = "application/json; charset=utf-8".toMediaType()
        val urls = listOf(
            "${HuggingFaceConfig.ROUTER_BASE}/$model",
            "${HuggingFaceConfig.INFERENCE_BASE}/$model"
        )
        var last: GenerationException? = null
        for (url in urls) {
            try {
                return execute(url, bodyJson, media, expectVideo, waitForModel = true)
            } catch (e: GenerationException) {
                last = e
            }
        }
        throw last ?: GenerationException(GenerationError.API)
    }

    private fun execute(
        url: String,
        bodyJson: String,
        media: okhttp3.MediaType,
        expectVideo: Boolean,
        waitForModel: Boolean
    ): ByteArray {
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(media))
            .header("Accept", if (expectVideo) "video/mp4,application/json" else "image/jpeg,image/png,application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Kolpona/1.3.0 (Android)")
            .apply {
                if (waitForModel) header("X-Wait-For-Model", "true")
            }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                when (response.code) {
                    in 200..299 -> {
                        if (expectVideo) {
                            if (!looksLikeVideo(bytes)) throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                        } else if (!looksLikeImage(bytes)) {
                            throw GenerationException(GenerationError.EMPTY_RESPONSE)
                        }
                        return bytes
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
            throw GenerationException(GenerationError.TIMEOUT)
        } catch (_: UnknownHostException) {
            throw GenerationException(GenerationError.NETWORK)
        } catch (_: IOException) {
            throw GenerationException(GenerationError.NETWORK)
        } catch (_: Exception) {
            throw GenerationException(GenerationError.UNKNOWN)
        }
    }

    private fun buildInputJson(prompt: String, width: Int?, height: Int?): String {
        val quoted = jsonEscape(prompt.take(1400))
        return if (width != null && height != null) {
            """{"inputs":"$quoted","parameters":{"width":$width,"height":$height}}"""
        } else {
            """{"inputs":"$quoted"}"""
        }
    }

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

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 24) return false
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val jpg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val webp = bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()
        return png || jpg || webp
    }

    private fun looksLikeVideo(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val ftyp = bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        val webm = bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte()
        return ftyp || webm
    }
}
