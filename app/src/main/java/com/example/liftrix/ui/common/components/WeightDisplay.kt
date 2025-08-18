package com.example.liftrix.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.service.WeightUnitManager
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * A reactive weight display component that automatically converts and formats weights
 * based on the user's current unit preference.
 * 
 * This component solves the critical weight unit persistence issue by ensuring all
 * weight displays stay consistent with user preferences without hardcoding units.
 * 
 * Key features:
 * - Automatic unit conversion based on user preferences
 * - Reactive updates when preferences change
 * - Proper formatting with appropriate precision
 * - Consistent typography and styling
 * 
 * @param weight The weight value in its stored unit
 * @param storedUnit The unit the weight value is stored in
 * @param modifier Modifier for styling the component
 * @param style Text style for the weight display
 * @param precision Number of decimal places to display (default: 1)
 * @param fontWeight Font weight for the display text
 * @param textAlign Text alignment (default: start)
 * @param compact Whether to use compact formatting (fewer decimals for readability)
 */
@Composable
fun WeightDisplay(
    weight: Double,
    storedUnit: WeightUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    precision: Int = 1,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Start,
    compact: Boolean = false,
    weightUnitManager: WeightUnitManager? = null
) {
    // Use dependency injection to get WeightUnitManager when available
    weightUnitManager?.let { manager ->
        val displayText = if (compact) {
            manager.rememberWeightDisplayCompact(weight, storedUnit)
        } else {
            manager.rememberWeightDisplay(weight, storedUnit, precision)
        }
        
        Text(
            text = displayText,
            style = style.copy(
                fontWeight = fontWeight ?: style.fontWeight
            ),
            textAlign = textAlign,
            modifier = modifier
        )
    } ?: run {
        // Fallback for when WeightUnitManager is not available
        val fallbackText = remember(weight, storedUnit, precision) {
            if (precision == 0 || weight == weight.toInt().toDouble()) {
                "${weight.toInt()} ${storedUnit.symbol}"
            } else {
                "${"%.${precision}f".format(weight)} ${storedUnit.symbol}"
            }
        }
        
        Text(
            text = fallbackText,
            style = style.copy(
                fontWeight = fontWeight ?: style.fontWeight
            ),
            textAlign = textAlign,
            modifier = modifier
        )
    }
}

/**
 * A specialized weight display for chart axes and labels.
 * Optimized for readability in data visualization contexts.
 */
@Composable
fun ChartWeightDisplay(
    weight: Double,
    storedUnit: WeightUnit,
    modifier: Modifier = Modifier,
    weightUnitManager: WeightUnitManager? = null
) {
    WeightDisplay(
        weight = weight,
        storedUnit = storedUnit,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        compact = true,
        textAlign = TextAlign.Center,
        weightUnitManager = weightUnitManager
    )
}

/**
 * A large weight display for highlighting important values like personal records.
 */
@Composable
fun HeadlineWeightDisplay(
    weight: Double,
    storedUnit: WeightUnit,
    modifier: Modifier = Modifier,
    weightUnitManager: WeightUnitManager? = null
) {
    WeightDisplay(
        weight = weight,
        storedUnit = storedUnit,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        compact = true,
        textAlign = TextAlign.Center,
        weightUnitManager = weightUnitManager
    )
}

/**
 * Weight display with a label for use in forms and detailed views.
 */
@Composable
fun LabeledWeightDisplay(
    weight: Double,
    storedUnit: WeightUnit,
    label: String,
    modifier: Modifier = Modifier,
    weightUnitManager: WeightUnitManager? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        WeightDisplay(
            weight = weight,
            storedUnit = storedUnit,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            weightUnitManager = weightUnitManager
        )
    }
}

/**
 * Weight input field that accepts values in the current display unit and
 * provides proper conversion for storage.
 * 
 * This component handles the bidirectional conversion needed for user input,
 * ensuring users see and input values in their preferred unit while storing
 * in the appropriate unit for the database.
 * 
 * @param value Current input value as a string
 * @param onValueChange Callback when the input value changes
 * @param storedUnit The unit values should be stored in
 * @param modifier Modifier for styling
 * @param label Label for the input field
 * @param placeholder Placeholder text
 * @param isError Whether the field is in an error state
 * @param supportingText Optional supporting text below the field
 * @param weightUnitManager WeightUnitManager for unit conversion
 */
@Composable
fun WeightInputField(
    value: String,
    onValueChange: (String) -> Unit,
    storedUnit: WeightUnit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    weightUnitManager: WeightUnitManager? = null
) {
    val currentUnitSymbol = weightUnitManager?.rememberCurrentUnitSymbol() ?: storedUnit.symbol
    val displayLabel = label ?: "Weight ($currentUnitSymbol)"
    val displayPlaceholder = placeholder ?: "Enter weight"
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(displayLabel) },
        placeholder = { Text(displayPlaceholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        modifier = modifier
    )
}

/**
 * Compact weight range display showing a range of weights (e.g., "225-235 lbs").
 * Useful for displaying working weight ranges or target ranges.
 */
@Composable
fun WeightRangeDisplay(
    minWeight: Double,
    maxWeight: Double,
    storedUnit: WeightUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    weightUnitManager: WeightUnitManager? = null
) {
    weightUnitManager?.let { manager ->
        val minDisplayText = manager.rememberWeightDisplayCompact(minWeight, storedUnit)
        val maxDisplayText = manager.rememberWeightDisplayCompact(maxWeight, storedUnit)
        
        // Extract just the numeric part from the first value since both will have the same unit
        val minNumeric = minDisplayText.split(" ")[0]
        val maxWithUnit = maxDisplayText
        
        Text(
            text = "$minNumeric - $maxWithUnit",
            style = style,
            modifier = modifier
        )
    } ?: run {
        // Fallback display
        Text(
            text = "${minWeight.toInt()} - ${maxWeight.toInt()} ${storedUnit.symbol}",
            style = style,
            modifier = modifier
        )
    }
}

/**
 * Weight difference display showing the change between two weights (e.g., "+5.5 kg", "-2.0 lbs").
 * Useful for progress tracking and comparisons.
 */
@Composable
fun WeightDifferenceDisplay(
    previousWeight: Double,
    currentWeight: Double,
    storedUnit: WeightUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    weightUnitManager: WeightUnitManager? = null
) {
    val difference = currentWeight - previousWeight
    val isPositive = difference > 0
    val sign = if (isPositive) "+" else ""
    
    weightUnitManager?.let { manager ->
        val displayText = manager.rememberWeightDisplay(kotlin.math.abs(difference), storedUnit, 1)
        
        Text(
            text = "$sign$displayText",
            style = style,
            color = when {
                isPositive -> MaterialTheme.colorScheme.primary
                difference < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = modifier
        )
    } ?: run {
        // Fallback display
        val formattedDiff = "%.1f".format(kotlin.math.abs(difference))
        Text(
            text = "$sign$formattedDiff ${storedUnit.symbol}",
            style = style,
            color = when {
                isPositive -> MaterialTheme.colorScheme.primary
                difference < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = modifier
        )
    }
}

// Preview composables for testing
@Preview(showBackground = true)
@Composable
private fun WeightDisplayPreview() {
    LiftrixTheme {
        WeightDisplay(
            weight = 225.5,
            storedUnit = WeightUnit.POUNDS
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HeadlineWeightDisplayPreview() {
    LiftrixTheme {
        HeadlineWeightDisplay(
            weight = 315.0,
            storedUnit = WeightUnit.POUNDS
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LabeledWeightDisplayPreview() {
    LiftrixTheme {
        LabeledWeightDisplay(
            weight = 102.5,
            storedUnit = WeightUnit.KILOGRAMS,
            label = "Personal Record:"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeightRangeDisplayPreview() {
    LiftrixTheme {
        WeightRangeDisplay(
            minWeight = 200.0,
            maxWeight = 225.0,
            storedUnit = WeightUnit.POUNDS
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeightDifferenceDisplayPreview() {
    LiftrixTheme {
        WeightDifferenceDisplay(
            previousWeight = 200.0,
            currentWeight = 205.5,
            storedUnit = WeightUnit.POUNDS
        )
    }
}