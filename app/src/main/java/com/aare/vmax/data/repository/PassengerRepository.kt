package com.aare.vmax.data.repository

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

// -----------------------------
// DATA MODEL
// -----------------------------
@Serializable
data class Passenger(
    val name: String,
    val age: Int,
    val gender: String,
    val berthPreference: String
)

// -----------------------------
// DATASTORE SETUP
// -----------------------------
private val Context.dataStore by preferencesDataStore(name = "passenger_store")

class PassengerRepository(
    private val context: Context
) {

    private val PASSENGER_KEY = stringPreferencesKey("passenger_data_v1")

    // JSON Parser (safe + future-proof)
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // -----------------------------
    // SAVE PASSENGER
    // -----------------------------
    suspend fun savePassenger(passenger: Passenger) {
        val json = encodePassenger(passenger)

        context.dataStore.edit { prefs ->
            prefs[PASSENGER_KEY] = json
        }
    }

    // -----------------------------
    // GET PASSENGER
    // -----------------------------
    fun getPassenger(): Flow<Passenger?> {
        return context.dataStore.data.map { prefs ->
            val json = prefs[PASSENGER_KEY] ?: return@map null
            decodePassenger(json)
        }
    }

    // -----------------------------
    // CLEAR DATA
    // -----------------------------
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // -----------------------------
    // JSON ENCODING
    // -----------------------------
    private fun encodePassenger(p: Passenger): String {
        return jsonParser.encodeToString(p)
    }

    private fun decodePassenger(data: String): Passenger {
        return jsonParser.decodeFromString(data)
    }
}
