package com.kolpona.app.utils

import com.kolpona.app.R
import com.kolpona.app.domain.manager.CreditConfig
import com.kolpona.app.domain.model.GenerationError

fun GenerationError.messageRes(): Int = when (this) {
    GenerationError.EMPTY_PROMPT, GenerationError.INVALID_PROMPT -> R.string.error_invalid_prompt
    GenerationError.INSUFFICIENT_CREDITS -> R.string.error_insufficient_credits
    GenerationError.NETWORK -> R.string.error_network
    GenerationError.TIMEOUT -> R.string.error_timeout
    GenerationError.SERVER -> R.string.error_server
    GenerationError.RATE_LIMIT -> R.string.error_rate_limit
    GenerationError.EMPTY_RESPONSE -> R.string.error_empty_response
    GenerationError.API -> R.string.error_api
    GenerationError.UNKNOWN -> R.string.error_generic
}

fun GenerationError.formatArgs(): Array<Any>? = when (this) {
    GenerationError.INSUFFICIENT_CREDITS -> arrayOf(CreditConfig.GENERATION_COST)
    else -> null
}
