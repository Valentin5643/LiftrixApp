package com.example.liftrix.core.formatting

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import javax.inject.Inject

class WeightFormatter @Inject constructor() {
    fun formatWeight(weight: Weight, unit: WeightUnit, precision: Int = 1): String =
        weight.format(unit, precision)

    fun formatWeightFromKg(kilograms: Double, unit: WeightUnit, precision: Int = 1): String =
        unit.formatWeight(kilograms, precision)

    fun getWeightValue(weight: Weight, unit: WeightUnit): Double =
        weight.getValue(unit)
}

