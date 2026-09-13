package com.kolpona.app.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Start.io ads: rewarded video (load-on-click), interstitial, banner.
 *
 * Rewards are granted only after an ad was actually displayed.
 * Failures never crash the app.
 */
class StartIoAdManager(
    private val app: Application
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var interstitialAd: Any? = null

    @Volatile
    private var interstitialReady: Boolean = false

    private val showInFlight = AtomicBoolean(false)

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
        preloadInterstitial(activity)
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

    /**
     * Loads a rewarded video on demand, then shows it.
     * Falls back to VIDEO then interstitial so the user actually sees an ad.
     */
    fun showRewarded(
        activity: Activity,
        onRewarded: (token: String) -> Unit,
        onUnavailable: () -> Unit,
        onClosed: () -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { showRewarded(activity, onRewarded, onUnavailable, onClosed) }
            return
        }
        if (!showInFlight.compareAndSet(false, true)) return

        val token = UUID.randomUUID().toString()
        val displayed = AtomicBoolean(false)
        val granted = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val loaded = AtomicBoolean(false)

        fun finishUnavailable() {
            if (finished.compareAndSet(false, true)) {
                showInFlight.set(false)
                onUnavailable()
            }
        }

        fun finishClosed() {
            if (finished.compareAndSet(false, true)) {
                showInFlight.set(false)
                onClosed()
                preloadInterstitial(activity)
            }
        }

        fun grant() {
            if (granted.compareAndSet(false, true)) {
                onRewarded(token)
            }
        }

        try {
            val startAppAdClass = Class.forName("com.startapp.sdk.adsbase.StartAppAd")
            val displayListener = displayListener(
                onDisplayed = { displayed.set(true) },
                onHidden = {
                    if (displayed.get()) grant()
                    finishClosed()
                },
                onNotDisplayed = { finishUnavailable() }
            )

            fun bindAndShow(ad: Any) {
                loaded.set(true)
                attachVideoListener(ad) { grant() }
                val shown = showAd(ad, displayListener)
                if (shown == false) {
                    finishUnavailable()
                }
            }

            val modes = listOf("REWARDED", "VIDEO", "FULLPAGE", "AUTOMATIC")
            loadFirstAvailable(
                startAppAdClass = startAppAdClass,
                activity = activity,
                modeHints = modes,
                onLoaded = { ad -> bindAndShow(ad) },
                onFailed = {
                    val cached = interstitialAd
                    if (cached != null && (interstitialReady || isAdReady(cached))) {
                        interstitialReady = false
                        bindAndShow(cached)
                    } else {
                        finishUnavailable()
                    }
                }
            )

            main.postDelayed({
                if (!loaded.get() && !displayed.get() && !granted.get() && !finished.get()) {
                    Log.w(TAG, "rewarded wait timed out")
                    finishUnavailable()
                }
            }, 16_000)
        } catch (t: Throwable) {
            Log.w(TAG, "show rewarded failed", t)
            finishUnavailable()
        }
    }

    fun showInterstitial(activity: Activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { showInterstitial(activity) }
            return
        }
        val ad = interstitialAd
        val ready = interstitialReady || isAdReady(ad)
        if (ad == null || !ready) {
            preloadInterstitial(activity)
            return
        }
        try {
            interstitialReady = false
            val displayListener = displayListener(
                onDisplayed = {},
                onHidden = { preloadInterstitial(activity) },
                onNotDisplayed = { preloadInterstitial(activity) }
            )
            showAd(ad, displayListener)
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

    private fun loadFirstAvailable(
        startAppAdClass: Class<*>,
        ad: Any,
        activity: Activity,
        modeHints: List<String>,
        onLoaded: () -> Unit,
        onFailed: () -> Unit
    ) {
        val remaining = modeHints.toMutableList()
        fun tryNext() {
            val hint = remaining.removeFirstOrNull()
            val listener = adListener { ready ->
                main.post {
                    if (ready) {
                        Log.d(TAG, "ad loaded mode=$hint")
                        onLoaded()
                    } else if (remaining.isNotEmpty()) {
                        tryNext()
                    } else {
                        onFailed()
                    }
                }
            }
            val loaded = if (hint == null) {
                invokeLoad(ad, null, listener)
            } else {
                val mode = adMode(startAppAdClass, hint)
                invokeLoad(ad, mode, listener)
            }
            if (!loaded) {
                if (remaining.isNotEmpty()) tryNext() else onFailed()
            }
        }
        tryNext()
        activity.hashCode() // keep activity referenced
    }

    private fun invokeLoad(ad: Any, mode: Any?, listener: Any): Boolean {
        return try {
            if (mode != null) {
                val two = ad.javaClass.methods.find { method ->
                    method.name == "loadAd" && method.parameterCount == 2 &&
                        method.parameterTypes[1].name.contains("AdEventListener")
                }
                if (two != null) {
                    two.invoke(ad, mode, listener)
                    return true
                }
            }
            val one = ad.javaClass.methods.find { it.name == "loadAd" && it.parameterCount == 1 }
            if (one != null) {
                one.invoke(ad, listener)
                true
            } else {
                ad.javaClass.methods.find { it.name == "loadAd" && it.parameterCount == 0 }?.invoke(ad)
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "loadAd failed", t)
            false
        }
    }

    private fun showAd(ad: Any, displayListener: Any): Boolean? {
        return try {
            val withListener = ad.javaClass.methods.find { method ->
                method.name == "showAd" && method.parameterCount == 1 &&
                    method.parameterTypes[0].name.contains("AdDisplayListener")
            }
            val result = withListener?.invoke(ad, displayListener)
                ?: ad.javaClass.methods.find { it.name == "showAd" && it.parameterCount == 0 }?.invoke(ad)
            result as? Boolean
        } catch (t: Throwable) {
            Log.w(TAG, "showAd failed", t)
            false
        }
    }

    private fun attachVideoListener(ad: Any, onCompleted: () -> Unit) {
        val videoListenerClass = firstClass(
            "com.startapp.sdk.adsbase.VideoListener",
            "com.startapp.sdk.adsbase.adlisteners.VideoListener"
        ) ?: return
        val videoListener = proxy(videoListenerClass) { name, _ ->
            if (name == "onVideoCompleted" || name == "invoke") onCompleted()
            null
        }
        runCatching {
            ad.javaClass.methods.find { it.name == "setVideoListener" }?.invoke(ad, videoListener)
        }
    }

    private fun displayListener(
        onDisplayed: () -> Unit,
        onHidden: () -> Unit,
        onNotDisplayed: () -> Unit
    ): Any {
        val cls = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdDisplayListener")
        return proxy(cls) { name, _ ->
            when (name) {
                "adDisplayed", "adReceived" -> onDisplayed()
                "adHidden" -> onHidden()
                "adNotDisplayed" -> onNotDisplayed()
            }
            null
        }
    }

    private fun adListener(onReady: (Boolean) -> Unit): Any {
        val listenerClass = Class.forName("com.startapp.sdk.adsbase.adlisteners.AdEventListener")
        return proxy(listenerClass) { name, _ ->
            when (name) {
                "onReceiveAd" -> onReady(true)
                "onFailedToReceiveAd" -> onReady(false)
            }
            null
        }
    }

    private fun proxy(listenerClass: Class<*>, handler: (String, Array<out Any>?) -> Any?): Any {
        return Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Kolpona:${listenerClass.simpleName}"
                else -> handler(method.name, args)
            }
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

    private fun adMode(startAppAdClass: Class<*>, hint: String): Any? {
        val modeClass = startAppAdClass.classes.find { it.simpleName == "AdMode" }
            ?: runCatching { Class.forName("com.startapp.sdk.adsbase.StartAppAd\$AdMode") }.getOrNull()
            ?: return null
        val constants = modeClass.enumConstants ?: return null
        return constants.find { (it as Enum<*>).name.contains(hint, ignoreCase = true) }
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
