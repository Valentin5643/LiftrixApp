package com.example.liftrix.domain.model.analytics

/**
 * Dashboard configuration settings for UI behavior and chart preferences
 * 
 * Represents configurable dashboard behavior including chart preferences,
 * layout modes, and display options. These settings work alongside the
 * DashboardConfiguration sealed class to provide complete customization.
 * 
 * Features:
 * - Modern chart component enable/disable
 * - Layout mode preferences (2-column mobile optimization)
 * - Animation and transition settings
 * - Performance optimization flags
 */
data class DashboardSettings(
    val useModernCharts: Boolean = true,
    val enableSmoothTransitions: Boolean = true,
    val optimizeForMobile: Boolean = true,
    val enableHapticFeedback: Boolean = true,
    val animationDuration: Int = 300,
    val autoRefreshEnabled: Boolean = true,
    val showWidgetHeaders: Boolean = true
) {
    
    /**
     * Checks if modern chart components should be used
     */
    fun shouldUseModernCharts(): Boolean = useModernCharts
    
    /**
     * Checks if 2-column mobile layout should be enabled
     */
    fun shouldOptimizeForMobile(): Boolean = optimizeForMobile
    
    /**
     * Gets the effective animation duration for chart transitions
     */
    fun getEffectiveAnimationDuration(): Int = if (enableSmoothTransitions) animationDuration else 0
    
    companion object {
        /**
         * Default settings with modern features enabled
         */
        fun createDefault(): DashboardSettings = DashboardSettings()
        
        /**
         * Performance-optimized settings for lower-end devices
         */
        fun createPerformanceOptimized(): DashboardSettings = DashboardSettings(
            useModernCharts = true, // Keep modern charts but optimize performance
            enableSmoothTransitions = false,
            optimizeForMobile = true,
            enableHapticFeedback = false,
            animationDuration = 150,
            autoRefreshEnabled = false
        )
        
        /**
         * Legacy settings for compatibility mode
         */
        fun createLegacyMode(): DashboardSettings = DashboardSettings(
            useModernCharts = false,
            enableSmoothTransitions = false,
            optimizeForMobile = false,
            enableHapticFeedback = false,
            animationDuration = 0,
            autoRefreshEnabled = true
        )
    }
}