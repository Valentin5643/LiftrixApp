package com.example.liftrix.domain.model.onboarding

data class OnboardingDataSnapshot(
    val userId: String,
    val ageInput: String = "",
    val weightInput: String = "",
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val preferNotToSayWeight: Boolean = false,
    val selectedEquipment: Set<String> = emptySet(),
    val otherEquipmentInput: String = "",
    val selectedGoals: Set<String> = emptySet(),
    val goalsPriority: Map<String, Int> = emptyMap()
)

enum class WeightUnit(val symbol: String, val displayName: String) {
    KILOGRAMS("kg", "Kilograms"),
    POUNDS("lbs", "Pounds")
}
