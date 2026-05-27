package com.example.liftrix.demo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class DemoModeStore @Inject constructor(
    @Named("demoModeDataStore") private val dataStore: DataStore<Preferences>
) {
    val state: Flow<DemoModeState> = dataStore.data
        .map { preferences ->
            DemoModeState(
                enabled = preferences[Keys.Enabled] ?: false,
                sessionSeed = preferences[Keys.SessionSeed],
                activatedAtMillis = preferences[Keys.ActivatedAtMillis],
                lastDisabledAtMillis = preferences[Keys.LastDisabledAtMillis]
            )
        }
        .catch { emit(DemoModeState.Inactive) }

    suspend fun activate(sessionSeed: Long, activatedAtMillis: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.Enabled] = true
            preferences[Keys.SessionSeed] = sessionSeed
            preferences[Keys.ActivatedAtMillis] = activatedAtMillis
        }
    }

    suspend fun disable(disabledAtMillis: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.Enabled] = false
            preferences.remove(Keys.SessionSeed)
            preferences.remove(Keys.ActivatedAtMillis)
            preferences[Keys.LastDisabledAtMillis] = disabledAtMillis
        }
    }

    private object Keys {
        val Enabled = booleanPreferencesKey("enabled")
        val SessionSeed = longPreferencesKey("session_seed")
        val ActivatedAtMillis = longPreferencesKey("activated_at_millis")
        val LastDisabledAtMillis = longPreferencesKey("last_disabled_at_millis")
    }
}
