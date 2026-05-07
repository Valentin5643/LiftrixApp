package com.example.liftrix.core.extensions

import com.example.liftrix.domain.model.Weight

/**
 * Extension functions for Weight class to support analytics calculations
 */

/**
 * Get the weight value in kilograms as Double
 */
fun Weight.toKilograms(): Double = this.kilograms

/**
 * Get display-friendly string representation
 */
fun Weight.toDisplayString(): String = this.displayValue