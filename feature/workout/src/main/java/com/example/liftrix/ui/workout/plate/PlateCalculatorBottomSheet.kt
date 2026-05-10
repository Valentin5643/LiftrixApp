package com.example.liftrix.ui.workout.plate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.feature.workout.ui.AccessibilityUtils.ensureMinimumTouchTarget
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorBottomSheet(
    isVisible: Boolean,
    weightUnit: WeightUnit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val unitSymbol = weightUnit.symbol
    val defaultPlates = remember(weightUnit) { defaultPlateDenominations(weightUnit) }
    var targetTotalText by rememberSaveable(weightUnit) { mutableStateOf(defaultTargetTotal(weightUnit)) }
    var barWeightText by rememberSaveable(weightUnit) { mutableStateOf(defaultBarWeight(weightUnit)) }
    var selectedDenominationsText by rememberSaveable(weightUnit) {
        mutableStateOf(defaultPlates.joinToString(","))
    }
    var customDenominationText by rememberSaveable { mutableStateOf("") }

    val selectedDenominations = remember(selectedDenominationsText) {
        selectedDenominationsText
            .split(",")
            .mapNotNull { it.toDoubleOrNull() }
            .filter { it > 0.0 }
            .distinct()
            .sortedDescending()
    }
    val targetTotal = targetTotalText.toDoubleOrNull()
    val barWeight = barWeightText.toDoubleOrNull()
    val result = remember(targetTotal, barWeight, selectedDenominations, unitSymbol) {
        calculatePlateLoading(
            targetTotal = targetTotal ?: Double.NaN,
            barWeight = barWeight ?: Double.NaN,
            denominations = selectedDenominations,
            unitSymbol = unitSymbol
        )
    }

    fun updateSelectedDenominations(values: List<Double>) {
        selectedDenominationsText = values
            .filter { it > 0.0 }
            .distinct()
            .sortedDescending()
            .joinToString(",")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics {
            contentDescription = "Plate calculator"
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Plate Calculator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            OutlinedTextField(
                value = targetTotalText,
                onValueChange = { targetTotalText = it.filterPlateInput() },
                label = { Text("Target total ($unitSymbol)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = barWeightText,
                onValueChange = { barWeightText = it.filterPlateInput() },
                label = { Text("Bar weight ($unitSymbol)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            ChipSectionLabel("Bar")
            ChipRows(
                values = defaultBarWeights(weightUnit),
                content = { value ->
                    AssistChip(
                        onClick = { barWeightText = formatPlateValue(value) },
                        label = { Text("${formatPlateValue(value)} $unitSymbol") },
                        modifier = Modifier.ensureMinimumTouchTarget()
                    )
                }
            )

            ChipSectionLabel("Plates")
            ChipRows(
                values = (defaultPlates + selectedDenominations).distinct().sortedDescending(),
                content = { value ->
                    val selected = selectedDenominations.any { abs(it - value) < 0.005 }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            updateSelectedDenominations(
                                if (selected) {
                                    selectedDenominations.filterNot { abs(it - value) < 0.005 }
                                } else {
                                    selectedDenominations + value
                                }
                            )
                        },
                        label = { Text("${formatPlateValue(value)} $unitSymbol") },
                        modifier = Modifier.ensureMinimumTouchTarget()
                    )
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customDenominationText,
                    onValueChange = { customDenominationText = it.filterPlateInput() },
                    label = { Text("Custom plate") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        customDenominationText.toDoubleOrNull()
                            ?.takeIf { it > 0.0 }
                            ?.let { customValue ->
                                updateSelectedDenominations(selectedDenominations + customValue)
                                customDenominationText = ""
                            }
                    },
                    modifier = Modifier.ensureMinimumTouchTarget()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add custom plate"
                    )
                }
            }

            if (selectedDenominations.any { it !in defaultPlates }) {
                ChipRows(
                    values = selectedDenominations.filter { it !in defaultPlates },
                    content = { value ->
                        InputChip(
                            selected = true,
                            onClick = {
                                updateSelectedDenominations(
                                    selectedDenominations.filterNot { abs(it - value) < 0.005 }
                                )
                            },
                            label = { Text("${formatPlateValue(value)} $unitSymbol") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove custom plate",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.ensureMinimumTouchTarget()
                        )
                    }
                )
            }

            PlateResultCard(result = result)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChipSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ChipRows(
    values: List<Double>,
    content: @Composable (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { rowValues ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowValues.forEach { value ->
                    content(value)
                }
            }
        }
    }
}

@Composable
private fun PlateResultCard(result: PlateLoadingResult) {
    val isWarning = result.validationMessage != null || !result.exact
    val containerColor = if (isWarning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isWarning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = result.validationMessage ?: perSideText(result),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (result.validationMessage == null) {
                Text(
                    text = "Total: ${formatPlateValue(result.achievedTotal)} ${result.unitSymbol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                if (!result.exact) {
                    Text(
                        text = closestWarningText(result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

private fun perSideText(result: PlateLoadingResult): String {
    if (result.perSidePlates.isEmpty()) return "Per side: no plates"
    return result.perSidePlates.joinToString(
        prefix = "Per side: ",
        separator = " + "
    ) { "${formatPlateValue(it)} ${result.unitSymbol}" }
}

private fun closestWarningText(result: PlateLoadingResult): String {
    val direction = if (result.isOverTarget) "over" else "under"
    return "Exact target unavailable. Closest is ${formatPlateValue(result.achievedTotal)} ${result.unitSymbol}, " +
            "${formatPlateValue(abs(result.delta))} ${result.unitSymbol} $direction target."
}

private fun defaultPlateDenominations(unit: WeightUnit): List<Double> {
    return when (unit) {
        WeightUnit.KILOGRAMS -> listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        WeightUnit.POUNDS -> listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)
    }
}

private fun defaultBarWeights(unit: WeightUnit): List<Double> {
    return when (unit) {
        WeightUnit.KILOGRAMS -> listOf(20.0, 15.0, 10.0)
        WeightUnit.POUNDS -> listOf(45.0, 35.0, 15.0)
    }
}

private fun defaultBarWeight(unit: WeightUnit): String {
    return when (unit) {
        WeightUnit.KILOGRAMS -> "20"
        WeightUnit.POUNDS -> "45"
    }
}

private fun defaultTargetTotal(unit: WeightUnit): String {
    return when (unit) {
        WeightUnit.KILOGRAMS -> "60"
        WeightUnit.POUNDS -> "135"
    }
}

private fun String.filterPlateInput(): String {
    val normalized = replace(',', '.')
    val firstDot = normalized.indexOf('.')
    return normalized.filterIndexed { index, char ->
        char.isDigit() || (char == '.' && index == firstDot)
    }.take(8)
}
