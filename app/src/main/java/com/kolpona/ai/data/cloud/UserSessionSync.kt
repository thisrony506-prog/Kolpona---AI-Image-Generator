package com.kolpona.ai.data.cloud

import com.google.firebase.auth.FirebaseUser
import com.kolpona.ai.data.auth.AuthRepository
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.repository.ChatRepository
import com.kolpona.ai.data.repository.HistoryRepository
import com.kolpona.ai.domain.manager.CreditManager
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Binds Firebase Auth UID to local caches and Firestore `users/{uid}`.
 * Each signed-in account only sees and writes its own document tree.
 */
class UserSessionSync(
    private val auth: AuthRepository,
    private val cloud: UserCloudRepository,
    private val preferences: AppPreferences,
    private val history: HistoryRepository,
    private val chats: ChatRepository,
    private val credits: CreditManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var boundUid: String? = null

    fun start() {
        scope.launch {
            auth.user.map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid.isNullOrBlank()) {
                    unbind()
                } else {
                    val user = auth.currentUser
                    if (user != null) bind(user)
                }
            }
        }
    }

    private suspend fun unbind() {
        mutex.withLock {
            boundUid = null
            history.setOwnerUid("")
            chats.setOwnerUid("")
            preferences.setActiveUid("")
        }
    }

    suspend fun refresh(): Boolean {
        val user = auth.currentUser ?: return true
        attach(user)
        return runCatching {
            sync(user)
            true
        }.getOrDefault(false)
    }

    private suspend fun bind(user: FirebaseUser) {
        val first = attach(user)
        if (first) runCatching { sync(user) }
    }

    private suspend fun attach(user: FirebaseUser): Boolean = mutex.withLock {
        if (boundUid == user.uid) return@withLock false
        history.setOwnerUid(user.uid)
        chats.setOwnerUid(user.uid)
        preferences.setActiveUid(user.uid)
        history.claimOrphans(user.uid)
        chats.claimOrphans(user.uid)
        boundUid = user.uid
        true
    }

    private suspend fun sync(user: FirebaseUser) {
        val settings = currentSettings()
        val wallet = CloudWallet(
            credits = preferences.getCredits(),
            lastResetEpochDay = preferences.getLastResetEpochDay()
        )
        val profile = cloud.ensureProfile(user, settings, wallet)
        if (profile.existed) {
            profile.settings?.let { applySettings(it) }
            profile.wallet?.let { applyWallet(it) }
        }
        credits.refreshDailyCredits()
        cloud.saveWallet(
            user.uid,
            CloudWallet(preferences.getCredits(), preferences.getLastResetEpochDay())
        )
        cloud.pullHistory(user.uid).forEach { history.insertLocal(it) }
        cloud.pullChats(user.uid).forEach { chats.replaceFromCloud(it.session, it.messages) }
        if (!profile.existed) pushLocal(user.uid)
    }

    private suspend fun pushLocal(uid: String) {
        history.listLocal().forEach { image ->
            cloud.enqueue { cloud.upsertHistory(uid, image) }
        }
        chats.listLocal().forEach { session ->
            val messages = chats.rawMessages(session.id)
            cloud.enqueue { cloud.upsertChat(uid, session, messages) }
        }
        cloud.saveSettings(uid, currentSettings())
    }

    private suspend fun currentSettings() = CloudSettings(
        themeMode = preferences.themeModeValue().name,
        style = preferences.styleValue().id,
        aspect = preferences.aspectValue().id,
        quality = preferences.qualityValue().id,
        enhance = preferences.enhanceValue()
    )

    private suspend fun applySettings(settings: CloudSettings) {
        preferences.setThemeMode(ThemeMode.fromId(settings.themeMode))
        preferences.setDefaultStyle(ImageStyle.fromId(settings.style))
        preferences.setDefaultAspectRatio(AspectRatio.fromId(settings.aspect))
        preferences.setImageQuality(ImageQuality.fromId(settings.quality))
        preferences.setEnhancePrompts(settings.enhance)
    }

    private suspend fun applyWallet(wallet: CloudWallet) {
        preferences.setCredits(wallet.credits)
        preferences.setLastResetEpochDay(wallet.lastResetEpochDay)
    }
}
