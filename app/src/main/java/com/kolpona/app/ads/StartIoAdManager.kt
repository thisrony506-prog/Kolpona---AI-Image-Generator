package com.kolpona.app.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Start.io ads: rewarded video, interstitial, and banner.
 *
 * Reflection keeps the app compiling against Start.io 4.x and 5.x.
 * Rewards are granted only from the SDK video-completed callback, once per shown ad.
 * Failures never crash the app.
 */
class StartIoAdManager(
    private val app: Application
) {
    @Volatile
    private var rewardedAd: Any? = null

    @Volatile
    private var rewardedReady: Boolean = false

    @Volatile
    private var interstitialAd: Any? = null

    @Volatile
    private var interstitialReady: Boolean = false

    private val rewardLock = Any()
    private var currentShowToken: String? = null
    private val rewardConsumed = AtomicBoolean(false)

    fun initialize() {
        try {
            val sdk = Class.forName("com.startapp.sdk.adsbase.StartAppSDK")
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
                sdk.methods.find { it.name == "init" && it.parameterCount == 3 }
                    ?.invoke(null, app, AdsConfig.APP_ID, false)
            }
            sdk.methods.find { it.name == "setTestAdsEnabled" && it.parameterCount == 1 }
                ?.invoke(null, AdsConfig.isTestMode)
            val startAppAd = Class.forName("com.startapp.sdk.adsbase.StartAppAd")
            startAppAd.methods.find { it.name == "disableSplash" && it.parameterCount == 0 }
                ?.invoke(null)
            startAppAd.methods.find { it.name == "disableAutoInterstitial" && it.parameterCount == 0 }
                ?.invoke(null)
            Log.d(TAG, "Start.io initialized testMode=${AdsConfig.isTestMode}")
        } catch (t: Throwable) {
            Log.w(TAG, "Start.io init failed", t)
        }
    }

    fun preload(activity: Activity) {
        preloadRewarded(activity)
        preloadInterstitial(activity)
    }

    fun preloadRewarded(activity: Activity) {
        try {
            val startAppAdClass = Class.forName("com.startapp.sdk.adsbase.StartAppAd")
            val ad = startAppAdClass.getConstructor(Context::class.java).newInstance(activity)
            rewardedReady = false
            val listener = adListener { ready ->
                rewardedReady = ready
                Log.d(TAG, "rewarded load ready=$ready")
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
        } catch (t: Throwable) {
            Log.w(TAG, "preload rewarded failed", t)
            rewardedReady = false
        }
    }

    fun preloadInterstitial(activity: Activity) {
        try {
            val startAppAdClass = Class.forName("com.startapp.sdk.adsbase.StartAppAd")
            val ad = startAppAdClass.getConstructor(Context::class.java).newInstance(activity)
            interstitialReady = false
            val listener = adListener { ready ->
                interstitialReady = ready
                Log.d(TAG, "interstitial load ready=$ready")
            }
            startAppAdClass.methods.find { it.name == "loadAd" && it.parameterCount == 1 }
                ?.invoke(ad, listener)
                ?: startAppAdClass.methods.find { it.name == "loadAd" && it.parameterCount == 0 }
                    ?.invoke(ad)
            interstitialAd = ad
        } catch (t: Throwable) {
            Log.w(TAG, "preload interstitial failed", t)
            interstitialReady = false
        }
    }

    fun isReady(): Boolean {
        if (rewardedReady && rewardedAd != null) return true
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
            preloadRewarded(activity)
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
            ) ?: run {
                onUnavailable()
                preloadRewarded(activity)
                return
            }
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

            rewardedReady = false

            val displayListenerClass = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdDisplayListener")
            val displayListener = Proxy.newProxyInstance(
                displayListenerClass.classLoader,
                arrayOf(displayListenerClass)
            ) { _, method, _ ->
                when (method.name) {
                    "adHidden", "adNotDisplayed" -> {
                        onClosed()
                        preloadRewarded(activity)
                    }
                }
                null
            }
            val shown = ad.javaClass.methods.find { method ->
                method.name == "showAd" && method.parameterCount == 1 &&
                    method.parameterTypes[0].name.contains("AdDisplayListener")
            }?.invoke(ad, displayListener)
                ?: ad.javaClass.methods.find { it.name == "showAd" && it.parameterCount == 0 }?.invoke(ad)
            if (shown is Boolean && !shown) {
                onUnavailable()
                preloadRewarded(activity)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "show rewarded failed", t)
            onUnavailable()
            preloadRewarded(activity)
        }
    }

    fun showInterstitial(activity: Activity) {
        val ad = interstitialAd
        val ready = interstitialReady || isAdReady(ad)
        if (ad == null || !ready) {
            preloadInterstitial(activity)
            return
        }
        try {
            interstitialReady = false
            val displayListenerClass = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdDisplayListener")
            val displayListener = Proxy.newProxyInstance(
                displayListenerClass.classLoader,
                arrayOf(displayListenerClass)
            ) { _, method, _ ->
                when (method.name) {
                    "adHidden", "adNotDisplayed" -> preloadInterstitial(activity)
                }
                null
            }
            ad.javaClass.methods.find { method ->
                method.name == "showAd" && method.parameterCount == 1 &&
                    method.parameterTypes[0].name.contains("AdDisplayListener")
            }?.invoke(ad, displayListener)
                ?: ad.javaClass.methods.find { it.name == "showAd" && it.parameterCount == 0 }?.invoke(ad)
        } catch (t: Throwable) {
            Log.w(TAG, "show interstitial failed", t)
            preloadInterstitial(activity)
        }
    }

    fun createBanner(context: Context): View? {
        return try {
            val bannerClass = firstClass(
                "com.startapp.sdk.ads.banner.Banner",
                "com.startapp.sdk.ads.banner.bannerstandard.BannerStandard",
                "com.startapp.android.publish.ads.banner.Banner"
            ) ?: return null
            val ctor = bannerClass.constructors.firstOrNull { constructor ->
                constructor.parameterCount == 1 &&
                    Context::class.java.isAssignableFrom(constructor.parameterTypes[0])
            } ?: return null
            val banner = ctor.newInstance(context) as? View ?: return null
            val height = (50 * context.resources.displayMetrics.density).toInt()
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            ).apply { gravity = Gravity.CENTER }
            runCatching {
                banner.javaClass.methods.find { it.name == "loadAd" && it.parameterCount == 0 }
                    ?.invoke(banner)
            }
            val host = FrameLayout(context)
            host.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            host.addView(banner)
            host
        } catch (t: Throwable) {
            Log.w(TAG, "create banner failed", t)
            null
        }
    }

    private fun isAdReady(ad: Any?): Boolean {
        if (ad == null) return false
        return try {
            val method = ad.javaClass.methods.find { it.name == "isReady" && it.parameterCount == 0 }
            (method?.invoke(ad) as? Boolean) ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun adListener(onReady: (Boolean) -> Unit): Any {
        val listenerClass = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdEventListener")
        return Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { _, method, _ ->
            when (method.name) {
                "onReceiveAd" -> onReady(true)
                "onFailedToReceiveAd" -> onReady(false)
            }
            null
        }
    }

    private fun rewardedMode(startAppAdClass: Class<*>): Any? {
        val modeClass = startAppAdClass.classes.find { it.simpleName == "AdMode" }
            ?: runCatching { Class.forName("com.startapp.sdk.adsbase.StartAppAd\$AdMode") }.getOrNull()
            ?: return null
        return modeClass.enumConstants?.find { (it as Enum<*>).name.contains("REWARDED") }
    }

    private fun firstClass(vararg names: String): Class<*>? {
        names.forEach { name ->
            runCatching { Class.forName(name) }.getOrNull()?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "KolponaAds"
    }
}
