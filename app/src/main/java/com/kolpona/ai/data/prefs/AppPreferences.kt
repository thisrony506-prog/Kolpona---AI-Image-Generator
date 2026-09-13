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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kolpona_prefs")

class AppPreferences(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val credits: Flow<Int> = dataStore.data.map { it[KEY_CREDITS] ?: CreditConfig.DAILY_INITIAL_CREDITS }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[KEY_ONBOARDING] ?: false }
    val themeMode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.fromId(it[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
    val defaultStyle: Flow<ImageStyle> = dataStore.data.map { ImageStyle.fromId(it[KEY_STYLE] ?: ImageStyle.ALL.id) }
    val defaultAspectRatio: Flow<AspectRatio> = dataStore.data.map {
        AspectRatio.fromId(it[KEY_ASPECT] ?: AspectRatio.SQUARE.id)
    }
    val imageQuality: Flow<ImageQuality> = dataStore.data.map {
        ImageQuality.fromId(it[KEY_QUALITY] ?: ImageQuality.HIGH.id)
    }
    val enhancePrompts: Flow<Boolean> = dataStore.data.map { it[KEY_ENHANCE] ?: true }

    suspend fun getCredits(): Int = credits.first()

    suspend fun setCredits(value: Int) {
        dataStore.edit { it[KEY_CREDITS] = value.coerceAtLeast(0) }
    }

    suspend fun getLastResetEpochDay(): Long = dataStore.data.first()[KEY_LAST_RESET] ?: 0L

    suspend fun setLastResetEpochDay(epochDay: Long) {
        dataStore.edit { it[KEY_LAST_RESET] = epochDay }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING] = complete }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDefaultStyle(style: ImageStyle) {
        dataStore.edit { it[KEY_STYLE] = style.id }
    }

    suspend fun setDefaultAspectRatio(ratio: AspectRatio) {
        dataStore.edit { it[KEY_ASPECT] = ratio.id }
    }

    suspend fun setImageQuality(quality: ImageQuality) {
        dataStore.edit { it[KEY_QUALITY] = quality.id }
    }

    suspend fun setEnhancePrompts(enabled: Boolean) {
        dataStore.edit { it[KEY_ENHANCE] = enabled }
    }

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
    }
}

data class PendingUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String
)
