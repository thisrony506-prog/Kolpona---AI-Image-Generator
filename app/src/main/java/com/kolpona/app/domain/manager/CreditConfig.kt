package com.kolpona.app.domain.manager

/**
 * Single place to change credit economy values.
 *
 * Daily reset always restores [DAILY_INITIAL_CREDITS].
 * Image generation costs [GENERATION_COST].
 * A completed rewarded video grants [REWARDED_VIDEO_REWARD].
 */
object CreditConfig {
    const val DAILY_INITIAL_CREDITS = 100
    const val GENERATION_COST = 25
    const val REWARDED_VIDEO_REWARD = 25
}
