package com.example.liftrix.feature.workout.ui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit

object AccessibilityUtils {
    private val MinimumTouchTargetSize = 48.dp

    fun Modifier.ensureMinimumTouchTarget(minSize: Dp = MinimumTouchTargetSize): Modifier =
        sizeIn(minWidth = minSize, minHeight = minSize)

    fun Modifier.accessibilitySemantics(
        description: String,
        role: Role? = null,
        stateDescription: String? = null,
        isHeading: Boolean = false,
        traversalIndex: Float? = null,
        testTag: String? = null,
        liveRegion: LiveRegionMode? = null,
        isSelected: Boolean = false,
        isEnabled: Boolean = true
    ): Modifier = semantics {
        contentDescription = description
        role?.let { this.role = it }
        stateDescription?.let { this.stateDescription = it }
        if (isHeading) heading()
        traversalIndex?.let { this.traversalIndex = it }
        testTag?.let { this.testTag = it }
        liveRegion?.let { this.liveRegion = it }
        if (isSelected) selected = true
        if (!isEnabled) disabled()
    }
}

@Composable
fun WeightDisplay(
    weight: Weight,
    unit: WeightUnit = WeightUnit.KILOGRAMS,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${weight.value} ${unit.displayName}",
        modifier = modifier
    )
}

@Composable
fun rememberWeightUnitManager(): WorkoutWeightUnitManager = WorkoutWeightUnitManager()

class WorkoutWeightUnitManager {
    fun convertForDisplay(value: Double, storedUnit: WeightUnit): Double =
        storedUnit.convertFromKilograms(value)

    fun formatWeightCompact(value: Double, storedUnit: WeightUnit): String =
        storedUnit.formatWeight(value, precision = 1)

    fun getCurrentUnitSymbol(): String = WeightUnit.KILOGRAMS.symbol
}

@Composable
fun rememberFormattedWeight(
    weight: Weight,
    targetUnit: WeightUnit = WeightUnit.KILOGRAMS,
    showUnit: Boolean = true,
    precision: Int = 1
): String {
    val formatted = "%.${precision}f".format(weight.value)
    return if (showUnit) "$formatted ${targetUnit.displayName}" else formatted
}
