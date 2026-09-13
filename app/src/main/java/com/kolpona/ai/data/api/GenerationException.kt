package com.kolpona.ai.data.api

import com.kolpona.ai.domain.model.GenerationError

class GenerationException(val error: GenerationError) : Exception()
