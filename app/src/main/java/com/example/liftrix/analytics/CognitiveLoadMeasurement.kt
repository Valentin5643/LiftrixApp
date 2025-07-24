package com.example.liftrix.analytics

import com.example.liftrix.domain.service.AnalyticsService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.pow

/**
 * Cognitive Load Measurement system for quantifying user mental effort and interface complexity.
 * 
 * This system implements scientific cognitive load assessment based on:
 * - Intrinsic Load: Task complexity and information processing requirements
 * - Extraneous Load: Interface friction and usability issues  
 * - Germane Load: Mental effort devoted to learning and schema construction
 * 
 * Key Features:
 * - Multi-dimensional cognitive load calculation
 * - Real-time load assessment during user workflows
 * - Baseline comparison for measuring 40% reduction target
 * - Integration with UX metrics for comprehensive user experience analysis
 * - Scientific validation through established cognitive psychology principles
 */
@Singleton
class CognitiveLoadMeasurement @Inject constructor(
    private val analyticsService: AnalyticsService
) {
    
    companion object {
        // Cognitive Load Theory constants
        private const val WORKING_MEMORY_CAPACITY = 7.0 // Miller's Rule: 7±2 items
        private const val TIME_PRESSURE_FACTOR = 0.001 // Load increase per millisecond
        private const val ERROR_PENALTY_FACTOR = 0.5 // Load increase per error
        private const val CONTEXT_SWITCH_PENALTY = 0.3 // Load increase per navigation
        private const val INTERRUPTION_PENALTY = 0.4 // Load increase per interruption
        
        // Load categorization thresholds (normalized 0-10 scale)
        private const val LOW_LOAD_THRESHOLD = 3.0
        private const val MODERATE_LOAD_THRESHOLD = 6.0
        private const val HIGH_LOAD_THRESHOLD = 8.0
        
        // Cognitive load types
        const val INTRINSIC_LOAD = "intrinsic"
        const val EXTRANEOUS_LOAD = "extraneous" 
        const val GERMANE_LOAD = "germane"
        const val TOTAL_LOAD = "total"
        
        // Analytics events
        private const val EVENT_COGNITIVE_LOAD_CALCULATED = "cognitive_load_calculated"
        private const val EVENT_LOAD_THRESHOLD_EXCEEDED = "cognitive_load_threshold_exceeded"
        private const val EVENT_BASELINE_COMPARISON = "cognitive_load_baseline_comparison"
    }
    
    /**
     * Calculates comprehensive cognitive load for a user workflow.
     * 
     * @param workflowData Complete workflow interaction data
     * @return CognitiveLoadResult with detailed analysis
     */
    fun calculateCognitiveLoad(workflowData: WorkflowData): CognitiveLoadResult {
        Timber.d("Calculating cognitive load for workflow: ${workflowData.workflowId}")
        
        // Calculate individual load components
        val intrinsicLoad = calculateIntrinsicLoad(workflowData)
        val extraneousLoad = calculateExtraneousLoad(workflowData)
        val germaneLoad = calculateGermaneLoad(workflowData)
        
        // Total cognitive load with interaction effects
        val totalLoad = calculateTotalLoad(intrinsicLoad, extraneousLoad, germaneLoad)
        
        // Determine load category and severity
        val loadCategory = categorizeLoad(totalLoad)
        val severity = calculateSeverity(totalLoad)
        
        val result = CognitiveLoadResult(
            workflowId = workflowData.workflowId,
            intrinsicLoad = intrinsicLoad,
            extraneousLoad = extraneousLoad,  
            germaneLoad = germaneLoad,
            totalLoad = totalLoad,
            loadCategory = loadCategory,
            severity = severity,
            recommendations = generateRecommendations(intrinsicLoad, extraneousLoad, germaneLoad),
            calculationTimestamp = System.currentTimeMillis()
        )
        
        // Log cognitive load calculation
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_COGNITIVE_LOAD_CALCULATED,
                parameters = mapOf(
                    "workflow_id" to workflowData.workflowId,
                    "intrinsic_load" to intrinsicLoad,
                    "extraneous_load" to extraneousLoad,
                    "germane_load" to germaneLoad,
                    "total_load" to totalLoad,
                    "load_category" to loadCategory,
                    "severity" to severity
                )
            )
        }
        
        // Check if thresholds exceeded
        if (totalLoad > HIGH_LOAD_THRESHOLD) {
            GlobalScope.launch {
                analyticsService.logEvent(
                    eventName = EVENT_LOAD_THRESHOLD_EXCEEDED,
                    parameters = mapOf(
                        "workflow_id" to workflowData.workflowId,
                        "total_load" to totalLoad,
                        "threshold" to HIGH_LOAD_THRESHOLD,
                        "severity" to severity
                    )
                )
            }
        }
        
        Timber.i("Cognitive load calculated for ${workflowData.workflowId}: Total=$totalLoad ($loadCategory)")
        
        return result
    }
    
    /**
     * Calculates intrinsic cognitive load - the inherent complexity of the task itself.
     * Based on information processing requirements and task structure.
     */
    private fun calculateIntrinsicLoad(workflowData: WorkflowData): Double {
        // Base task complexity
        val taskComplexity = when (workflowData.workflowType) {
            "workout_creation" -> 4.0 // Moderate complexity - planning and decision making
            "active_session" -> 2.5 // Lower complexity - following established routine
            "historical_editing" -> 3.5 // Moderate complexity - reviewing and modifying data
            "navigation" -> 1.5 // Low complexity - simple navigation
            else -> 3.0 // Default moderate complexity
        }
        
        // Information density factor
        val informationElements = workflowData.uniqueElementsEncountered
        val informationLoad = ln(informationElements.toDouble() + 1) * 0.5
        
        // Decision points increase intrinsic load
        val decisionLoad = workflowData.decisionPoints * 0.3
        
        val intrinsicLoad = taskComplexity + informationLoad + decisionLoad
        
        return minOf(intrinsicLoad, 10.0) // Cap at maximum scale
    }
    
    /**
     * Calculates extraneous cognitive load - mental effort wasted on poor interface design.
     * This is the primary target for the 40% reduction goal.
     */
    private fun calculateExtraneousLoad(workflowData: WorkflowData): Double {
        // Navigation friction
        val navigationLoad = workflowData.navigationSteps * CONTEXT_SWITCH_PENALTY
        
        // Error recovery effort
        val errorLoad = workflowData.errorCount * ERROR_PENALTY_FACTOR
        
        // Time pressure from interface delays
        val timePressureLoad = workflowData.totalTime * TIME_PRESSURE_FACTOR
        
        // Interface interruptions and modals
        val interruptionLoad = workflowData.modalInterruptions * INTERRUPTION_PENALTY
        
        // Inconsistent UI patterns increase extraneous load
        val inconsistencyLoad = calculateInconsistencyLoad(workflowData)
        
        // Search and discovery effort
        val searchLoad = workflowData.searchAttempts * 0.2
        
        val extraneousLoad = navigationLoad + errorLoad + timePressureLoad + 
                           interruptionLoad + inconsistencyLoad + searchLoad
        
        return minOf(extraneousLoad, 10.0) // Cap at maximum scale
    }
    
    /**
     * Calculates germane cognitive load - productive mental effort for learning and understanding.
     * This represents valuable cognitive work that improves user competence.
     */
    private fun calculateGermaneLoad(workflowData: WorkflowData): Double {
        // Learning new features or workflows
        val learningLoad = if (workflowData.isFirstTimeUser) 2.0 else 0.5
        
        // Mental model building for complex tasks
        val modelingLoad = workflowData.conceptualElements * 0.25
        
        // Pattern recognition and skill development
        val skillLoad = if (workflowData.involvesSkillDevelopment) 1.5 else 0.0
        
        val germaneLoad = learningLoad + modelingLoad + skillLoad
        
        return minOf(germaneLoad, 10.0) // Cap at maximum scale
    }
    
    /**
     * Calculates total cognitive load with interaction effects between load types.
     */
    private fun calculateTotalLoad(intrinsic: Double, extraneous: Double, germane: Double): Double {
        // Basic additive model
        val additiveLoad = intrinsic + extraneous + germane
        
        // Interaction effects - extraneous load multiplies the impact of intrinsic load
        val interactionEffect = (intrinsic * extraneous) * 0.1
        
        // Working memory capacity constraint
        val capacityConstrainedLoad = minOf(additiveLoad + interactionEffect, WORKING_MEMORY_CAPACITY * 1.5)
        
        // Normalize to 0-10 scale
        return (capacityConstrainedLoad / (WORKING_MEMORY_CAPACITY * 1.5)) * 10.0
    }
    
    /**
     * Calculates inconsistency load based on UI pattern deviations.
     */
    private fun calculateInconsistencyLoad(workflowData: WorkflowData): Double {
        val buttonInconsistencies = workflowData.buttonStyleVariations * 0.1
        val layoutInconsistencies = workflowData.layoutPatternChanges * 0.15
        val terminologyInconsistencies = workflowData.terminologyVariations * 0.2
        
        return buttonInconsistencies + layoutInconsistencies + terminologyInconsistencies
    }
    
    /**
     * Categorizes cognitive load into human-readable levels.
     */
    private fun categorizeLoad(totalLoad: Double): String {
        return when {
            totalLoad < LOW_LOAD_THRESHOLD -> "low"
            totalLoad < MODERATE_LOAD_THRESHOLD -> "moderate"
            totalLoad < HIGH_LOAD_THRESHOLD -> "high"
            else -> "extreme"
        }
    }
    
    /**
     * Calculates severity score for prioritizing optimization efforts.
     */
    private fun calculateSeverity(totalLoad: Double): Double {
        return (totalLoad / 10.0).pow(1.5) * 100.0 // Non-linear scaling emphasizes high loads
    }
    
    /**
     * Generates optimization recommendations based on load analysis.
     */
    private fun generateRecommendations(intrinsic: Double, extraneous: Double, germane: Double): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (extraneous > 4.0) {
            recommendations.add("High extraneous load detected - prioritize UI simplification")
            recommendations.add("Reduce navigation complexity and modal interruptions") 
            recommendations.add("Improve error prevention and recovery flows")
        }
        
        if (intrinsic > 6.0) {
            recommendations.add("High intrinsic load - consider task decomposition")
            recommendations.add("Provide progressive disclosure of complex information")
        }
        
        if (germane < 1.0 && intrinsic > 3.0) {
            recommendations.add("Low germane load - add helpful learning scaffolding")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Cognitive load within acceptable range - maintain current patterns")
        }
        
        return recommendations
    }
    
    /**
     * Compares current cognitive load against baseline for PRD validation.
     * 
     * @param currentLoad Current cognitive load measurement
     * @param baselineLoad Historical baseline load
     * @return Comparison result with improvement percentage
     */
    fun compareToBaseline(currentLoad: CognitiveLoadResult, baselineLoad: CognitiveLoadResult): BaselineComparison {
        val totalImprovement = ((baselineLoad.totalLoad - currentLoad.totalLoad) / baselineLoad.totalLoad) * 100.0
        val extraneousImprovement = ((baselineLoad.extraneousLoad - currentLoad.extraneousLoad) / baselineLoad.extraneousLoad) * 100.0
        
        val meetsTarget = totalImprovement >= 40.0 // PRD target: 40% cognitive load reduction
        
        val comparison = BaselineComparison(
            currentLoad = currentLoad.totalLoad,
            baselineLoad = baselineLoad.totalLoad,
            totalImprovement = totalImprovement,
            extraneousImprovement = extraneousImprovement,
            meetsTarget = meetsTarget,
            targetImprovement = 40.0
        )
        
        // Log baseline comparison
        GlobalScope.launch {
            analyticsService.logEvent(
                eventName = EVENT_BASELINE_COMPARISON,
                parameters = mapOf(
                    "workflow_id" to currentLoad.workflowId,
                    "current_load" to currentLoad.totalLoad,
                    "baseline_load" to baselineLoad.totalLoad,
                    "total_improvement" to totalImprovement,
                    "extraneous_improvement" to extraneousImprovement,
                    "meets_target" to meetsTarget,
                    "target_percentage" to 40.0
                )
            )
        }
        
        return comparison
    }
}

/**
 * Data class containing all workflow interaction data needed for cognitive load calculation.
 */
data class WorkflowData(
    val workflowId: String,
    val workflowType: String,
    val totalTime: Long,
    val errorCount: Int,
    val navigationSteps: Int,
    val modalInterruptions: Int,
    val searchAttempts: Int,
    val decisionPoints: Int,
    val uniqueElementsEncountered: Int,
    val conceptualElements: Int,
    val isFirstTimeUser: Boolean,
    val involvesSkillDevelopment: Boolean,
    val buttonStyleVariations: Int,
    val layoutPatternChanges: Int,
    val terminologyVariations: Int
)

/**
 * Result of cognitive load calculation with detailed analysis.
 */
data class CognitiveLoadResult(
    val workflowId: String,
    val intrinsicLoad: Double,
    val extraneousLoad: Double,
    val germaneLoad: Double,
    val totalLoad: Double,
    val loadCategory: String,
    val severity: Double,
    val recommendations: List<String>,
    val calculationTimestamp: Long
)

/**
 * Baseline comparison result for measuring PRD success.
 */
data class BaselineComparison(
    val currentLoad: Double,
    val baselineLoad: Double,
    val totalImprovement: Double,
    val extraneousImprovement: Double,
    val meetsTarget: Boolean,
    val targetImprovement: Double
) {
    /**
     * Human-readable improvement description.
     */
    val improvementDescription: String
        get() = when {
            totalImprovement >= 40.0 -> "Excellent: Exceeds 40% reduction target"
            totalImprovement >= 20.0 -> "Good: Significant improvement, approaching target"
            totalImprovement >= 0.0 -> "Fair: Some improvement, needs optimization"
            else -> "Poor: Cognitive load increased, requires investigation"
        }
}