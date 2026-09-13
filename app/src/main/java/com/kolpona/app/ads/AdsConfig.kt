package com.kolpona.app.ads

import com.kolpona.app.BuildConfig

/**
 * Start.io configuration.
 *
 * App ID is a public publisher identifier (not a secret).
 * Test mode is true on debug builds and false on release builds.
 */
object AdsConfig {
    const val APP_ID = "208407601"

    val isTestMode: Boolean
        get() = BuildConfig.IS_AD_TEST_MODE
}
