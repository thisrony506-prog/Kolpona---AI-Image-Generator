package com.kolpona.ai.data.api

import android.util.Log
import com.kolpona.ai.data.auth.AuthRepository
import com.kolpona.ai.domain.model.GenerationError
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android talks only to Kolpona's Firebase function for video.
 * The Hugging Face token never leaves Cloud Functions.
 */
class VideoBackendService(
    private val client: OkHttpClient,
    private val auth: AuthRepository
) {
    private val http: OkHttpClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(540, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(560, TimeUnit.SECONDS)
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

    suspend fun generate(
        prompt: String,
        aspectRatio: String,
        width: Int,
        height: Int
    ): MediaBytes {
        var forceRefresh = false
        var last: GenerationException? = null
        repeat(2) {
            val idToken = auth.idToken(forceRefresh)
                ?: throw GenerationException(GenerationError.PERMISSION_DENIED)
            try {
                return requestVideo(idToken, prompt, aspectRatio, width, height)
            } catch (e: GenerationException) {
                last = e
                if (e.error == GenerationError.PERMISSION_DENIED && !forceRefresh) {
                    forceRefresh = true
                } else {
                    throw e
                }
            }
        }
        throw last ?: GenerationException(GenerationError.PERMISSION_DENIED)
    }

    private suspend fun requestVideo(
        idToken: String,
        prompt: String,
        aspectRatio: String,
        width: Int,
        height: Int
    ): MediaBytes {
        val payload = JSONObject()
            .put("prompt", prompt.take(1400))
            .put("aspectRatio", aspectRatio)
            .put("width", width)
            .put("height", height)
            .toString()
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(payload.toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $idToken")
            .header("User-Agent", "Kolpona/1.14.4 (Android)")
            .build()
        try {
            val (code, text) = executeText(request)
            val json = runCatching { JSONObject(text) }.getOrNull()
            val errorCode = json?.optString("error").orEmpty()
            if (code !in 200..299 || json?.optBoolean("ok") == false) {
                Log.w(TAG, "generateVideo http=$code error=$errorCode")
                throw GenerationException(mapError(code, errorCode))
            }
            val videoUrl = json?.optString("videoUrl").orEmpty()
            if (!videoUrl.startsWith("http")) {
                throw GenerationException(GenerationError.EMPTY_RESPONSE)
            }
            val bytes = download(videoUrl)
            if (bytes.size < 4_000 || !MediaPayload.looksLikeVideo(bytes)) {
                throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
            }
            return MediaBytes(bytes, json?.optString("model").orEmpty().ifBlank { "wan-t2v" })
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

    private suspend fun download(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "video/mp4,*/*")
            .header("User-Agent", "Kolpona/1.14.4 (Android)")
            .build()
        val (code, bytes) = executeBytes(request)
        if (code !in 200..299) {
            Log.w(TAG, "video download http=$code")
            throw GenerationException(GenerationError.VIDEO_UNAVAILABLE)
        }
        return bytes
    }

    private suspend fun executeText(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { body ->
                        val text = body.body?.string().orEmpty()
                        if (cont.isCancelled) return
                        cont.resume(body.code to text)
                    }
                }
            })
        }

    private suspend fun executeBytes(request: Request): Pair<Int, ByteArray> =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { body ->
                        val bytes = body.body?.bytes() ?: ByteArray(0)
                        if (cont.isCancelled) return
                        cont.resume(body.code to bytes)
                    }
                }
            })
        }

    private fun mapError(http: Int, code: String): GenerationError = when (code.uppercase()) {
        "INVALID_PROMPT" -> GenerationError.INVALID_PROMPT
        "INVALID_TOKEN" -> GenerationError.INVALID_TOKEN
        "PERMISSION_DENIED" -> GenerationError.PERMISSION_DENIED
        "MODEL_UNAVAILABLE" -> GenerationError.MODEL_UNAVAILABLE
        "PROVIDER_UNAVAILABLE" -> GenerationError.PROVIDER_UNAVAILABLE
        "RATE_LIMIT" -> GenerationError.RATE_LIMIT
        "TIMEOUT" -> GenerationError.TIMEOUT
        "NETWORK" -> GenerationError.NETWORK
        "SERVER" -> GenerationError.SERVER
        else -> when (http) {
            401 -> GenerationError.INVALID_TOKEN
            403 -> GenerationError.PERMISSION_DENIED
            404 -> GenerationError.MODEL_UNAVAILABLE
            408, 504 -> GenerationError.TIMEOUT
            429 -> GenerationError.RATE_LIMIT
            in 500..599 -> GenerationError.SERVER
            else -> GenerationError.VIDEO_UNAVAILABLE
        }
    }

    companion object {
        private const val TAG = "KolponaVideo"
        const val ENDPOINT =
            "https://us-central1-kolpona-ai.cloudfunctions.net/generateVideo"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
