package com.example.liftrix.ui.onboarding.model

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot
import com.example.liftrix.domain.model.onboarding.WeightUnit
import java.time.LocalDateTime

/**
 * Mutable data class for onboarding form state management.
 * Separate from the immutable UserProfile domain model to handle validation and UI state.
 */
data class UserProfileData(
    val userId: String,
    
    // Profile information
    val displayName: String = "",
    val bio: String = "",
    
    // Age input state
    val ageInput: String = "",
    val ageError: String? = null,
    
    // Weight input state
    val weightInput: String = "",
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val weightError: String? = null,
    val preferNotToSayWeight: Boolean = false,
    
    // Equipment selection state
    val selectedEquipment: Set<Equipment> = emptySet(),
    val otherEquipmentInput: String = "",
    val otherEquipmentError: String? = null,
    
    // Goals selection state
    val selectedGoals: Set<FitnessGoal> = emptySet(),
    val goalsPriority: Map<FitnessGoal, Int> = emptyMap(),
    val goalsError: String? = null
) {
    
    /**
     * Validate age input and return parsed value if valid.
     */
    fun getValidatedAge(): Int? {
        if (ageInput.isBlank()) return null
        return try {
            val age = ageInput.trim().toInt()
            if (age in UserProfile.MIN_AGE..UserProfile.MAX_AGE) age else null
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * Validate weight input and return Weight object if valid.
     */
    fun getValidatedWeight(): Weight? {
        if (preferNotToSayWeight || weightInput.isBlank()) return null
        return try {
            val weightValue = weightInput.trim().toDouble()
            when (weightUnit) {
                WeightUnit.KILOGRAMS -> Weight.fromKilograms(weightValue)
                WeightUnit.POUNDS -> Weight.fromPounds(weightValue)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get validated other equipment description.
     */
    fun getValidatedOtherEquipment(): String? {
        val trimmed = otherEquipmentInput.trim()
        return if (trimmed.isNotBlank() && 
                   trimmed.length <= UserProfile.MAX_OTHER_EQUIPMENT_LENGTH) {
            trimmed
        } else {
            null
        }
    }
    
    /**
     * Check if age step is valid for navigation.
     */
    fun isAgeStepValid(): Boolean {
        return getValidatedAge() != null
    }
    
    /**
     * Check if weight step is valid for navigation (always valid since it's optional).
     */
    fun isWeightStepValid(): Boolean {
        return true // Weight is optional
    }
    
    /**
     * Check if equipment step is valid for navigation.
     */
    fun isEquipmentStepValid(): Boolean {
        return selectedEquipment.isNotEmpty() && 
               (otherEquipmentInput.isBlank() || getValidatedOtherEquipment() != null)
    }
    
    /**
     * Check if goals step is valid for navigation.
     */
    fun isGoalsStepValid(): Boolean {
        return selectedGoals.isNotEmpty()
    }
    
    /**
     * Check if the profile data is complete enough to be saved.
     */
    fun isCompleteForSaving(): Boolean {
        return isAgeStepValid() && 
               isEquipmentStepValid() && 
               isGoalsStepValid()
    }
    
    /**
     * Convert to domain UserProfile model.
     * Only call this after validating the data is complete.
     */
    fun toDomainModel(): UserProfile {
        require(isCompleteForSaving()) { "Profile data is not complete for saving" }
        
        return UserProfile(
            userId = userId,
            displayName = displayName.takeIf { it.isNotBlank() } ?: "",
            bio = bio.takeIf { it.isNotBlank() },
            age = getValidatedAge(),
            weight = getValidatedWeight(),
            availableEquipment = selectedEquipment.toList(),
            otherEquipment = getValidatedOtherEquipment(),
            fitnessGoals = selectedGoals.toList(),
            goalsPriority = goalsPriority.takeIf { it.isNotEmpty() },
            lastActiveAt = LocalDateTime.now(),
            memberSince = LocalDateTime.now(),
            completedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * Calculate weight conversion for display purposes.
     */
    fun getWeightConversion(): String? {
        val weight = getValidatedWeight() ?: return null
        return when (weightUnit) {
            WeightUnit.KILOGRAMS -> {
                val pounds = weight.toPounds()
                "${String.format("%.1f", pounds)} lbs"
            }
            WeightUnit.POUNDS -> {
                val kg = weight.kilograms
                "${String.format("%.1f", kg)} kg"
            }
        }
    }
    
    companion object {
        /**
         * Create initial UserProfileData for a user.
         */
        fun createInitial(userId: String): UserProfileData {
            return UserProfileData(userId = userId)
        }
        
        /**
         * Create UserProfileData from existing UserProfile for editing.
         */
        fun fromDomainModel(userProfile: UserProfile): UserProfileData {
            return UserProfileData(
                userId = userProfile.userId,
                ageInput = userProfile.age?.toString() ?: "",
                weightInput = userProfile.weight?.let { 
                    String.format("%.1f", it.kilograms) 
                } ?: "",
                weightUnit = WeightUnit.KILOGRAMS,
                preferNotToSayWeight = userProfile.weight == null,
                selectedEquipment = userProfile.availableEquipment.toSet(),
                otherEquipmentInput = userProfile.otherEquipment ?: "",
                selectedGoals = userProfile.fitnessGoals.toSet(),
                goalsPriority = userProfile.goalsPriority ?: emptyMap()
            )
        }

        fun fromSnapshot(snapshot: OnboardingDataSnapshot): UserProfileData {
            return UserProfileData(
                userId = snapshot.userId,
                ageInput = snapshot.ageInput,
                weightInput = snapshot.weightInput,
                weightUnit = snapshot.weightUnit,
                preferNotToSayWeight = snapshot.preferNotToSayWeight,
                selectedEquipment = snapshot.selectedEquipment.mapNotNull { name ->
                    runCatching { Equipment.valueOf(name) }.getOrNull()
                }.toSet(),
                otherEquipmentInput = snapshot.otherEquipmentInput,
                selectedGoals = snapshot.selectedGoals.mapNotNull { name ->
                    runCatching { FitnessGoal.valueOf(name) }.getOrNull()
                }.toSet(),
                goalsPriority = snapshot.goalsPriority.mapNotNull { (name, priority) ->
                    runCatching { FitnessGoal.valueOf(name) }.getOrNull()?.let { it to priority }
                }.toMap()
            )
        }
    }
}

fun UserProfileData.toSnapshot(): OnboardingDataSnapshot {
    return OnboardingDataSnapshot(
        userId = userId,
        ageInput = ageInput,
        weightInput = weightInput,
        weightUnit = weightUnit,
        preferNotToSayWeight = preferNotToSayWeight,
        selectedEquipment = selectedEquipment.map { it.name }.toSet(),
        otherEquipmentInput = otherEquipmentInput,
        selectedGoals = selectedGoals.map { it.name }.toSet(),
        goalsPriority = goalsPriority.mapKeys { it.key.name }
    )
}
