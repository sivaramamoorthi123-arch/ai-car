package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.SettingsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "robot_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ESP32_IP = stringPreferencesKey("esp32_ip")
        val WS_PORT = intPreferencesKey("ws_port")
        val CAM_URL = stringPreferencesKey("cam_url")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val SPEED_LIMIT = intPreferencesKey("speed_limit")
        val WATCHDOG_MS = longPreferencesKey("watchdog_ms")
        val OBSTACLE_DIST = intPreferencesKey("obstacle_dist")
        val BATTERY_CELLS = intPreferencesKey("battery_cells")
        val CRITICAL_BATT = intPreferencesKey("critical_batt")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    }

    val settingsFlow: Flow<SettingsConfig> = context.dataStore.data.map { prefs ->
        SettingsConfig(
            esp32Ip = prefs[Keys.ESP32_IP] ?: "192.168.4.1",
            wsPort = prefs[Keys.WS_PORT] ?: 81,
            camUrl = prefs[Keys.CAM_URL] ?: "http://192.168.4.1:81/stream",
            isDemoMode = prefs[Keys.DEMO_MODE] ?: true,
            speedLimitPercent = prefs[Keys.SPEED_LIMIT] ?: 80,
            watchdogTimeoutMs = prefs[Keys.WATCHDOG_MS] ?: 500L,
            obstacleSafetyDistanceCm = prefs[Keys.OBSTACLE_DIST] ?: 20,
            batteryCellCount = prefs[Keys.BATTERY_CELLS] ?: 2,
            criticalBatteryThreshold = prefs[Keys.CRITICAL_BATT] ?: 15,
            ttsFeedbackEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true
        )
    }

    suspend fun updateSettings(settings: SettingsConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ESP32_IP] = settings.esp32Ip
            prefs[Keys.WS_PORT] = settings.wsPort
            prefs[Keys.CAM_URL] = settings.camUrl
            prefs[Keys.DEMO_MODE] = settings.isDemoMode
            prefs[Keys.SPEED_LIMIT] = settings.speedLimitPercent
            prefs[Keys.WATCHDOG_MS] = settings.watchdogTimeoutMs
            prefs[Keys.OBSTACLE_DIST] = settings.obstacleSafetyDistanceCm
            prefs[Keys.BATTERY_CELLS] = settings.batteryCellCount
            prefs[Keys.CRITICAL_BATT] = settings.criticalBatteryThreshold
            prefs[Keys.TTS_ENABLED] = settings.ttsFeedbackEnabled
            prefs[Keys.HAPTIC_ENABLED] = settings.hapticEnabled
        }
    }
}
