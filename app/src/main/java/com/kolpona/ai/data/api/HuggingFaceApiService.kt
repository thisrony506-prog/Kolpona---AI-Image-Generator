package com.kolpona.ai.data.api

import android.util.Base64
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

class HuggingFaceApiService(
    private val client: OkHttpClient
) {
    val isConfigured: Boolean get() = HuggingFaceConfig.isConfigured

    private val videoClient: OkHttpClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(260, TimeUnit.SECONDS)
        .build()

    private val chatClient: OkHttpClient = client.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun generateImage(
        prompt: String,
        width: Int,
        height: Int,
        negativePrompt: String? = null,
        model: String = HuggingFaceConfig.IMAGE_MODEL_DEV
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.API)
        val (w, h) = fit(width, height)
        postModel(
            model = model,
            prompt = prompt,
            expectVideo = false,
            width = w,
            height = h,
            negativePrompt = negativePrompt,
            steps = HuggingFaceConfig.IMAGE_STEPS,
            guidance = HuggingFaceConfig.IMAGE_GUIDANCE
        )
    }

    suspend fun generateVideo(
        prompt: String,
        model: String = HuggingFaceConfig.VIDEO_MODEL,
        width: Int? = null,
        height: Int? = null,
        negativePrompt: String? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        val media = "application/json; charset=utf-8".toMediaType()
        val urls = videoUrls(model).map { withFalQueue(it) }
        var last: GenerationException? = null
        for (url in urls) {
            for (body in videoBodiesFor(url, prompt, width, height)) {
                try {
                    return@withContext execute(url, body, media, expectVideo = true, allowRetry = true)
                } catch (e: GenerationException) {
                    last = e
                    if (e.error == GenerationError.RATE_LIMIT || e.error == GenerationError.NETWORK) throw e
                    if (e.error != GenerationError.INVALID_PROMPT) break
                }
            }
        }
        throw last ?: GenerationException(GenerationError.VIDEO_UNAVAILABLE)
    }

    suspend fun generateVideoFromImage(
        prompt: String,
        jpeg: ByteArray
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        val quoted = jsonEscape(prompt.take(800))
        val dataUri = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val imageJson = jsonEscape(dataUri)
        val media = "application/json; charset=utf-8".toMediaType()
        val bodies = listOf(
            """{"prompt":"$quoted","image_url":"$imageJson"}""",
            """{"prompt":"$quoted","image":"$imageJson"}""",
            """{"inputs":"$quoted","image":"$imageJson"}"""
        )
        val urls = listOf(
            HuggingFaceConfig.I2V_URL,
            "${HuggingFaceConfig.ROUTER_HOST}/fal-ai/fal-ai/wan/v2.2-5b/image-to-video"
        ).map { withFalQueue(it) }
        var last: GenerationException? = null
        for (url in urls) {
            for (body in bodies) {
                try {
                    return@withContext execute(url, body, media, expectVideo = true, allowRetry = true)
                } catch (e: GenerationException) {
                    last = e
                    if (e.error == GenerationError.RATE_LIMIT || e.error == GenerationError.NETWORK) throw e
                    if (e.error != GenerationError.INVALID_PROMPT) break
                }
            }
        }
        throw last ?: GenerationException(GenerationError.VIDEO_UNAVAILABLE)
    }

    suspend fun rewriteToEnglish(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        chatRewrite(prompt)?.let { return@withContext it }
        nllbTranslate(prompt)
    }

    private fun chatRewrite(prompt: String): String? {
        val media = "application/json; charset=utf-8".toMediaType()
        val system = "Rewrite the user's request as a faithful English prompt for an image or video generator. " +
            "Keep the exact meaning, subjects, clothing, places, colors, camera, and actions. " +
            "Keep cultural names such as sari, lungi, panjabi, kurta, rickshaw, and city names. " +
            "Do not add quality slogans such as 8k, ultra realistic, masterpiece, or highly detailed. " +
            "Output only the rewritten prompt."
        for (model in HuggingFaceConfig.CHAT_MODELS) {
            val payload = JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", prompt.take(800)))
                )
                .put("max_tokens", 220)
                .put("temperature", 0.1)
                .toString()
            val request = Request.Builder()
                .url(HuggingFaceConfig.CHAT_URL)
                .post(payload.toRequestBody(media))
                .header("Authorization", "Bearer ${HuggingFaceConfig.apiKey}")
                .header("Content-Type", "application/json")
                .header("User-Agent", HuggingFaceConfig.USER_AGENT)
                .build()
            val text = runCatching {
                chatClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull() ?: continue
            val rewritten = MediaPayload.chatText(text).orEmpty().trim().trim('"')
            if (isUsefulRewrite(prompt, rewritten)) return rewritten.take(1400)
        }
        return null
    }

    private fun nllbTranslate(prompt: String): String? {
        val src = nllbSource(prompt) ?: return null
        val media = "application/json; charset=utf-8".toMediaType()
        val body = JSONObject()
            .put("inputs", prompt.take(800))
            .put(
                "parameters",
                JSONObject()
                    .put("src_lang", src)
                    .put("tgt_lang", "eng_Latn")
            )
            .toString()
        val urls = listOf(
            "${HuggingFaceConfig.INFERENCE_BASE}/${HuggingFaceConfig.TRANSLATE_MODEL}",
            "${HuggingFaceConfig.ROUTER_BASE}/${HuggingFaceConfig.TRANSLATE_MODEL}"
        )
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(media))
                .header("Authorization", "Bearer ${HuggingFaceConfig.apiKey}")
                .header("Content-Type", "application/json")
                .header("X-Wait-For-Model", "true")
                .header("User-Agent", HuggingFaceConfig.USER_AGENT)
                .build()
            val text = runCatching {
                chatClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull() ?: continue
            val rewritten = MediaPayload.chatText(text).orEmpty().trim().trim('"')
            if (isUsefulRewrite(prompt, rewritten)) return rewritten.take(1400)
        }
        return null
    }

    private fun postModel(
        model: String,
        prompt: String,
        expectVideo: Boolean,
        width: Int?,
        height: Int?,
        negativePrompt: String?,
        steps: Int?,
        guidance: Double? = null
    ): ByteArray {
        val rich = buildInputJson(prompt, width, height, negativePrompt, steps, guidance)
        val simple = buildInputJson(prompt, null, null, null, null)
        val media = "application/json; charset=utf-8".toMediaType()
        val urls = listOf(
            "${HuggingFaceConfig.INFERENCE_BASE}/$model",
            "${HuggingFaceConfig.ROUTER_BASE}/$model"
        )
        var last: GenerationException? = null
        for (url in urls) {
            for (body in listOf(rich, simple).distinct()) {
                try {
                    return execute(url, body, media, expectVideo, allowRetry = true)
                } catch (e: GenerationException) {
                    last = e
                }
            }
        }
        throw last ?: GenerationException(
            if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.API
        )
    }

    private fun execute(
        url: String,
        bodyJson: String,
        media: okhttp3.MediaType,
        expectVideo: Boolean,
        allowRetry: Boolean
    ): ByteArray {
        val http = if (expectVideo) videoClient else client
        val builder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(media))
            .header("Accept", if (expectVideo) "video/mp4,application/json" else "image/jpeg,image/png,application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${HuggingFaceConfig.apiKey}")
            .header("User-Agent", HuggingFaceConfig.USER_AGENT)
            .header("X-Wait-For-Model", "true")
        if (expectVideo && url.contains("replicate", ignoreCase = true)) {
            builder.header("Prefer", "wait")
        }
        val request = builder.build()
        try {
            http.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                when (response.code) {
                    in 200..299 -> {
                        val location = response.header("Location")
                        val asText = runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
                        val wait = MediaPayload.loadingWaitSeconds(asText)
                        if (wait != null && allowRetry) {
                            Thread.sleep(wait * 1000L)
                            return execute(url, bodyJson, media, expectVideo, allowRetry = false)
                        }
                        if (!location.isNullOrBlank() && (bytes.isEmpty() || response.code == 202)) {
                            return pollUntilMedia(location, expectVideo, http)
                        }
                        return interpretSuccess(bytes, expectVideo, http, url)
                    }
                    503, 529 -> {
                        if (allowRetry) {
                            val wait = MediaPayload.loadingWaitSeconds(
                                runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
                            ) ?: 12
                            Thread.sleep(wait * 1000L)
                            return execute(url, bodyJson, media, expectVideo, allowRetry = false)
                        }
                        throw GenerationException(
                            if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.SERVER
                        )
                    }
                    429 -> throw GenerationException(GenerationError.RATE_LIMIT)
                    404, 410 -> throw GenerationException(
                        if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.API
                    )
                    400, 422 -> throw GenerationException(GenerationError.INVALID_PROMPT)
                    401, 403 -> throw GenerationException(
                        if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.API
                    )
                    in 500..599 -> throw GenerationException(GenerationError.SERVER)
                    else -> throw GenerationException(
                        if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.API
                    )
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
        } catch (_: InterruptedException) {
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.TIMEOUT
            )
        } catch (_: Exception) {
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.UNKNOWN
            )
        }
    }

    private fun interpretSuccess(
        bytes: ByteArray,
        expectVideo: Boolean,
        http: OkHttpClient,
        submitUrl: String = ""
    ): ByteArray {
        if (expectVideo && MediaPayload.looksLikeVideo(bytes)) return bytes
        if (!expectVideo && MediaPayload.looksLikeImage(bytes)) return bytes
        val asText = runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
        MediaPayload.decodeEmbeddedImage(asText)?.let { decoded ->
            if (expectVideo && MediaPayload.looksLikeVideo(decoded)) return decoded
            if (!expectVideo && MediaPayload.looksLikeImage(decoded)) return decoded
        }
        if (expectVideo && isFalQueue(asText)) {
            return pollFalQueue(asText, submitUrl, http)
        }
        if (expectVideo && isWavespeedSubmit(asText)) {
            return pollWavespeed(asText, submitUrl, http)
        }
        val statusUrl = MediaPayload.queueStatusUrl(asText)
        if (statusUrl != null) {
            val pollUrl = rewriteProviderPollUrl(submitUrl, statusUrl)
            return pollUntilMedia(pollUrl, expectVideo, http)
        }
        val nested = MediaPayload.extractHttpUrl(asText)
        if (!nested.isNullOrBlank()) {
            return executeGet(nested, expectVideo, http)
        }
        throw GenerationException(
            if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
        )
    }

    private fun pollUntilMedia(url: String, expectVideo: Boolean, http: OkHttpClient): ByteArray {
        repeat(50) { index ->
            if (index > 0) Thread.sleep(4_000L)
            val request = authorizedGet(url)
            http.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (!response.isSuccessful) return@use
                if (expectVideo && MediaPayload.looksLikeVideo(bytes)) return bytes
                if (!expectVideo && MediaPayload.looksLikeImage(bytes)) return bytes
                val text = runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
                if (MediaPayload.queueFailed(text)) {
                    throw GenerationException(
                        if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
                    )
                }
                MediaPayload.decodeEmbeddedImage(text)?.let { decoded ->
                    if (expectVideo && MediaPayload.looksLikeVideo(decoded)) return decoded
                    if (!expectVideo && MediaPayload.looksLikeImage(decoded)) return decoded
                }
                val mediaUrl = MediaPayload.extractHttpUrl(text)
                if (!mediaUrl.isNullOrBlank() && mediaUrl != url && MediaPayload.isMediaFileUrl(mediaUrl)) {
                    return executeGet(mediaUrl, expectVideo, http)
                }
                if (MediaPayload.queueDone(text)) {
                    val result = MediaPayload.queueResponseUrl(text)
                    if (!result.isNullOrBlank() && result != url) {
                        return executeGet(result, expectVideo, http)
                    }
                }
            }
        }
        throw GenerationException(
            if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.TIMEOUT
        )
    }

    private fun videoUrls(model: String): List<String> {
        val fromHub = runCatching { fetchProviderUrls(model, "text-to-video") }.getOrNull().orEmpty()
        val hardcoded = when {
            model.contains("A14B", ignoreCase = true) -> listOf(
                "${HuggingFaceConfig.ROUTER_HOST}/fal-ai/fal-ai/wan/v2.2-a14b/text-to-video",
                "${HuggingFaceConfig.ROUTER_HOST}/replicate/v1/models/wan-video/wan-2.2-t2v-fast/predictions",
                "${HuggingFaceConfig.ROUTER_HOST}/wavespeed/api/v3/wavespeed-ai/wan-2.2/t2v-720p"
            )
            model.contains("Wan2.2", ignoreCase = true) -> listOf(
                "${HuggingFaceConfig.ROUTER_HOST}/fal-ai/fal-ai/wan/v2.2-5b/text-to-video",
                "${HuggingFaceConfig.ROUTER_HOST}/replicate/v1/models/wan-video/wan-2.2-5b-fast/predictions",
                "${HuggingFaceConfig.ROUTER_HOST}/wavespeed/api/v3/wavespeed-ai/wan-2.2/t2v-5b-720p"
            )
            model.contains("Wan2.1", ignoreCase = true) -> listOf(
                "${HuggingFaceConfig.ROUTER_HOST}/fal-ai/fal-ai/wan/v2.1/1.3b/text-to-video"
            )
            model.contains("Hunyuan", ignoreCase = true) -> listOf(
                "${HuggingFaceConfig.ROUTER_HOST}/fal-ai/fal-ai/hunyuan-video",
                "${HuggingFaceConfig.ROUTER_HOST}/wavespeed/api/v3/wavespeed-ai/hunyuan-video/t2v"
            )
            else -> emptyList()
        }
        return (hardcoded + fromHub).distinct()
    }

    private fun fetchProviderUrls(model: String, task: String): List<String> {
        val request = Request.Builder()
            .url("https://huggingface.co/api/models/$model?expand[]=inferenceProviderMapping")
            .get()
            .header("User-Agent", HuggingFaceConfig.USER_AGENT)
            .build()
        val text = chatClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        val mapping = runCatching { JSONObject(text).optJSONObject("inferenceProviderMapping") }
            .getOrNull() ?: return emptyList()
        val urls = mutableListOf<String>()
        val keys = mapping.keys()
        while (keys.hasNext()) {
            val provider = keys.next()
            val entry = mapping.optJSONObject(provider) ?: continue
            if (entry.optString("status") != "live") continue
            if (entry.optString("task") != task) continue
            val providerId = entry.optString("providerId")
            if (providerId.isBlank()) continue
            urls += providerUrl(provider, providerId)
        }
        return urls
    }

    private fun videoBodiesFor(
        url: String,
        prompt: String,
        width: Int?,
        height: Int?
    ): List<String> {
        val quoted = jsonEscape(prompt.take(1400))
        val promptOnly = """{"prompt":"$quoted"}"""
        val replicate = """{"input":{"prompt":"$quoted"}}"""
        val aspect = if (width != null && height != null && width >= height) "16:9" else "9:16"
        val fal = """{"prompt":"$quoted","aspect_ratio":"$aspect","resolution":"720p","enable_prompt_expansion":false}"""
        return when {
            url.contains("replicate", ignoreCase = true) -> listOf(replicate)
            url.contains("wavespeed", ignoreCase = true) -> listOf(promptOnly)
            else -> listOf(fal, promptOnly)
        }.distinct()
    }

    private fun providerUrl(provider: String, providerId: String): String {
        val host = HuggingFaceConfig.ROUTER_HOST
        return when (provider.lowercase()) {
            "fal-ai" -> "$host/fal-ai/$providerId"
            "replicate" -> "$host/replicate/v1/models/$providerId/predictions"
            "wavespeed" -> "$host/wavespeed/api/v3/$providerId"
            else -> "$host/$provider/$providerId"
        }
    }

    private fun withFalQueue(url: String): String {
        if (!url.contains("fal-ai", ignoreCase = true)) return url
        if (url.contains("_subdomain=")) return url
        return if (url.contains("?")) "$url&_subdomain=queue" else "$url?_subdomain=queue"
    }

    private fun isFalQueue(text: String): Boolean {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        val id = json.optString("request_id").ifBlank { json.optString("requestId") }
        val response = json.optString("response_url").ifBlank { json.optString("responseUrl") }
        return id.isNotBlank() && response.startsWith("http")
    }

    private fun pollFalQueue(submitText: String, submitUrl: String, http: OkHttpClient): ByteArray {
        val json = JSONObject(submitText.trim())
        val responseUrl = json.optString("response_url").ifBlank { json.optString("responseUrl") }
        if (!responseUrl.startsWith("http")) {
            throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
        val (statusUrl, resultUrl) = falQueueUrls(submitUrl, responseUrl)
        try {
            repeat(180) { index ->
                if (index > 0) Thread.sleep(2_000L)
                val statusText = getText(statusUrl, http) ?: return@repeat
                if (MediaPayload.queueFailed(statusText)) {
                    throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                }
                val status = runCatching { JSONObject(statusText).optString("status") }.getOrNull().orEmpty()
                if (status.equals("COMPLETED", ignoreCase = true) ||
                    status.equals("complete", ignoreCase = true) ||
                    status.equals("OK", ignoreCase = true)
                ) {
                    val resultText = getText(resultUrl, http)
                        ?: throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                    val mediaUrl = MediaPayload.extractHttpUrl(resultText)
                    if (!mediaUrl.isNullOrBlank() && MediaPayload.isMediaFileUrl(mediaUrl)) {
                        return executeGet(mediaUrl, expectVideo = true, http)
                    }
                    MediaPayload.decodeEmbeddedImage(resultText)?.let { decoded ->
                        if (MediaPayload.looksLikeVideo(decoded)) return decoded
                    }
                    throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                }
            }
        } catch (e: GenerationException) {
            throw e
        } catch (_: Exception) {
            throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
        throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
    }

    private fun falQueueUrls(submitUrl: String, responseUrl: String): Pair<String, String> {
        val submit = java.net.URI(submitUrl)
        val path = java.net.URI(responseUrl).path.orEmpty()
        val query = submit.rawQuery?.let { "?$it" }.orEmpty()
        val base = if (submit.host.equals("router.huggingface.co", ignoreCase = true)) {
            "${submit.scheme}://${submit.host}/fal-ai"
        } else {
            "${submit.scheme}://${submit.host}"
        }
        val resultUrl = "$base$path$query"
        return "$resultUrl".let { result ->
            val trimmed = result.substringBefore("?").removeSuffix("/") + query
            val status = result.substringBefore("?").removeSuffix("/") + "/status" + query
            status to trimmed
        }
    }

    private fun isWavespeedSubmit(text: String): Boolean {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        val data = json.optJSONObject("data") ?: return false
        val get = data.optJSONObject("urls")?.optString("get").orEmpty()
        return get.startsWith("http") || data.optString("id").isNotBlank()
    }

    private fun pollWavespeed(submitText: String, submitUrl: String, http: OkHttpClient): ByteArray {
        val json = JSONObject(submitText.trim())
        val data = json.optJSONObject("data") ?: throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        val getUrl = data.optJSONObject("urls")?.optString("get").orEmpty()
        if (!getUrl.startsWith("http")) {
            throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
        val pollUrl = rewriteProviderPollUrl(submitUrl, getUrl)
        try {
            repeat(180) { index ->
                if (index > 0) Thread.sleep(2_000L)
                val statusText = getText(pollUrl, http) ?: return@repeat
                if (MediaPayload.queueFailed(statusText)) {
                    throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                }
                if (MediaPayload.queueDone(statusText)) {
                    val mediaUrl = MediaPayload.extractHttpUrl(statusText)
                    if (!mediaUrl.isNullOrBlank() && MediaPayload.isMediaFileUrl(mediaUrl)) {
                        return executeGet(mediaUrl, expectVideo = true, http)
                    }
                    throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                }
            }
        } catch (e: GenerationException) {
            throw e
        } catch (_: Exception) {
            throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
        throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
    }

    private fun rewriteProviderPollUrl(submitUrl: String, responseUrl: String): String {
        if (!responseUrl.startsWith("http")) return submitUrl
        val submit = runCatching { java.net.URI(submitUrl) }.getOrNull() ?: return responseUrl
        val response = runCatching { java.net.URI(responseUrl) }.getOrNull() ?: return responseUrl
        if (response.host.equals("router.huggingface.co", ignoreCase = true)) return responseUrl
        val path = response.path.orEmpty()
        if (path.isBlank()) return responseUrl
        val query = submit.rawQuery?.let { "?$it" }.orEmpty()
        val extra = when {
            !submit.host.equals("router.huggingface.co", ignoreCase = true) -> ""
            submitUrl.contains("/fal-ai", ignoreCase = true) -> "/fal-ai"
            submitUrl.contains("/wavespeed", ignoreCase = true) -> "/wavespeed"
            submitUrl.contains("/replicate", ignoreCase = true) -> "/replicate"
            else -> ""
        }
        return "${submit.scheme}://${submit.host}$extra$path$query"
    }

    private fun authorizedGet(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", HuggingFaceConfig.USER_AGENT)
            .header("Accept", "application/json,video/mp4,*/*")
        if (url.contains("huggingface.co", ignoreCase = true)) {
            builder.header("Authorization", "Bearer ${HuggingFaceConfig.apiKey}")
        }
        return builder.build()
    }

    private fun getText(url: String, http: OkHttpClient): String? {
        return http.newCall(authorizedGet(url)).execute().use { response ->
            when (response.code) {
                in 200..299 -> response.body?.string()
                401, 403, 404 -> throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
                else -> null
            }
        }
    }

    private fun buildInputJson(
        prompt: String,
        width: Int?,
        height: Int?,
        negativePrompt: String?,
        steps: Int?,
        guidance: Double? = null
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
            if (guidance != null) add("\"guidance_scale\":$guidance")
        }
        return if (params.isEmpty()) {
            """{"inputs":"$quoted"}"""
        } else {
            """{"inputs":"$quoted","parameters":{${params.joinToString(",")}}}"""
        }
    }

    private fun fit(width: Int, height: Int): Pair<Int, Int> {
        val maxSide = 1280
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

    private fun executeGet(
        url: String,
        expectVideo: Boolean,
        http: OkHttpClient = if (expectVideo) videoClient else client,
        hop: Int = 0
    ): ByteArray {
        val request = authorizedGet(url)
        http.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw GenerationException(
                    if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
                )
            }
            if (expectVideo && MediaPayload.looksLikeVideo(bytes)) return bytes
            if (!expectVideo && MediaPayload.looksLikeImage(bytes)) return bytes
            val text = runCatching { bytes.decodeToString() }.getOrNull().orEmpty()
            MediaPayload.decodeEmbeddedImage(text)?.let { decoded ->
                if (expectVideo && MediaPayload.looksLikeVideo(decoded)) return decoded
                if (!expectVideo && MediaPayload.looksLikeImage(decoded)) return decoded
            }
            val nested = MediaPayload.extractHttpUrl(text)
            if (hop < 2 && !nested.isNullOrBlank() && nested != url && MediaPayload.isMediaFileUrl(nested)) {
                return executeGet(nested, expectVideo, http, hop + 1)
            }
            throw GenerationException(
                if (expectVideo) GenerationError.VIDEO_UNAVAILABLE else GenerationError.EMPTY_RESPONSE
            )
        }
    }

    private fun isUsefulRewrite(original: String, rewritten: String): Boolean {
        if (rewritten.length < 3) return false
        val lower = rewritten.lowercase()
        if (lower.startsWith("i'm sorry") || lower.startsWith("i cannot") || lower.startsWith("as an ai")) {
            return false
        }
        if (rewritten.equals(original.trim(), ignoreCase = true)) return false
        return LanguageScripts.nonLatinRatio(rewritten) < 0.25f
    }

    private fun nllbSource(text: String): String? {
        text.forEach { c ->
            when (c.code) {
                in 0x0980..0x09FF -> return "ben_Beng"
                in 0x0900..0x097F -> return "hin_Deva"
                in 0x0A00..0x0A7F -> return "pan_Guru"
                in 0x0A80..0x0AFF -> return "guj_Gujr"
                in 0x0B00..0x0B7F -> return "ory_Orya"
                in 0x0B80..0x0BFF -> return "tam_Taml"
                in 0x0C00..0x0C7F -> return "tel_Telu"
                in 0x0C80..0x0CFF -> return "kan_Knda"
                in 0x0D00..0x0D7F -> return "mal_Mlym"
                in 0x0600..0x06FF, in 0x0750..0x077F -> return "arb_Arab"
                in 0x0400..0x04FF -> return "rus_Cyrl"
                in 0x4E00..0x9FFF -> return "zho_Hans"
                in 0x3040..0x30FF -> return "jpn_Jpan"
                in 0xAC00..0xD7AF -> return "kor_Hang"
                in 0x0E00..0x0E7F -> return "tha_Thai"
                in 0x10A0..0x10FF -> return "kat_Geor"
                in 0x0370..0x03FF -> return "ell_Grek"
                in 0x0590..0x05FF -> return "heb_Hebr"
            }
        }
        return null
    }
}
