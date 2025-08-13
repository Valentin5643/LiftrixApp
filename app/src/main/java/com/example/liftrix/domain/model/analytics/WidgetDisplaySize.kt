package com.example.liftrix.domain.model.analytics

/**
 * Widget display sizes for UI customization
 */
enum class WidgetDisplaySize(val displayName: String, val heightMultiplier: Float) {
    COMPACT("Compact", 0.8f),
    STANDARD("Standard", 1.0f),
    EXPANDED("Expanded", 1.4f)
}