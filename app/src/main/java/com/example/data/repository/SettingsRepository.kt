package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "netra_settings")

class SettingsRepository(private val context: Context) {
    object Keys {
        val MONITOR_THERMAL = booleanPreferencesKey("monitor_thermal")

        val MONITOR_WEATHER = booleanPreferencesKey("monitor_weather")
        val NOTIFY_WEATHER = booleanPreferencesKey("notify_weather")
        val ANNOUNCE_WEATHER = booleanPreferencesKey("announce_weather")
        val MONITOR_LIGHT = booleanPreferencesKey("monitor_light")
        val NOTIFY_LIGHT = booleanPreferencesKey("notify_light")
        val ANNOUNCE_LIGHT = booleanPreferencesKey("announce_light")
        val MONITOR_BLUETOOTH = booleanPreferencesKey("monitor_bluetooth")
        val NOTIFY_BLUETOOTH = booleanPreferencesKey("notify_bluetooth")
        val ANNOUNCE_BLUETOOTH = booleanPreferencesKey("announce_bluetooth")
        val MONITOR_LOCATION = booleanPreferencesKey("monitor_location")
        val NOTIFY_LOCATION = booleanPreferencesKey("notify_location")
        val ANNOUNCE_LOCATION = booleanPreferencesKey("announce_location")

        val MONITOR_MAGNETIC = booleanPreferencesKey("monitor_magnetic")
        val NOTIFY_MAGNETIC = booleanPreferencesKey("notify_magnetic")
        val ANNOUNCE_MAGNETIC = booleanPreferencesKey("announce_magnetic")
        val MONITOR_PROXIMITY = booleanPreferencesKey("monitor_proximity")
        val NOTIFY_PROXIMITY = booleanPreferencesKey("notify_proximity")
        val ANNOUNCE_PROXIMITY = booleanPreferencesKey("announce_proximity")
        val REFRESH_INTERVAL_MS = intPreferencesKey("refresh_interval_ms")
        val THERMAL_THRESHOLD_C = intPreferencesKey("thermal_threshold_c")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
                val TRAVEL_MODE = stringPreferencesKey("travel_mode")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val DEVELOPER_PIN_HASH = stringPreferencesKey("developer_pin_hash")
        val DEVELOPER_PIN_SALT = stringPreferencesKey("developer_pin_salt")
        val DEVELOPER_PIN_CHANGED_DATE = stringPreferencesKey("developer_pin_changed_date")
        val DEVELOPER_PIN_STRENGTH = stringPreferencesKey("developer_pin_strength")
        val DEVELOPER_PIN_FAILED_ATTEMPTS = intPreferencesKey("developer_pin_failed_attempts")
        val DEVELOPER_PIN_RECOVERY_KEY = stringPreferencesKey("developer_pin_recovery_key")
        val FAILED_LOGIN_TIMESTAMPS = stringPreferencesKey("failed_login_timestamps")
        val LOCKOUT_UNTIL_TIMESTAMP = longPreferencesKey("lockout_until_timestamp")
    }

    val monitorThermal: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_THERMAL] ?: true }

    val monitorMagnetic: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_MAGNETIC] ?: true }
    val notifyMagnetic: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_MAGNETIC] ?: true }
    val announceMagnetic: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANNOUNCE_MAGNETIC] ?: true }
    val monitorProximity: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_PROXIMITY] ?: true }
    val notifyProximity: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_PROXIMITY] ?: true }
    val announceProximity: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANNOUNCE_PROXIMITY] ?: true }

    val monitorWeather: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_WEATHER] ?: true }
    val notifyWeather: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_WEATHER] ?: true }
    val announceWeather: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANNOUNCE_WEATHER] ?: true }
    val monitorLight: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_LIGHT] ?: true }
    val notifyLight: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_LIGHT] ?: true }
    val announceLight: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANNOUNCE_LIGHT] ?: true }
    val monitorBluetooth: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_BLUETOOTH] ?: true }
    val notifyBluetooth: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_BLUETOOTH] ?: true }
    val announceBluetooth: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANNOUNCE_BLUETOOTH] ?: true }
    val monitorLocation: Flow<Boolean> = context.dataStore.data.map { it[Keys.MONITOR_LOCATION] ?: true }
    val notifyLocation: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_LOCATION] ?: true }
    val announceLocation: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANNOUNCE_LOCATION] ?: true }
    val refreshIntervalMs: Flow<Int> = context.dataStore.data.map { it[Keys.REFRESH_INTERVAL_MS] ?: 300 }
    val thermalThresholdC: Flow<Int> = context.dataStore.data.map { it[Keys.THERMAL_THRESHOLD_C] ?: 45 }
    val encryptionEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ENCRYPTION_ENABLED] ?: true }
    val travelMode: Flow<String> = context.dataStore.data.map { it[Keys.TRAVEL_MODE] ?: "AUTO" }
    val developerMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEVELOPER_MODE] ?: false }
    val developerPinHash: Flow<String?> = context.dataStore.data.map { it[Keys.DEVELOPER_PIN_HASH] }
    val developerPinSalt: Flow<String?> = context.dataStore.data.map { it[Keys.DEVELOPER_PIN_SALT] }
    val developerPinChangedDate: Flow<String?> = context.dataStore.data.map { it[Keys.DEVELOPER_PIN_CHANGED_DATE] }
    val developerPinStrength: Flow<String?> = context.dataStore.data.map { it[Keys.DEVELOPER_PIN_STRENGTH] }
    val developerPinFailedAttempts: Flow<Int> = context.dataStore.data.map { it[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] ?: 0 }
    val developerPinRecoveryKey: Flow<String?> = context.dataStore.data.map { it[Keys.DEVELOPER_PIN_RECOVERY_KEY] }
    val failedLoginTimestamps: Flow<List<Long>> = context.dataStore.data.map { preferences ->
        val raw = preferences[Keys.FAILED_LOGIN_TIMESTAMPS] ?: ""
        if (raw.isEmpty()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
    }
    val lockoutUntilTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[Keys.LOCKOUT_UNTIL_TIMESTAMP] ?: 0L
    }

    suspend fun setMonitorThermal(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_THERMAL] = enabled }
    }



    suspend fun setMonitorMagnetic(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_MAGNETIC] = enabled }
    }

    suspend fun setMonitorProximity(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_PROXIMITY] = enabled }
    }



    suspend fun setMonitorWeather(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_WEATHER] = enabled }
    }

    suspend fun setNotifyWeather(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_WEATHER] = enabled }
    }

    suspend fun setAnnounceWeather(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_WEATHER] = enabled }
    }

    suspend fun setMonitorLight(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_LIGHT] = enabled }
    }

    suspend fun setNotifyLight(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_LIGHT] = enabled }
    }

    suspend fun setAnnounceLight(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_LIGHT] = enabled }
    }

    suspend fun setMonitorBluetooth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_BLUETOOTH] = enabled }
    }

    suspend fun setNotifyBluetooth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_BLUETOOTH] = enabled }
    }

    suspend fun setAnnounceBluetooth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_BLUETOOTH] = enabled }
    }

    suspend fun setMonitorLocation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONITOR_LOCATION] = enabled }
    }

    suspend fun setNotifyLocation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_LOCATION] = enabled }
    }

    suspend fun setAnnounceLocation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_LOCATION] = enabled }
    }



    suspend fun setNotifyMagnetic(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_MAGNETIC] = enabled }
    }

    suspend fun setAnnounceMagnetic(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_MAGNETIC] = enabled }
    }

    suspend fun setNotifyProximity(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_PROXIMITY] = enabled }
    }

    suspend fun setAnnounceProximity(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANNOUNCE_PROXIMITY] = enabled }
    }

    suspend fun setRefreshIntervalMs(interval: Int) {
        context.dataStore.edit { it[Keys.REFRESH_INTERVAL_MS] = interval }
    }

    suspend fun setThermalThresholdC(threshold: Int) {
        context.dataStore.edit { it[Keys.THERMAL_THRESHOLD_C] = threshold }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setTravelMode(mode: String) {
        context.dataStore.edit { it[Keys.TRAVEL_MODE] = mode }
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEVELOPER_MODE] = enabled }
    }

    suspend fun saveDeveloperPin(hash: String, salt: String, strength: String, dateStr: String, recoveryKey: String) {
        context.dataStore.edit {
            it[Keys.DEVELOPER_PIN_HASH] = hash
            it[Keys.DEVELOPER_PIN_SALT] = salt
            it[Keys.DEVELOPER_PIN_STRENGTH] = strength
            it[Keys.DEVELOPER_PIN_CHANGED_DATE] = dateStr
            it[Keys.DEVELOPER_PIN_RECOVERY_KEY] = recoveryKey
            it[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] = 0 // Reset failures on change
        }
    }

    suspend fun incrementFailedAttempts() {
        context.dataStore.edit {
            val current = it[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] ?: 0
            it[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] = current + 1
        }
    }

    suspend fun recordFailedLoginAttempt(timestamp: Long) {
        context.dataStore.edit { preferences ->
            val raw = preferences[Keys.FAILED_LOGIN_TIMESTAMPS] ?: ""
            val currentList = if (raw.isEmpty()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
            val newList = (currentList + timestamp).takeLast(10) // Keep last 10 failed attempts
            preferences[Keys.FAILED_LOGIN_TIMESTAMPS] = newList.joinToString(",")
            
            // Increment the regular failure count too
            val currentCount = preferences[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] ?: 0
            preferences[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] = currentCount + 1

            // Trigger temporary lockout after 5 consecutive failures
            if (newList.size >= 5) {
                preferences[Keys.LOCKOUT_UNTIL_TIMESTAMP] = System.currentTimeMillis() + 30000L // 30s lockout
            }
        }
    }

    suspend fun clearFailedAttemptsAndLockout() {
        context.dataStore.edit { preferences ->
            preferences[Keys.FAILED_LOGIN_TIMESTAMPS] = ""
            preferences[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] = 0
            preferences[Keys.LOCKOUT_UNTIL_TIMESTAMP] = 0L
        }
    }

    suspend fun resetFailedAttempts() {
        context.dataStore.edit {
            it[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] = 0
        }
    }

    suspend fun clearDeveloperPin() {
        context.dataStore.edit {
            it.remove(Keys.DEVELOPER_PIN_HASH)
            it.remove(Keys.DEVELOPER_PIN_SALT)
            it.remove(Keys.DEVELOPER_PIN_STRENGTH)
            it.remove(Keys.DEVELOPER_PIN_CHANGED_DATE)
            it.remove(Keys.DEVELOPER_PIN_RECOVERY_KEY)
            it[Keys.DEVELOPER_PIN_FAILED_ATTEMPTS] = 0
        }
    }

    suspend fun saveFeatureStatus(featureId: String, status: String) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("feature_status_$featureId")] = status
        }
    }

    fun getFeatureStatus(featureId: String): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[stringPreferencesKey("feature_status_$featureId")]
        }
    }
}
