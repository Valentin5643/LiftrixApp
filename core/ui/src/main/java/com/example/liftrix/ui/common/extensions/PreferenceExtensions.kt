package com.example.liftrix.ui.common.extensions

import com.example.liftrix.domain.model.WeightUnit

fun Map<String, Any?>.getWeightUnitFromPreferences(): WeightUnit =
    (this["weightUnit"] as? WeightUnit) ?: WeightUnit.getSystemDefault()

fun Map<String, Any?>.getWeightUnitSymbolFromPreferences(): String =
    getWeightUnitFromPreferences().symbol
