package com.aare.vmax.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class BookingConfig(
    val trainNumber: String,
    val bookingClass: String,
    val quota: String = "TATKAL",
    val paymentMethod: String = "UPI"
)

private val Context.configDataStore by preferencesDataStore(name = "config_store")

class ConfigStore(private val context: Context) {

    private val CONFIG_KEY = stringPreferencesKey("booking_config_json_v1")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun saveConfig(config: BookingConfig) {
        context.configDataStore.edit { prefs ->
            prefs[CONFIG_KEY] = json.encodeToString(config)
        }
    }

    fun getConfig(): Flow<BookingConfig?> {
        return context.configDataStore.data.map { prefs ->
            val data = prefs[CONFIG_KEY] ?: return@map null

            try {
                json.decodeFromString<BookingConfig>(data)
            } catch (e: Exception) {
                null
            }
        }
    }
}
