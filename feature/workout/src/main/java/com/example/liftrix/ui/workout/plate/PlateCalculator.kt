package com.example.liftrix.ui.workout.plate

import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

private const val CENTI_UNITS = 100
private const val MAX_DISPLAY_WEIGHT = 10_000.0
private const val EXACT_TOLERANCE = 0.005

data class PlateLoadingResult(
    val targetTotal: Double,
    val barWeight: Double,
    val achievedTotal: Double,
    val delta: Double,
    val exact: Boolean,
    val perSidePlates: List<Double>,
    val availableDenominations: List<Double>,
    val unitSymbol: String,
    val validationMessage: String? = null
) {
    val isValid: Boolean = validationMessage == null
    val isUnderTarget: Boolean = delta < 0
    val isOverTarget: Boolean = delta > 0
}

fun calculatePlateLoading(
    targetTotal: Double,
    barWeight: Double,
    denominations: Collection<Double>,
    unitSymbol: String
): PlateLoadingResult {
    val sanitizedDenominations = denominations
        .filter { it > 0.0 && it.isFinite() }
        .map { it.toCentiUnits().toDisplayUnits() }
        .distinct()
        .sortedDescending()

    val sanitizedTarget = if (targetTotal.isFinite()) targetTotal.coerceIn(0.0, MAX_DISPLAY_WEIGHT) else 0.0
    val sanitizedBar = if (barWeight.isFinite()) barWeight.coerceIn(0.0, MAX_DISPLAY_WEIGHT) else 0.0

    fun invalid(message: String) = PlateLoadingResult(
        targetTotal = sanitizedTarget,
        barWeight = sanitizedBar,
        achievedTotal = sanitizedBar,
        delta = sanitizedBar - sanitizedTarget,
        exact = false,
        perSidePlates = emptyList(),
        availableDenominations = sanitizedDenominations,
        unitSymbol = unitSymbol,
        validationMessage = message
    )

    if (!targetTotal.isFinite() || !barWeight.isFinite()) {
        return invalid("Enter a valid target and bar weight.")
    }

    if (sanitizedTarget < sanitizedBar) {
        return invalid("Target weight must be at least the bar weight.")
    }

    val perSideTarget = ((sanitizedTarget - sanitizedBar) / 2.0).toCentiUnits()
    if (perSideTarget == 0) {
        return PlateLoadingResult(
            targetTotal = sanitizedTarget,
            barWeight = sanitizedBar,
            achievedTotal = sanitizedBar,
            delta = 0.0,
            exact = true,
            perSidePlates = emptyList(),
            availableDenominations = sanitizedDenominations,
            unitSymbol = unitSymbol
        )
    }

    if (sanitizedDenominations.isEmpty()) {
        return invalid("Select at least one plate denomination.")
    }

    val denominationCentiUnits = sanitizedDenominations.map { it.toCentiUnits() }
    val smallestPlate = denominationCentiUnits.minOrNull() ?: return invalid("Select at least one plate denomination.")
    val searchLimit = perSideTarget + smallestPlate
    val reachable = BooleanArray(searchLimit + 1)
    val previousSum = IntArray(searchLimit + 1) { -1 }
    val previousPlate = IntArray(searchLimit + 1) { -1 }

    reachable[0] = true
    for (sum in 0..searchLimit) {
        if (!reachable[sum]) continue
        for (plate in denominationCentiUnits) {
            val next = sum + plate
            if (next <= searchLimit && !reachable[next]) {
                reachable[next] = true
                previousSum[next] = sum
                previousPlate[next] = plate
            }
        }
    }

    var bestSum = 0
    var bestDelta = Int.MAX_VALUE
    for (sum in 0..searchLimit) {
        if (!reachable[sum]) continue
        val delta = abs(sum - perSideTarget)
        if (delta < bestDelta || (delta == bestDelta && sum < bestSum)) {
            bestDelta = delta
            bestSum = sum
        }
    }

    val perSidePlates = buildList {
        var current = bestSum
        while (current > 0) {
            val plate = previousPlate[current]
            if (plate <= 0) break
            add(plate.toDisplayUnits())
            current = previousSum[current]
        }
    }.sortedDescending()

    val achievedTotal = sanitizedBar + 2.0 * bestSum.toDisplayUnits()
    val delta = achievedTotal - sanitizedTarget

    return PlateLoadingResult(
        targetTotal = sanitizedTarget,
        barWeight = sanitizedBar,
        achievedTotal = achievedTotal,
        delta = delta,
        exact = abs(delta) <= EXACT_TOLERANCE,
        perSidePlates = perSidePlates,
        availableDenominations = sanitizedDenominations,
        unitSymbol = unitSymbol
    )
}

fun formatPlateValue(value: Double): String {
    return when {
        value == value.toInt().toDouble() -> value.toInt().toString()
        (value * 10).roundToInt().toDouble() == value * 10 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

private fun Double.toCentiUnits(): Int = (this * CENTI_UNITS).roundToInt()

private fun Int.toDisplayUnits(): Double = this.toDouble() / CENTI_UNITS
