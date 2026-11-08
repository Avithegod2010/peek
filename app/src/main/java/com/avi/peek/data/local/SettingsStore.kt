package com.avi.peek.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.peekSettings: DataStore<Preferences> by preferencesDataStore(name = "peek_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PaletteStyle(val label: String) {
    LAVENDER("Lavender"),
    OCEAN("Ocean"),
    FOREST("Forest"),
    SUNSET("Sunset"),
    ROSE("Rose"),
    MINT("Mint"),
    COFFEE("Coffee"),
    SAKURA("Sakura"),
    CITRUS("Citrus"),
    INDIGO("Indigo"),
    SLATE("Slate"),
    SAND("Sand"),
    AURORA("Aurora"),
}

data class PeekSettings(
    val onboarded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val palette: PaletteStyle = PaletteStyle.LAVENDER,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val syncOnOpen: Boolean = true,
    val enrichFeed: Boolean = true,
    val syncDepth: Int = 1,
    val wifiOnlySync: Boolean = false,
    val backgroundSync: Boolean = false,
    val backgroundIntervalHours: Int = 6,
    val notifications: Boolean = false,
    val autoplayReels: Boolean = true,
    val muteVideos: Boolean = true,
    val dataSaver: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val appLock: Boolean = false,
    val oneHanded: Boolean = false,
    val downloadChargingOnly: Boolean = false,
    val downloadQueuePerProfile: Int = 0,
    val gallerySidecars: Boolean = false,
    val lastFullSync: Long = 0L,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val PALETTE = stringPreferencesKey("palette")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val SYNC_ON_OPEN = booleanPreferencesKey("sync_on_open")
        val ENRICH = booleanPreferencesKey("enrich_feed")
        val SYNC_DEPTH = intPreferencesKey("sync_depth")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val BACKGROUND_SYNC = booleanPreferencesKey("background_sync")
        val BACKGROUND_INTERVAL = intPreferencesKey("background_interval")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val AUTOPLAY = booleanPreferencesKey("autoplay_reels")
        val MUTE = booleanPreferencesKey("mute_videos")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val HIDE_LIKES = booleanPreferencesKey("hide_likes")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val ONE_HANDED = booleanPreferencesKey("one_handed")
        val DOWNLOAD_CHARGING = booleanPreferencesKey("download_charging")
        val DOWNLOAD_QUEUE_LIMIT = intPreferencesKey("download_queue_limit")
        val GALLERY_SIDECARS = booleanPreferencesKey("gallery_sidecars")
        val LAST_SYNC = stringPreferencesKey("last_full_sync")
    }

    val settings: Flow<PeekSettings> = context.peekSettings.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs ->
            PeekSettings(
                onboarded = prefs[Keys.ONBOARDED] ?: false,
                themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = prefs[Keys.DYNAMIC] ?: true,
                amoled = prefs[Keys.AMOLED] ?: false,
                palette = runCatching { PaletteStyle.valueOf(prefs[Keys.PALETTE] ?: "LAVENDER") }.getOrDefault(PaletteStyle.LAVENDER),
                haptics = prefs[Keys.HAPTICS] ?: true,
                reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
                syncOnOpen = prefs[Keys.SYNC_ON_OPEN] ?: true,
                enrichFeed = prefs[Keys.ENRICH] ?: true,
                syncDepth = (prefs[Keys.SYNC_DEPTH] ?: 1).coerceIn(0, 6),
                wifiOnlySync = prefs[Keys.WIFI_ONLY] ?: false,
                backgroundSync = prefs[Keys.BACKGROUND_SYNC] ?: false,
                backgroundIntervalHours = (prefs[Keys.BACKGROUND_INTERVAL] ?: 6).coerceIn(1, 24),
                notifications = prefs[Keys.NOTIFICATIONS] ?: false,
                autoplayReels = prefs[Keys.AUTOPLAY] ?: true,
                muteVideos = prefs[Keys.MUTE] ?: true,
                dataSaver = prefs[Keys.DATA_SAVER] ?: false,
                hideLikeCounts = prefs[Keys.HIDE_LIKES] ?: false,
                appLock = prefs[Keys.APP_LOCK] ?: false,
                oneHanded = prefs[Keys.ONE_HANDED] ?: false,
                downloadChargingOnly = prefs[Keys.DOWNLOAD_CHARGING] ?: false,
                downloadQueuePerProfile = (prefs[Keys.DOWNLOAD_QUEUE_LIMIT] ?: 0).coerceIn(0, 500),
                gallerySidecars = prefs[Keys.GALLERY_SIDECARS] ?: false,
                lastFullSync = prefs[Keys.LAST_SYNC]?.toLongOrNull() ?: 0L,
            )
        }

    suspend fun setOnboarded(value: Boolean) = edit { it[Keys.ONBOARDED] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC] = value }
    suspend fun setAmoled(value: Boolean) = edit { it[Keys.AMOLED] = value }
    suspend fun setPalette(value: PaletteStyle) = edit { it[Keys.PALETTE] = value.name }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.HAPTICS] = value }
    suspend fun setReduceMotion(value: Boolean) = edit { it[Keys.REDUCE_MOTION] = value }
    suspend fun setSyncOnOpen(value: Boolean) = edit { it[Keys.SYNC_ON_OPEN] = value }
    suspend fun setEnrichFeed(value: Boolean) = edit { it[Keys.ENRICH] = value }
    suspend fun setSyncDepth(value: Int) = edit { it[Keys.SYNC_DEPTH] = value.coerceIn(0, 6) }
    suspend fun setWifiOnly(value: Boolean) = edit { it[Keys.WIFI_ONLY] = value }
    suspend fun setBackgroundSync(value: Boolean) = edit { it[Keys.BACKGROUND_SYNC] = value }
    suspend fun setBackgroundInterval(value: Int) = edit { it[Keys.BACKGROUND_INTERVAL] = value.coerceIn(1, 24) }
    suspend fun setNotifications(value: Boolean) = edit { it[Keys.NOTIFICATIONS] = value }
    suspend fun setAutoplayReels(value: Boolean) = edit { it[Keys.AUTOPLAY] = value }
    suspend fun setMuteVideos(value: Boolean) = edit { it[Keys.MUTE] = value }
    suspend fun setDataSaver(value: Boolean) = edit { it[Keys.DATA_SAVER] = value }
    suspend fun setHideLikeCounts(value: Boolean) = edit { it[Keys.HIDE_LIKES] = value }
    suspend fun setAppLock(value: Boolean) = edit { it[Keys.APP_LOCK] = value }
    suspend fun setOneHanded(value: Boolean) = edit { it[Keys.ONE_HANDED] = value }
    suspend fun setDownloadChargingOnly(value: Boolean) = edit { it[Keys.DOWNLOAD_CHARGING] = value }
    suspend fun setDownloadQueuePerProfile(value: Int) =
        edit { it[Keys.DOWNLOAD_QUEUE_LIMIT] = value.coerceIn(0, 500) }
    suspend fun setGallerySidecars(value: Boolean) = edit { it[Keys.GALLERY_SIDECARS] = value }
    suspend fun setLastFullSync(value: Long) = edit { it[Keys.LAST_SYNC] = value.toString() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.peekSettings.edit(block)
    }
}
