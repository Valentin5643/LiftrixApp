package com.example.liftrix.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.example.liftrix.domain.service.WeightUnitManager

/**
 * Shared CompositionLocal for weight unit formatting in reusable UI components.
 */
val LocalWeightUnitManager = compositionLocalOf<WeightUnitManager?> { null }
