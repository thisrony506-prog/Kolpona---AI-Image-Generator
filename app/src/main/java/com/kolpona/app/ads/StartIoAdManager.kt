package com.kolpona.app.ads

import android.app.Activity
import android.app.Application
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolated Start.io rewarded-video manager.
 *
 * Uses reflection so the app compiles against Start.io 4.x and 5.x.
 * Rewards are granted only from the SDK video-completed callback, once per shown ad.
 */
class StartIoAdManager(
    private val app: Application
) {
    @Volatile
    private var rewardedAd: Any? = null

    @Volatile
    private var loaded: Boolean = false

    private val rewardLock = Any()
    private var currentShowToken: String? = null
    private val rewardConsumed = AtomicBoolean(false)

    fun initialize() {
        try {
            val sdk = Class.forName("com.startapp.sdk.adsbase.StartAppSDK")
            // 5.x: initParams(context, appId).setReturnAdsEnabled(false).init()
            val initParams = sdk.methods.find { it.name == "initParams" && it.parameterCount == 2 }
            if (initParams != null) {
                val params = initParams.invoke(null, app, AdsConfig.APP_ID)
                params?.javaClass?.methods
                    ?.find { it.name == "setReturnAdsEnabled" }
                    ?.invoke(params, false)
                params?.javaClass?.methods
                    ?.find { it.name == "init" && it.parameterCount == 0 }
                    ?.invoke(params)
            } else {
                // 4.x: init(context, appId, returnAds)
                sdk.methods.find { it.name == "init" && it.parameterCount == 3 }
                    ?.invoke(null, app, AdsConfig.APP_ID, false)
            }
            if (AdsConfig.isTestMode) {
                sdk.methods.find { it.name == "setTestAdsEnabled" }
                    ?.invoke(null, true)
            }
            val startAppAd = Class.forName("com.startapp.sdk.adsbase.StartAppAd")
            startAppAd.methods.find { it.name == "disableSplash" && it.parameterCount == 0 }
                ?.invoke(null)
            startAppAd.methods.find { it.name == "disableAutoInterstitial" && it.parameterCount == 0 }
                ?.invoke(null)
        } catch (_: Throwable) {
            // Ads must never crash the app.
        }
    }

    fun preload(activity: Activity) {
        try {
            val startAppAdClass = Class.forName("com.startapp.sdk.adsbase.StartAppAd")
            val ad = startAppAdClass.getConstructor(android.content.Context::class.java).newInstance(activity)
            loaded = false

            val listenerClass = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdEventListener")
            val listener = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, _ ->
                when (method.name) {
                    "onReceiveAd" -> loaded = true
                    "onFailedToReceiveAd" -> loaded = false
                }
                null
            }

            val adMode = rewardedMode(startAppAdClass)
            val loadWithMode = startAppAdClass.methods.find { method ->
                method.name == "loadAd" && method.parameterCount == 2 &&
                    method.parameterTypes[1].name.contains("AdEventListener")
            }
            if (adMode != null && loadWithMode != null) {
                loadWithMode.invoke(ad, adMode, listener)
            } else {
                startAppAdClass.methods.find { it.name == "loadAd" && it.parameterCount == 1 }
                    ?.invoke(ad, listener)
            }
            rewardedAd = ad
        } catch (_: Throwable) {
            loaded = false
        }
    }

    fun isReady(): Boolean {
        if (loaded && rewardedAd != null) return true
        val ad = rewardedAd ?: return false
        return try {
            val method = ad.javaClass.methods.find { it.name == "isReady" && it.parameterCount == 0 }
            (method?.invoke(ad) as? Boolean) ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun showRewarded(
        activity: Activity,
        onRewarded: (token: String) -> Unit,
        onUnavailable: () -> Unit,
        onClosed: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null || !isReady()) {
            onUnavailable()
            preload(activity)
            return
        }

        val token = UUID.randomUUID().toString()
        synchronized(rewardLock) {
            currentShowToken = token
            rewardConsumed.set(false)
        }

        try {
            val videoListenerClass = firstClass(
                "com.startapp.sdk.adsbase.VideoListener",
                "com.startapp.sdk.adsbase.adlisteners.VideoListener"
            ) ?: return onUnavailable().also { preload(activity) }
            val videoListener = Proxy.newProxyInstance(
                videoListenerClass.classLoader,
                arrayOf(videoListenerClass)
            ) { _, method, _ ->
                if (method.name == "onVideoCompleted" || method.name == "invoke") {
                    val grant = synchronized(rewardLock) {
                        if (currentShowToken == token && rewardConsumed.compareAndSet(false, true)) token else null
                    }
                    if (grant != null) onRewarded(grant)
                }
                null
            }
            ad.javaClass.methods.find { it.name == "setVideoListener" }
                ?.invoke(ad, videoListener)

            loaded = false

            val displayListenerClass = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdDisplayListener")
            val displayListener = Proxy.newProxyInstance(
                displayListenerClass.classLoader,
                arrayOf(displayListenerClass)
            ) { _, method, _ ->
                when (method.name) {
                    "adHidden", "adNotDisplayed" -> {
                        onClosed()
                        preload(activity)
                    }
                }
                null
            }
            ad.javaClass.methods.find { method ->
                method.name == "showAd" && method.parameterCount == 1 &&
                    method.parameterTypes[0].name.contains("AdDisplayListener")
            }?.invoke(ad, displayListener)
                ?: ad.javaClass.methods.find { it.name == "showAd" && it.parameterCount == 0 }?.invoke(ad)
        } catch (_: Throwable) {
            onUnavailable()
            preload(activity)
        }
    }

    private fun rewardedMode(startAppAdClass: Class<*>): Any? {
        val modeClass = startAppAdClass.classes.find { it.simpleName == "AdMode" }
            ?: runCatching { Class.forName("com.startapp.sdk.adsbase.StartAppAd\$AdMode") }.getOrNull()
            ?: return null
        return modeClass.enumConstants?.find { (it as Enum<*>).name.contains("REWARDED") }
    }
}
