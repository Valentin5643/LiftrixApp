package com.example.liftrix.domain.interactor.profile

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.domain.usecase.ValidationResult
import javax.inject.Inject

class ProfileValidationInteractor @Inject constructor(
    private val validateProfileInputUseCase: ValidateProfileInputUseCase
) {
    fun validateAge(input: String): ValidationResult =
        validateProfileInputUseCase.validateAge(input)

    fun validateWeight(value: String, unit: String): ValidationResult =
        validateProfileInputUseCase.validateWeight(value, unit)

    fun validateEquipmentSelection(equipment: List<Equipment>): ValidationResult =
        validateProfileInputUseCase.validateEquipmentSelection(equipment)

    fun validateOtherEquipment(description: String): ValidationResult =
        validateProfileInputUseCase.validateOtherEquipment(description)

    fun validateGoalSelection(goals: List<FitnessGoal>): ValidationResult =
        validateProfileInputUseCase.validateGoalSelection(goals)

    fun validateGoalPriority(priority: String): ValidationResult =
        validateProfileInputUseCase.validateGoalPriority(priority)

    fun validateGoalPriorityMapping(
        goals: List<FitnessGoal>,
        priorities: Map<FitnessGoal, Int>
    ): ValidationResult =
        validateProfileInputUseCase.validateGoalPriorityMapping(goals, priorities)

    fun validateProfileCompletion(
        age: Int?,
        weight: Weight?,
        equipment: List<Equipment>,
        goals: List<FitnessGoal>
    ): ValidationResult =
        validateProfileInputUseCase.validateProfileCompletion(age, weight, equipment, goals)
}
