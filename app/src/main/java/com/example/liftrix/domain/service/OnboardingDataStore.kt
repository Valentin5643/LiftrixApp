package com.example.liftrix.domain.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.onboarding.WeightUnit
import com.example.liftrix.ui.onboarding.model.UserProfileData
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Temporary storage service for onboarding data that survives authentication transitions.
 * 
 * This service addresses the critical issue where onboarding data is lost when a guest user
 * signs in with Google, causing the profile to be associated with the guest user ID but then
 * lost when the user switches to their authenticated Google user ID.
 * 
 * Design Pattern:
 * 1. During onboarding, store data temporarily in encrypted DataStore
 * 2. After successful authentication, transfer data to authenticated user profile
 * 3. Clear temporary data after successful transfer
 * 
 * Security: Uses encrypted storage to protect sensitive user data during temporary storage.
 */
@Singleton
class OnboardingDataStore @Inject constructor(
    @Named("onboardingDataStore") private val dataStore: DataStore<Preferences>
) {
    
    companion object {
        private val HAS_PENDING_DATA_KEY = booleanPreferencesKey("onboarding_has_pending_data")
        private val STORED_TIMESTAMP_KEY = stringPreferencesKey("onboarding_stored_timestamp")
        
        // User profile fields
        private val AGE_INPUT_KEY = stringPreferencesKey("onboarding_age_input")
        private val WEIGHT_INPUT_KEY = stringPreferencesKey("onboarding_weight_input")
        private val WEIGHT_UNIT_KEY = stringPreferencesKey("onboarding_weight_unit")
        private val PREFER_NOT_SAY_WEIGHT_KEY = booleanPreferencesKey("onboarding_prefer_not_say_weight")
        private val OTHER_EQUIPMENT_KEY = stringPreferencesKey("onboarding_other_equipment")
        
        // Equipment and goals (stored as comma-separated strings)
        private val SELECTED_EQUIPMENT_KEY = stringPreferencesKey("onboarding_selected_equipment")
        private val SELECTED_GOALS_KEY = stringPreferencesKey("onboarding_selected_goals")
        private val GOALS_PRIORITY_KEY = stringPreferencesKey("onboarding_goals_priority")
        
        // Data expiry (24 hours)
        private const val DATA_EXPIRY_MS = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Store onboarding data temporarily during the authentication flow.
     * This data will survive the guest→authenticated user transition.
     */
    suspend fun storeOnboardingData(profileData: UserProfileData): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "STORE_ONBOARDING_DATA_FAILED",
                errorMessage = "Failed to store onboarding data temporarily",
                analyticsContext = mapOf(
                    "user_id" to profileData.userId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Storing onboarding data temporarily for user: ${profileData.userId}")
        
        dataStore.edit { preferences ->
            preferences[HAS_PENDING_DATA_KEY] = true
            preferences[STORED_TIMESTAMP_KEY] = System.currentTimeMillis().toString()
            
            // Store basic profile data
            preferences[AGE_INPUT_KEY] = profileData.ageInput
            preferences[WEIGHT_INPUT_KEY] = profileData.weightInput
            preferences[WEIGHT_UNIT_KEY] = profileData.weightUnit.name
            preferences[PREFER_NOT_SAY_WEIGHT_KEY] = profileData.preferNotToSayWeight
            preferences[OTHER_EQUIPMENT_KEY] = profileData.otherEquipmentInput
            
            // Store equipment selection
            preferences[SELECTED_EQUIPMENT_KEY] = profileData.selectedEquipment
                .joinToString(",") { it.name }
            
            // Store goals selection
            preferences[SELECTED_GOALS_KEY] = profileData.selectedGoals
                .joinToString(",") { it.name }
            
            // Store goals priority as "GOAL1:1,GOAL2:2" format
            preferences[GOALS_PRIORITY_KEY] = profileData.goalsPriority
                .map { "${it.key.name}:${it.value}" }
                .joinToString(",")
        }
        
        Timber.d("Onboarding data stored successfully in temporary storage")
    }
    
    /**
     * Check if there is pending onboarding data that needs to be transferred.
     */
    suspend fun hasPendingOnboardingData(): Boolean {
        return try {
            val preferences = dataStore.data.first()
            val hasPendingData = preferences[HAS_PENDING_DATA_KEY] ?: false
            
            if (hasPendingData) {
                // Check if data has expired
                val storedTimestamp = preferences[STORED_TIMESTAMP_KEY]?.toLongOrNull() ?: 0L
                val isExpired = (System.currentTimeMillis() - storedTimestamp) > DATA_EXPIRY_MS
                
                if (isExpired) {
                    Timber.w("Pending onboarding data has expired, clearing it")
                    clearPendingOnboardingData()
                    false
                } else {
                    true
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for pending onboarding data")
            false
        }
    }
    
    /**
     * Retrieve and reconstruct UserProfileData from temporary storage.
     * This is used after successful authentication to transfer the data.
     */
    suspend fun retrievePendingOnboardingData(newUserId: String): LiftrixResult<UserProfileData?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "RETRIEVE_ONBOARDING_DATA_FAILED",
                errorMessage = "Failed to retrieve pending onboarding data",
                analyticsContext = mapOf(
                    "new_user_id" to newUserId,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val preferences = dataStore.data.first()
        val hasPendingData = preferences[HAS_PENDING_DATA_KEY] ?: false
        
        if (!hasPendingData) {
            Timber.d("No pending onboarding data found")
            return@liftrixCatching null
        }
        
        // Check expiry
        val storedTimestamp = preferences[STORED_TIMESTAMP_KEY]?.toLongOrNull() ?: 0L
        val isExpired = (System.currentTimeMillis() - storedTimestamp) > DATA_EXPIRY_MS
        
        if (isExpired) {
            Timber.w("Pending onboarding data has expired")
            clearPendingOnboardingData()
            return@liftrixCatching null
        }
        
        Timber.d("Retrieving pending onboarding data for new user ID: $newUserId")
        
        // Reconstruct UserProfileData with new user ID
        val profileData = UserProfileData(
            userId = newUserId, // Use the new authenticated user ID
            ageInput = preferences[AGE_INPUT_KEY] ?: "",
            weightInput = preferences[WEIGHT_INPUT_KEY] ?: "",
            weightUnit = preferences[WEIGHT_UNIT_KEY]?.let { 
                try { WeightUnit.valueOf(it) } catch (e: Exception) { WeightUnit.KILOGRAMS }
            } ?: WeightUnit.KILOGRAMS,
            preferNotToSayWeight = preferences[PREFER_NOT_SAY_WEIGHT_KEY] ?: false,
            otherEquipmentInput = preferences[OTHER_EQUIPMENT_KEY] ?: "",
            selectedEquipment = parseEquipmentSelection(preferences[SELECTED_EQUIPMENT_KEY] ?: ""),
            selectedGoals = parseGoalsSelection(preferences[SELECTED_GOALS_KEY] ?: ""),
            goalsPriority = parseGoalsPriority(preferences[GOALS_PRIORITY_KEY] ?: "")
        )
        
        Timber.d("Successfully retrieved onboarding data: age=${profileData.ageInput}, weight=${profileData.weightInput}, equipment=${profileData.selectedEquipment.size}, goals=${profileData.selectedGoals.size}")
        
        profileData
    }
    
    /**
     * Clear pending onboarding data after successful transfer or expiry.
     */
    suspend fun clearPendingOnboardingData(): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CLEAR_ONBOARDING_DATA_FAILED",
                errorMessage = "Failed to clear pending onboarding data",
                analyticsContext = mapOf("error" to throwable.message.orEmpty())
            )
        }
    ) {
        Timber.d("Clearing pending onboarding data")
        
        dataStore.edit { preferences ->
            preferences.remove(HAS_PENDING_DATA_KEY)
            preferences.remove(STORED_TIMESTAMP_KEY)
            preferences.remove(AGE_INPUT_KEY)
            preferences.remove(WEIGHT_INPUT_KEY)
            preferences.remove(WEIGHT_UNIT_KEY)
            preferences.remove(PREFER_NOT_SAY_WEIGHT_KEY)
            preferences.remove(OTHER_EQUIPMENT_KEY)
            preferences.remove(SELECTED_EQUIPMENT_KEY)
            preferences.remove(SELECTED_GOALS_KEY)
            preferences.remove(GOALS_PRIORITY_KEY)
        }
        
        Timber.d("Pending onboarding data cleared successfully")
    }
    
    /**
     * Get summary of pending data for debugging and logging.
     */
    suspend fun getPendingDataSummary(): String {
        return try {
            val preferences = dataStore.data.first()
            val hasPendingData = preferences[HAS_PENDING_DATA_KEY] ?: false
            
            if (!hasPendingData) {
                "No pending onboarding data"
            } else {
                val timestamp = preferences[STORED_TIMESTAMP_KEY]?.toLongOrNull() ?: 0L
                val ageInput = preferences[AGE_INPUT_KEY] ?: ""
                val equipmentCount = parseEquipmentSelection(preferences[SELECTED_EQUIPMENT_KEY] ?: "").size
                val goalsCount = parseGoalsSelection(preferences[SELECTED_GOALS_KEY] ?: "").size
                
                "Pending data: timestamp=$timestamp, age='$ageInput', equipment=$equipmentCount, goals=$goalsCount"
            }
        } catch (e: Exception) {
            "Error reading pending data summary: ${e.message}"
        }
    }
    
    // Private helper methods for parsing stored data
    
    private fun parseEquipmentSelection(equipmentString: String): Set<Equipment> {
        return if (equipmentString.isBlank()) {
            emptySet()
        } else {
            equipmentString.split(",")
                .mapNotNull { 
                    try { Equipment.valueOf(it.trim()) } catch (e: Exception) { null }
                }
                .toSet()
        }
    }
    
    private fun parseGoalsSelection(goalsString: String): Set<FitnessGoal> {
        return if (goalsString.isBlank()) {
            emptySet()
        } else {
            goalsString.split(",")
                .mapNotNull { 
                    try { FitnessGoal.valueOf(it.trim()) } catch (e: Exception) { null }
                }
                .toSet()
        }
    }
    
    private fun parseGoalsPriority(priorityString: String): Map<FitnessGoal, Int> {
        return if (priorityString.isBlank()) {
            emptyMap()
        } else {
            priorityString.split(",")
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        try {
                            val goal = FitnessGoal.valueOf(parts[0].trim())
                            val priority = parts[1].trim().toInt()
                            goal to priority
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }
                .toMap()
        }
    }
}