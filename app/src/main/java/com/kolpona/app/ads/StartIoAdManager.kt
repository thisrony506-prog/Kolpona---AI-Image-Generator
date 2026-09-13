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
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppAd.AdMode
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.VideoListener
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Start.io ads using the official SDK (not reflection).
 *
 * Rewarded video is loaded on tap, then VIDEO / FULLPAGE so the user actually
 * sees an ad. Credits are granted only after an ad was displayed.
 */
class StartIoAdManager(
    private val app: Application
) {
    private val main = Handler(Looper.getMainLooper())
    private val showInFlight = AtomicBoolean(false)

    @Volatile
    private var interstitial: StartAppAd? = null

    @Volatile
    private var rewarded: StartAppAd? = null

    fun initialize() {
        try {
            StartAppSDK.init(app, AdsConfig.APP_ID, false)
            StartAppSDK.setTestAdsEnabled(AdsConfig.isTestMode)
            runCatching { StartAppAd.disableSplash() }
            runCatching { StartAppAd.disableAutoInterstitial() }
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { preloadRewarded(activity) }
            return
        }
        try {
            val ad = StartAppAd(activity)
            rewarded = ad
            ad.loadAd(AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(loaded: Ad) {
                    Log.d(TAG, "rewarded preloaded")
                }

                override fun onFailedToReceiveAd(loaded: Ad?) {
                    Log.d(TAG, "rewarded preload empty")
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "preload rewarded failed", t)
        }
    }

    fun preloadInterstitial(activity: Activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { preloadInterstitial(activity) }
            return
        }
        try {
            val ad = StartAppAd(activity)
            interstitial = ad
            ad.loadAd(AdMode.FULLPAGE, object : AdEventListener {
                override fun onReceiveAd(loaded: Ad) {
                    Log.d(TAG, "interstitial preloaded")
                }

                override fun onFailedToReceiveAd(loaded: Ad?) {
                    Log.d(TAG, "interstitial preload empty")
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "preload interstitial failed", t)
        }
    }

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
        if (activity.isFinishing || activity.isDestroyed) {
            onUnavailable()
            return
        }
        if (!showInFlight.compareAndSet(false, true)) return

        val token = UUID.randomUUID().toString()
        val displayed = AtomicBoolean(false)
        val granted = AtomicBoolean(false)
        val finished = AtomicBoolean(false)

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
                preloadRewarded(activity)
                preloadInterstitial(activity)
            }
        }

        fun grant() {
            if (granted.compareAndSet(false, true)) {
                onRewarded(token)
            }
        }

        val displayListener = object : AdDisplayListener {
            override fun adDisplayed(ad: Ad?) {
                displayed.set(true)
            }

            override fun adHidden(ad: Ad?) {
                if (displayed.get()) grant()
                finishClosed()
            }

            override fun adClicked(ad: Ad?) = Unit

            override fun adNotDisplayed(ad: Ad?) {
                if (!displayed.get()) finishUnavailable()
            }
        }

        fun bindAndShow(ad: StartAppAd) {
            try {
                ad.setVideoListener(object : VideoListener {
                    override fun onVideoCompleted() {
                        grant()
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "video listener", t)
            }
            val shown = try {
                ad.showAd(displayListener)
            } catch (t: Throwable) {
                Log.w(TAG, "showAd failed", t)
                false
            }
            if (!shown) {
                main.postDelayed({
                    if (!displayed.get() && !finished.get()) {
                        Log.w(TAG, "showAd returned false")
                        finishUnavailable()
                    }
                }, 1_200)
            }
        }

        val modes = listOf(AdMode.REWARDED_VIDEO, AdMode.VIDEO, AdMode.FULLPAGE, AdMode.AUTOMATIC)

        fun tryMode(index: Int) {
            if (finished.get()) return
            if (activity.isFinishing || activity.isDestroyed) {
                finishUnavailable()
                return
            }
            if (index >= modes.size) {
                val cached = interstitial
                if (cached != null && cached.isReady) {
                    bindAndShow(cached)
                } else {
                    finishUnavailable()
                }
                return
            }
            if (index == 0) {
                val ready = rewarded
                if (ready != null && ready.isReady) {
                    Log.d(TAG, "showing preloaded rewarded")
                    bindAndShow(ready)
                    return
                }
            }
            val mode = modes[index]
            val ad = StartAppAd(activity)
            Log.d(TAG, "loading ad mode=$mode")
            ad.loadAd(mode, object : AdEventListener {
                override fun onReceiveAd(loaded: Ad) {
                    main.post {
                        if (finished.get()) return@post
                        Log.d(TAG, "loaded mode=$mode")
                        bindAndShow(ad)
                    }
                }

                override fun onFailedToReceiveAd(loaded: Ad?) {
                    main.post {
                        Log.d(TAG, "no fill mode=$mode")
                        tryMode(index + 1)
                    }
                }
            })
        }

        try {
            tryMode(0)
            main.postDelayed({
                if (!displayed.get() && !granted.get() && !finished.get()) {
                    Log.w(TAG, "rewarded wait timed out")
                    finishUnavailable()
                }
            }, 28_000)
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
        val ad = interstitial
        if (ad == null || !ad.isReady) {
            preloadInterstitial(activity)
            return
        }
        try {
            ad.showAd(object : AdDisplayListener {
                override fun adDisplayed(shown: Ad?) = Unit
                override fun adClicked(shown: Ad?) = Unit
                override fun adHidden(shown: Ad?) {
                    preloadInterstitial(activity)
                }

                override fun adNotDisplayed(shown: Ad?) {
                    preloadInterstitial(activity)
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "show interstitial failed", t)
            preloadInterstitial(activity)
        }
    }

    fun createBanner(context: Context): View? {
        return try {
            val banner = Banner(context)
            val height = (50 * context.resources.displayMetrics.density).toInt()
            banner.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            ).apply { gravity = Gravity.CENTER }
            runCatching { banner.loadAd() }
            val host = FrameLayout(context)
            host.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            host.addView(banner)
            host
        } catch (t: Throwable) {
            Log.w(TAG, "create banner failed", t)
            null
        }
    }

    companion object {
        private const val TAG = "KolponaAds"
    }
}
