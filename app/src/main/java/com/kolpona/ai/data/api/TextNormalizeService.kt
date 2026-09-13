package com.kolpona.ai.data.api

import com.kolpona.ai.prompt.CulturalTerms
import com.kolpona.ai.prompt.DialectLexicon
import com.kolpona.ai.prompt.LanguageScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Language + dialect understanding. Never shown in the UI.
 * Local lexicon first, then a short model rewrite when the text is not English.
 */
class TextNormalizeService(
    private val huggingFace: HuggingFaceApiService? = null,
    private val cloudflare: CloudflareApiService? = null
) {
    suspend fun understand(prompt: String): String = withContext(Dispatchers.IO) {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val local = DialectLexicon.rewrite(cleaned).ifBlank { cleaned }
        val needsModel = DialectLexicon.stillNeedsModel(cleaned, local) ||
            LanguageScripts.nonLatinRatio(local) >= 0.08f ||
            LanguageScripts.isMixed(cleaned) ||
            !LanguageScripts.looksLikeEnglish(local)
        val rewritten = if (needsModel) {
            val fromCloudflare = runCatching { cloudflare?.rewriteToEnglish(cleaned) }.getOrNull()
            val fromHuggingFace = fromCloudflare
                ?: runCatching { huggingFace?.rewriteToEnglish(cleaned) }.getOrNull()
            fromHuggingFace?.takeIf { it.isNotBlank() } ?: local
        } else {
            local
        }
        CulturalTerms.ensure(rewritten.ifBlank { cleaned }, cleaned, local)
    }
}

object LanguageDetector {
    fun needsEnglish(text: String): Boolean =
        LanguageScripts.nonLatinRatio(text) >= 0.25f || LanguageScripts.isMixed(text)
}
