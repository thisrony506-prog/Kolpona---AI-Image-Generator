package com.kolpona.ai.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kolpona.ai.domain.manager.CreditConfig
import com.kolpona.ai.domain.model.AspectRatio
import com.kolpona.ai.domain.model.ImageQuality
import com.kolpona.ai.domain.model.ImageStyle
import com.kolpona.ai.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kolpona_prefs")

class AppPreferences(context: Context) {
    private val dataStore = context.applicationContext.dataStore
    private val activeUid = MutableStateFlow("")

    val credits: Flow<Int> = combine(dataStore.data, activeUid) { data, uid ->
        if (uid.isBlank()) CreditConfig.DAILY_INITIAL_CREDITS
        else data[creditsKey(uid)] ?: CreditConfig.DAILY_INITIAL_CREDITS
    }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDING] ?: false }
    val themeMode: Flow<ThemeMode> = combine(dataStore.data, activeUid) { data, uid ->
        ThemeMode.fromId(data[themeKey(uid)] ?: ThemeMode.SYSTEM.name)
    }
    val defaultStyle: Flow<ImageStyle> = combine(dataStore.data, activeUid) { data, uid ->
        ImageStyle.fromId(data[styleKey(uid)] ?: ImageStyle.ALL.id)
    }
    val defaultAspectRatio: Flow<AspectRatio> = combine(dataStore.data, activeUid) { data, uid ->
        AspectRatio.fromId(data[aspectKey(uid)] ?: AspectRatio.SQUARE.id)
    }
    val imageQuality: Flow<ImageQuality> = combine(dataStore.data, activeUid) { data, uid ->
        ImageQuality.fromId(data[qualityKey(uid)] ?: ImageQuality.HIGH.id)
    }
    val enhancePrompts: Flow<Boolean> = combine(dataStore.data, activeUid) { data, uid ->
        data[enhanceKey(uid)] ?: true
    }

    fun activeUid(): String = activeUid.value

    fun bindUid(uid: String) {
        activeUid.value = uid
    }

    suspend fun setActiveUid(uid: String) {
        if (uid.isNotBlank()) migrateLegacyIfNeeded(uid)
        activeUid.value = uid
    }

    suspend fun getCredits(): Int {
        val uid = activeUid.value
        if (uid.isBlank()) return CreditConfig.DAILY_INITIAL_CREDITS
        return dataStore.data.first()[creditsKey(uid)] ?: CreditConfig.DAILY_INITIAL_CREDITS
    }

    suspend fun setCredits(value: Int) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[creditsKey(uid)] = value.coerceAtLeast(0) }
    }

    suspend fun getLastResetEpochDay(): Long {
        val uid = activeUid.value
        if (uid.isBlank()) return 0L
        return dataStore.data.first()[resetKey(uid)] ?: 0L
    }

    suspend fun setLastResetEpochDay(epochDay: Long) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[resetKey(uid)] = epochDay }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = complete }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[themeKey(uid)] = mode.name }
    }

    suspend fun setDefaultStyle(style: ImageStyle) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[styleKey(uid)] = style.id }
    }

    suspend fun setDefaultAspectRatio(ratio: AspectRatio) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[aspectKey(uid)] = ratio.id }
    }

    suspend fun setImageQuality(quality: ImageQuality) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[qualityKey(uid)] = quality.id }
    }

    suspend fun setEnhancePrompts(enabled: Boolean) {
        val uid = activeUid.value
        if (uid.isBlank()) return
        dataStore.edit { it[enhanceKey(uid)] = enabled }
    }

    suspend fun themeModeValue(): ThemeMode = themeMode.first()
    suspend fun styleValue(): ImageStyle = defaultStyle.first()
    suspend fun aspectValue(): AspectRatio = defaultAspectRatio.first()
    suspend fun qualityValue(): ImageQuality = imageQuality.first()
    suspend fun enhanceValue(): Boolean = enhancePrompts.first()

    suspend fun getPendingUpdate(): PendingUpdate? {
        val data = dataStore.data.first()
        val code = data[KEY_UPDATE_CODE] ?: return null
        if (code <= 0) return null
        return PendingUpdate(
            versionCode = code,
            versionName = data[KEY_UPDATE_NAME].orEmpty(),
            apkUrl = data[KEY_UPDATE_URL].orEmpty(),
            sha256 = data[KEY_UPDATE_SHA].orEmpty(),
            notes = data[KEY_UPDATE_NOTES].orEmpty()
        )
    }

    suspend fun setPendingUpdate(update: PendingUpdate) {
        dataStore.edit {
            it[KEY_UPDATE_CODE] = update.versionCode
            it[KEY_UPDATE_NAME] = update.versionName
            it[KEY_UPDATE_URL] = update.apkUrl
            it[KEY_UPDATE_SHA] = update.sha256
            it[KEY_UPDATE_NOTES] = update.notes
        }
    }

    suspend fun clearPendingUpdate() {
        dataStore.edit {
            it.remove(KEY_UPDATE_CODE)
            it.remove(KEY_UPDATE_NAME)
            it.remove(KEY_UPDATE_URL)
            it.remove(KEY_UPDATE_SHA)
            it.remove(KEY_UPDATE_NOTES)
        }
    }

    suspend fun getLastNotifiedUpdateCode(): Int =
        dataStore.data.first()[KEY_NOTIFIED_UPDATE] ?: 0

    suspend fun setLastNotifiedUpdateCode(code: Int) {
        dataStore.edit { it[KEY_NOTIFIED_UPDATE] = code }
    }

    suspend fun getWelcomedUid(): String =
        dataStore.data.first()[KEY_WELCOMED_UID].orEmpty()

    suspend fun setWelcomedUid(uid: String) {
        dataStore.edit { it[KEY_WELCOMED_UID] = uid }
    }

    private suspend fun migrateLegacyIfNeeded(uid: String) {
        dataStore.edit { prefs ->
            if (!prefs[KEY_LEGACY_MIGRATED_UID].isNullOrBlank()) return@edit
            prefs[creditsKey(uid)] = prefs[KEY_CREDITS] ?: CreditConfig.DAILY_INITIAL_CREDITS
            prefs[resetKey(uid)] = prefs[KEY_LAST_RESET] ?: 0L
            prefs[themeKey(uid)] = prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name
            prefs[styleKey(uid)] = prefs[KEY_STYLE] ?: ImageStyle.ALL.id
            prefs[aspectKey(uid)] = prefs[KEY_ASPECT] ?: AspectRatio.SQUARE.id
            prefs[qualityKey(uid)] = prefs[KEY_QUALITY] ?: ImageQuality.HIGH.id
            prefs[enhanceKey(uid)] = prefs[KEY_ENHANCE] ?: true
            prefs[KEY_LEGACY_MIGRATED_UID] = uid
        }
    }

    private companion object {
        val KEY_CREDITS = intPreferencesKey("credits")
        val KEY_LAST_RESET = longPreferencesKey("last_credit_reset_epoch_day")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_STYLE = stringPreferencesKey("default_style")
        val KEY_ASPECT = stringPreferencesKey("default_aspect")
        val KEY_QUALITY = stringPreferencesKey("image_quality")
        val KEY_ENHANCE = booleanPreferencesKey("enhance_prompts")
        val KEY_UPDATE_CODE = intPreferencesKey("pending_update_version_code")
        val KEY_UPDATE_NAME = stringPreferencesKey("pending_update_version_name")
        val KEY_UPDATE_URL = stringPreferencesKey("pending_update_apk_url")
        val KEY_UPDATE_SHA = stringPreferencesKey("pending_update_sha256")
        val KEY_UPDATE_NOTES = stringPreferencesKey("pending_update_notes")
        val KEY_NOTIFIED_UPDATE = intPreferencesKey("notified_update_version_code")
        val KEY_WELCOMED_UID = stringPreferencesKey("welcomed_uid")
        val KEY_LEGACY_MIGRATED_UID = stringPreferencesKey("legacy_prefs_migrated_uid")

        fun creditsKey(uid: String) = intPreferencesKey("credits_$uid")
        fun resetKey(uid: String) = longPreferencesKey("reset_$uid")
        fun themeKey(uid: String) = stringPreferencesKey("theme_$uid")
        fun styleKey(uid: String) = stringPreferencesKey("style_$uid")
        fun aspectKey(uid: String) = stringPreferencesKey("aspect_$uid")
        fun qualityKey(uid: String) = stringPreferencesKey("quality_$uid")
        fun enhanceKey(uid: String) = booleanPreferencesKey("enhance_$uid")
    }
}

data class PendingUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String
)
