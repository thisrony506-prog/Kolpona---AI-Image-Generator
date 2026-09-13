package com.kolpona.ai.data.api

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object MediaPayload {
    fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 24) return false
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val jpg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val gif = bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
        val webp = bytes.size > 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[8] == 'W'.code.toByte()
        return png || jpg || gif || webp
    }

    fun looksLikeVideo(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val limit = minOf(512, bytes.size - 4)
        for (i in 0 until limit) {
            if (bytes[i] == 'f'.code.toByte() &&
                bytes.getOrNull(i + 1) == 't'.code.toByte() &&
                bytes.getOrNull(i + 2) == 'y'.code.toByte() &&
                bytes.getOrNull(i + 3) == 'p'.code.toByte()
            ) {
                return true
            }
        }
        val webm = bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte()
        val avi = bytes.size > 11 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[8] == 'A'.code.toByte() &&
            bytes[9] == 'V'.code.toByte()
        return webm || avi
    }

    fun decodeEmbeddedImage(text: String): ByteArray? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        runCatching { base64FromJson(JSONObject(trimmed)) }.getOrNull()?.let { return it }
        return decodeBase64(trimmed)
    }

    fun extractHttpUrl(text: String): String? {
        val trimmed = text.trim().trim('"')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val line = trimmed.lineSequence().first().trim().trim('"')
            if (line.startsWith("http")) return line
        }
        runCatching {
            urlFromJson(JSONObject(trimmed))
        }.getOrNull()?.takeIf { isMediaFileUrl(it) }?.let { return it }
        val regex = Regex("""https?://[^\s"'<>\\]+""")
        return regex.findAll(trimmed)
            .map { it.value.trimEnd(',', ')', ']', '.', ';') }
            .firstOrNull { isMediaFileUrl(it) }
    }

    fun loadingWaitSeconds(text: String): Int? {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return null
        val error = json.optString("error").lowercase()
        val estimated = json.optDouble("estimated_time", Double.NaN)
        val loading = error.contains("load") ||
            error.contains("warming") ||
            error.contains("overloaded") ||
            json.optBoolean("loading", false)
        if (!loading && estimated.isNaN()) return null
        val seconds = if (estimated.isNaN()) 12.0 else estimated
        return seconds.coerceIn(4.0, 45.0).toInt()
    }

    fun queueStatusUrl(text: String): String? {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return null
        listOf("status_url", "statusUrl", "response_url", "responseUrl").forEach { key ->
            json.optString(key).takeIf { it.startsWith("http") }?.let { return it }
        }
        json.optJSONObject("status")?.optString("url")
            ?.takeIf { it.startsWith("http") }
            ?.let { return it }
        json.optJSONObject("urls")?.optString("get")
            ?.takeIf { it.startsWith("http") }
            ?.let { return it }
        val status = json.optString("status").lowercase()
        val requestId = json.optString("request_id").ifBlank { json.optString("requestId") }
        if ((status.contains("queue") || status.contains("in_progress") || status == "pending" ||
                status == "starting" || status == "processing") &&
            requestId.isNotBlank()
        ) {
            json.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        }
        return null
    }

    fun queueResponseUrl(text: String): String? {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return null
        json.optString("response_url").takeIf { it.startsWith("http") }?.let { return it }
        json.optString("responseUrl").takeIf { it.startsWith("http") }?.let { return it }
        json.optJSONObject("urls")?.optString("get")
            ?.takeIf { it.startsWith("http") }
            ?.let { return it }
        return null
    }

    fun isMediaFileUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("/status") || lower.contains("/cancel")) return false
        if (lower.contains("queue.fal.run") && !lower.contains(".mp4")) return false
        return lower.contains(".mp4") ||
            lower.contains(".webm") ||
            lower.contains("fal.media") ||
            lower.contains("cdn.fal.ai") ||
            lower.contains("replicate.delivery") ||
            lower.contains("wavespeed")
    }

    fun queueDone(text: String): Boolean {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        val status = json.optString("status").lowercase()
        return status == "completed" ||
            status == "complete" ||
            status == "succeeded" ||
            status == "success" ||
            status == "ready"
    }

    fun queueFailed(text: String): Boolean {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        val status = json.optString("status").lowercase()
        return status == "failed" || status == "error" || status == "cancelled"
    }

    fun chatText(text: String): String? {
        val json = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return text.trim().takeIf { it.isNotBlank() }
        json.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
            choice.optJSONObject("message")?.optString("content")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            choice.optString("text").trim().takeIf { it.isNotBlank() }?.let { return it }
        }
        json.optJSONObject("result")?.optString("response")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        json.optString("generated_text").trim().takeIf { it.isNotBlank() }?.let { return it }
        json.optString("translation_text").trim().takeIf { it.isNotBlank() }?.let { return it }
        json.optJSONArray("result")?.optJSONObject(0)?.optString("translation_text")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        json.optString("response").trim().takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun base64FromJson(json: JSONObject): ByteArray? {
        listOf("image", "b64_json", "base64", "video").forEach { key ->
            json.optString(key).takeIf { it.length > 100 && !it.startsWith("http") }?.let { raw ->
                decodeBase64(raw)?.let { return it }
            }
        }
        json.optJSONObject("result")?.let { child -> base64FromJson(child)?.let { return it } }
        json.optJSONObject("data")?.let { child -> base64FromJson(child)?.let { return it } }
        json.optJSONObject("video")?.let { child -> base64FromJson(child)?.let { return it } }
        json.optJSONArray("data")?.let { array ->
            for (i in 0 until array.length()) {
                val child = array.optJSONObject(i) ?: continue
                base64FromJson(child)?.let { return it }
            }
        }
        return null
    }

    private fun decodeBase64(value: String): ByteArray? {
        var payload = value.trim().trim('"')
        val marker = payload.indexOf("base64,")
        if (marker >= 0) payload = payload.substring(marker + 7)
        payload = payload.replace("\\s".toRegex(), "")
        if (payload.length < 100) return null
        val decoded = runCatching {
            Base64.decode(payload, Base64.DEFAULT)
        }.getOrNull() ?: return null
        return decoded.takeIf { looksLikeImage(it) || looksLikeVideo(it) }
    }

    private fun urlFromJson(json: JSONObject): String? {
        listOf(
            "url", "video_url", "image_url", "output", "result", "file", "uri",
            "video", "image", "content", "download_url"
        ).forEach { key ->
            json.optString(key).takeIf { it.startsWith("http") }?.let { return it }
        }
        json.optJSONObject("video")?.let { child -> urlFromJson(child)?.let { return it } }
        json.optJSONObject("image")?.let { child -> urlFromJson(child)?.let { return it } }
        json.optJSONObject("output")?.let { child -> urlFromJson(child)?.let { return it } }
        json.optJSONObject("data")?.let { child -> urlFromJson(child)?.let { return it } }
        json.optJSONArray("data")?.let { array -> urlFromArray(array)?.let { return it } }
        json.optJSONArray("output")?.let { array -> urlFromArray(array)?.let { return it } }
        json.optJSONObject("result")?.let { child -> urlFromJson(child)?.let { return it } }
        json.optJSONObject("response")?.let { child -> urlFromJson(child)?.let { return it } }
        return null
    }

    private fun urlFromArray(array: JSONArray): String? {
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            when (value) {
                is String -> if (value.startsWith("http")) return value
                is JSONObject -> urlFromJson(value)?.let { return it }
            }
        }
        return null
    }
}
