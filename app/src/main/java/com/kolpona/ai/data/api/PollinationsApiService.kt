package com.kolpona.ai.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kolpona.ai.domain.model.GenerationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class GenerationException(val error: GenerationError) : Exception()

class PollinationsApiService(
    private val client: OkHttpClient
) {
    private val videoClient: OkHttpClient = client.newBuilder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(200, TimeUnit.SECONDS)
        .build()

    suspend fun generateImage(
        prompt: String,
        model: String,
        width: Int,
        height: Int,
        seed: Int? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val encodedPrompt = encode(prompt)
        val key = PollinationsConfig.apiKey
        val primary = buildUrl(
            base = PollinationsConfig.API_BASE_URL,
            path = PollinationsConfig.IMAGE_PATH,
            encodedPrompt = encodedPrompt,
            model = model,
            width = width,
            height = height,
            seed = seed,
            key = key,
            extra = emptyMap()
        )
        if (key.isNotBlank()) {
            execute(primary, expectVideo = false)
        } else {
            val fallback = buildUrl(
                base = PollinationsConfig.FALLBACK_BASE_URL,
                path = PollinationsConfig.FALLBACK_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = seed,
                key = "",
                extra = emptyMap()
            )
            val raw = try {
                execute(primary, expectVideo = false)
            } catch (first: GenerationException) {
                if (first.error == GenerationError.NETWORK ||
                    first.error == GenerationError.TIMEOUT ||
                    first.error == GenerationError.RATE_LIMIT ||
                    first.error == GenerationError.INVALID_PROMPT
                ) {
                    throw first
                }
                try {
                    execute(fallback, expectVideo = false)
                } catch (_: GenerationException) {
                    throw first
                }
            }
            stripWatermark(raw)
        }
    }

    suspend fun generateVideo(
        prompt: String,
        width: Int,
        height: Int,
        model: String = PollinationsConfig.VIDEO_MODEL
    ): ByteArray = withContext(Dispatchers.IO) {
        val encodedPrompt = encode(prompt.take(800))
        val key = PollinationsConfig.apiKey
        val ratio = ratioLabel(width, height)
        val attempts = videoAttempts(encodedPrompt, model, width, height, ratio, key)
        var last: GenerationException? = null
        for (url in attempts) {
            try {
                return@withContext execute(url, expectVideo = true, http = videoClient)
            } catch (e: GenerationException) {
                last = e
            }
        }
        throw when (last?.error) {
            GenerationError.NETWORK -> GenerationException(GenerationError.VIDEO_UNAVAILABLE)
            else -> last ?: GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
    }

    private fun videoAttempts(
        encodedPrompt: String,
        model: String,
        width: Int,
        height: Int,
        ratio: String,
        key: String
    ): List<String> {
        return listOf(
            buildUrl(
                base = PollinationsConfig.API_BASE_URL,
                path = PollinationsConfig.IMAGE_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = null,
                key = key,
                extra = mapOf("duration" to "5", "aspectRatio" to ratio, "format" to "mp4"),
                includeSize = true
            ),
            buildUrl(
                base = PollinationsConfig.API_BASE_URL,
                path = PollinationsConfig.IMAGE_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = null,
                key = key,
                extra = mapOf("duration" to "5", "format" to "mp4"),
                includeSize = false
            ),
            buildUrl(
                base = PollinationsConfig.API_BASE_URL,
                path = PollinationsConfig.VIDEO_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = null,
                key = key,
                extra = mapOf("duration" to "5", "aspectRatio" to ratio, "format" to "mp4"),
                includeSize = true
            ),
            buildUrl(
                base = PollinationsConfig.API_BASE_URL,
                path = PollinationsConfig.VIDEO_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = null,
                key = key,
                extra = mapOf("duration" to "5"),
                includeSize = false
            ),
            buildUrl(
                base = PollinationsConfig.FALLBACK_BASE_URL,
                path = PollinationsConfig.FALLBACK_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = null,
                key = key,
                extra = mapOf("duration" to "5", "aspectRatio" to ratio),
                includeSize = true
            ),
            buildUrl(
                base = PollinationsConfig.VIDEO_FALLBACK_BASE_URL,
                path = PollinationsConfig.FALLBACK_PATH,
                encodedPrompt = encodedPrompt,
                model = model,
                width = width,
                height = height,
                seed = null,
                key = key,
                extra = mapOf("duration" to "5"),
                includeSize = true
            )
        ).distinct()
    }

    private fun encode(prompt: String): String =
        URLEncoder.encode(prompt.take(1500), StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun ratioLabel(width: Int, height: Int): String {
        val r = width.toFloat() / height.coerceAtLeast(1).toFloat()
        return when {
            r > 1.4f -> "16:9"
            r < 0.7f -> "9:16"
            kotlin.math.abs(r - 0.8f) < 0.06f -> "4:5"
            r > 1.15f -> "4:3"
            r < 0.9f -> "3:4"
            else -> "1:1"
        }
    }

    private fun buildUrl(
        base: String,
        path: String,
        encodedPrompt: String,
        model: String,
        width: Int,
        height: Int,
        seed: Int?,
        key: String,
        extra: Map<String, String>,
        includeSize: Boolean = true
    ): String {
        val builder = "$base/$path/$encodedPrompt"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("model", model)
            .addQueryParameter("nologo", "true")
            .addQueryParameter("nofeed", "true")
            .addQueryParameter("private", "true")
            .addQueryParameter("enhance", "false")
            .addQueryParameter("safe", "false")
            .addQueryParameter("referrer", "kolpona")
        if (includeSize) {
            builder.addQueryParameter("width", width.toString())
            builder.addQueryParameter("height", height.toString())
        }
        extra.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        if (seed != null) builder.addQueryParameter("seed", seed.toString())
        if (key.isNotBlank()) builder.addQueryParameter("token", key)
        return builder.build().toString()
    }

    private fun execute(
        url: String,
        expectVideo: Boolean,
        http: OkHttpClient = client,
        followJson: Boolean = true
    ): ByteArray {
        val accept = if (expectVideo) {
            "video/mp4,video/webm,video/*,application/json,application/octet-stream"
        } else {
            "image/jpeg,image/png,image/webp,image/*,application/json"
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", accept)
            .header("User-Agent", PollinationsConfig.USER_AGENT)
        val key = PollinationsConfig.apiKey
        if (key.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $key")
        }
        val request = requestBuilder.build()
        try {
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val body = response.body ?: throw GenerationException(GenerationError.EMPTY_RESPONSE)
                        val bytes = body.bytes()
                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        if (expectVideo && MediaPayload.looksLikeVideo(bytes)) return bytes
                        if (!expectVideo && MediaPayload.looksLikeImage(bytes)) return bytes
                        val asText = runCatching { bytes.toString(StandardCharsets.UTF_8) }.getOrNull().orEmpty()
                        val nested = if (followJson) MediaPayload.extractHttpUrl(asText) else null
                        if (!nested.isNullOrBlank() && nested != url) {
                            return execute(nested, expectVideo, http, followJson = false)
                        }
                        if (contentType.contains("json") || contentType.contains("text/html") || asText.trimStart().startsWith("{")) {
                            throw GenerationException(
                                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.API
                            )
                        }
                        throw GenerationException(
                            if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
                        )
                    }
                    400, 422 -> throw GenerationException(GenerationError.INVALID_PROMPT)
                    401, 403 -> throw GenerationException(GenerationError.API)
                    404 -> throw GenerationException(
                        if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.API
                    )
                    429 -> throw GenerationException(GenerationError.RATE_LIMIT)
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

    private fun stripWatermark(bytes: ByteArray): ByteArray {
        return try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val crop = (bmp.height * 0.055f).toInt().coerceIn(32, 64)
            if (bmp.height <= crop + 128) return bytes
            val trimmed = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height - crop)
            val out = ByteArrayOutputStream()
            trimmed.compress(Bitmap.CompressFormat.JPEG, 95, out)
            if (!bmp.isRecycled) bmp.recycle()
            if (!trimmed.isRecycled) trimmed.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            bytes
        }
    }
}
