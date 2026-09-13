package com.kolpona.ai.data.api

import com.kolpona.ai.prompt.CulturalTerms
import com.kolpona.ai.prompt.DialectLexicon
import com.kolpona.ai.prompt.LanguageScripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Language + dialect understanding. Never shown in the UI.
 * Local lexicon only — no remote rewrite service.
 */
class TextNormalizeService {
    suspend fun understand(prompt: String): String = withContext(Dispatchers.IO) {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        val rewritten = DialectLexicon.rewrite(cleaned)
        CulturalTerms.ensure(rewritten.ifBlank { cleaned }, cleaned, rewritten)
    }
}

object LanguageDetector {
    fun needsEnglish(text: String): Boolean =
        LanguageScripts.nonLatinRatio(text) >= 0.25f || LanguageScripts.isMixed(text)
}
