package com.kolpona.ai.utils

import com.kolpona.ai.R
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.model.GenerationError

fun GenerationError.messageRes(): Int = when (this) {
    GenerationError.EMPTY_PROMPT, GenerationError.INVALID_PROMPT -> R.string.error_invalid_prompt
    GenerationError.INSUFFICIENT_CREDITS -> R.string.error_insufficient_credits
    GenerationError.NETWORK -> R.string.error_network
    GenerationError.TIMEOUT -> R.string.error_timeout
    GenerationError.SERVER -> R.string.error_server
    GenerationError.RATE_LIMIT -> R.string.error_rate_limit
    GenerationError.EMPTY_RESPONSE -> R.string.error_empty_response
    GenerationError.API -> R.string.error_api
    GenerationError.VIDEO_UNAVAILABLE -> R.string.error_video
    GenerationError.UNKNOWN -> R.string.error_generic
}

fun GenerationError.formatArgs(): Array<Any>? = when (this) {
    GenerationError.INSUFFICIENT_CREDITS -> arrayOf(CreditConfig.GENERATION_COST)
    else -> null
}
