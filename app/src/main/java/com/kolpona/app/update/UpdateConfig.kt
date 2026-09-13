package com.kolpona.app.update

/**
 * Public update manifest. Not a secret — the repo is public and the APK
 * must be able to read this without a GitHub token.
 */
object UpdateConfig {
    const val RELEASE_TAG = "kolpona-release-apk"

    const val MANIFEST_URL =
        "https://github.com/thisrony506-prog/Kolpona---AI-Image-Generator/releases/download/$RELEASE_TAG/version.json"

    const val FALLBACK_MANIFEST_URL =
        "https://github.com/thisrony506-prog/Kolpona---AI-Image-Generator/releases/latest/download/version.json"

    const val DEFAULT_APK_URL =
        "https://github.com/thisrony506-prog/Kolpona---AI-Image-Generator/releases/download/$RELEASE_TAG/app-release.apk"

    const val MIN_APK_BYTES = 100_000L
    const val CHECK_TIMEOUT_MS = 8_000L
}
