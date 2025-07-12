package com.example.liftrix.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.repository.ActiveWorkoutSessionRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentSessionStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeWorkoutSessionRepository: ActiveWorkoutSessionRepository,
    private val gson: Gson
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "live_workout_session")
    
    private val sessionIdKey = stringPreferencesKey("session_id")
    private val sessionDataKey = stringPreferencesKey("session_data")
    private val startTimeKey = longPreferencesKey("start_time")
    private val totalPausedDurationKey = longPreferencesKey("total_paused_duration")
    private val isRunningKey = stringPreferencesKey("is_running")
    private val pauseStartTimeKey = longPreferencesKey("pause_start_time")
    
    suspend fun saveSession(
        session: ActiveWorkoutSession,
        startTime: Long,
        totalPausedDuration: Long,
        isRunning: Boolean = true,
        pauseStartTime: Long? = null
    ) {
        try {
            context.dataStore.edit { preferences ->
                preferences[sessionIdKey] = session.id.value
                preferences[sessionDataKey] = gson.toJson(session)
                preferences[startTimeKey] = startTime
                preferences[totalPausedDurationKey] = totalPausedDuration
                preferences[isRunningKey] = isRunning.toString()
                
                pauseStartTime?.let { time ->
                    preferences[pauseStartTimeKey] = time
                }
            }
            
            Timber.d("Session saved to persistent storage: ${session.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save session to persistent storage")
        }
    }
    
    suspend fun updateSessionState(
        sessionId: WorkoutSessionId,
        isRunning: Boolean,
        totalPausedDuration: Long = 0L,
        pauseStartTime: Long? = null
    ) {
        try {
            context.dataStore.edit { preferences ->
                preferences[isRunningKey] = isRunning.toString()
                preferences[totalPausedDurationKey] = totalPausedDuration
                
                if (pauseStartTime != null) {
                    preferences[pauseStartTimeKey] = pauseStartTime
                } else {
                    preferences.remove(pauseStartTimeKey)
                }
            }
            
            Timber.d("Session state updated: running=$isRunning")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update session state")
        }
    }
    
    suspend fun updateSessionProgress(session: ActiveWorkoutSession) {
        try {
            context.dataStore.edit { preferences ->
                preferences[sessionDataKey] = gson.toJson(session)
            }
            
            Timber.d("Session progress updated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update session progress")
        }
    }
    
    suspend fun getStoredSession(): StoredSession? {
        return try {
            val preferences = context.dataStore.data.first()
            val sessionIdValue = preferences[sessionIdKey]
            val sessionDataJson = preferences[sessionDataKey]
            
            if (sessionIdValue != null && sessionDataJson != null) {
                val sessionId = WorkoutSessionId(sessionIdValue)
                
                // First try to get the latest session from repository
                val latestSession = activeWorkoutSessionRepository.getSessionById(sessionId)
                    .getOrNull()
                val session = latestSession ?: run {
                    // Fallback to stored session data if repository doesn't have it
                    gson.fromJson(sessionDataJson, ActiveWorkoutSession::class.java)
                }
                
                val startTime = preferences[startTimeKey] ?: System.currentTimeMillis()
                val totalPausedDuration = preferences[totalPausedDurationKey] ?: 0L
                val isRunning = preferences[isRunningKey]?.toBoolean() ?: true
                val pauseStartTime = preferences[pauseStartTimeKey]
                
                StoredSession(
                    session = session,
                    startTime = startTime,
                    totalPausedDuration = totalPausedDuration,
                    isRunning = isRunning,
                    pauseStartTime = pauseStartTime
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve stored session")
            null
        }
    }
    
    suspend fun clearSession() {
        try {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
            
            Timber.d("Session cleared from persistent storage")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear session")
        }
    }
    
    suspend fun hasStoredSession(): Boolean {
        return try {
            val preferences = context.dataStore.data.first()
            preferences[sessionIdKey] != null
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for stored session")
            false
        }
    }
    
    data class StoredSession(
        val session: ActiveWorkoutSession,
        val startTime: Long,
        val totalPausedDuration: Long,
        val isRunning: Boolean,
        val pauseStartTime: Long?
    )
}