package com.kolpona.app.ads

import android.app.Activity
import android.app.Application
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppAd.AdMode
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolated Start.io rewarded-video manager.
 *
 * Rewards are granted only from the SDK video-completed callback, and only once per shown ad.
 */
class StartIoAdManager(
    private val app: Application
) {
    @Volatile
    private var rewardedAd: StartAppAd? = null

    @Volatile
    private var loaded: Boolean = false

    private val rewardLock = Any()
    private var currentShowToken: String? = null
    private val rewardConsumed = AtomicBoolean(false)

    fun initialize() {
        try {
            StartAppSDK.initParams(app, AdsConfig.APP_ID)
                .setReturnAdsEnabled(false)
                .init()
            runCatching { StartAppAd::class.java.getMethod("disableSplash").invoke(null) }
            runCatching { StartAppAd::class.java.getMethod("disableAutoInterstitial").invoke(null) }
            if (AdsConfig.isTestMode) {
                StartAppSDK.setTestAdsEnabled(true)
            }
        } catch (_: Throwable) {
            // Ads must never crash the app.
        }
    }

    fun preload(activity: Activity) {
        try {
            val ad = StartAppAd(activity)
            loaded = false
            ad.loadAd(AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    loaded = true
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    loaded = false
                }
            })
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
            ad.setVideoListener {
                val grant = synchronized(rewardLock) {
                    if (currentShowToken == token && rewardConsumed.compareAndSet(false, true)) {
                        token
                    } else {
                        null
                    }
                }
                if (grant != null) {
                    onRewarded(grant)
                }
            }
            loaded = false
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad) {
                    onClosed()
                    preload(activity)
                }

                override fun adDisplayed(ad: Ad) = Unit

                override fun adClicked(ad: Ad) = Unit

                override fun adNotDisplayed(ad: Ad) {
                    onClosed()
                    preload(activity)
                }
            })
        } catch (_: Throwable) {
            onUnavailable()
            preload(activity)
        }
    }
}
