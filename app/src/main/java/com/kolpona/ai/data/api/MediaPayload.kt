package com.kolpona.ai.data.api

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
        val limit = minOf(32, bytes.size - 4)
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

    fun extractHttpUrl(text: String): String? {
        val trimmed = text.trim().trim('"')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val line = trimmed.lineSequence().first().trim().trim('"')
            if (line.startsWith("http")) return line
        }
        runCatching {
            urlFromJson(JSONObject(trimmed))
        }.getOrNull()?.let { return it }
        val regex = Regex("""https?://[^\s"'<>\\]+""")
        return regex.findAll(trimmed)
            .map { it.value.trimEnd(',', ')', ']', '.', ';') }
            .firstOrNull { candidate ->
                val lower = candidate.lowercase()
                lower.contains(".mp4") ||
                    lower.contains(".webm") ||
                    lower.contains("video") ||
                    lower.contains("pollinations") ||
                    lower.contains("huggingface")
            }
    }

    private fun urlFromJson(json: JSONObject): String? {
        listOf("url", "video_url", "image_url", "output", "result", "file", "uri").forEach { key ->
            json.optString(key).takeIf { it.startsWith("http") }?.let { return it }
        }
        json.optJSONObject("data")?.let { child -> urlFromJson(child)?.let { return it } }
        json.optJSONArray("data")?.let { array -> urlFromArray(array)?.let { return it } }
        json.optJSONArray("output")?.let { array -> urlFromArray(array)?.let { return it } }
        json.optJSONObject("result")?.let { child -> urlFromJson(child)?.let { return it } }
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
