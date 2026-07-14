package com.example.liftrix.ui.chat.workoutbuilder.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.ai.WorkoutGenerationStage
import com.example.liftrix.ui.animations.WorkoutCardSkeleton

@Composable
fun WorkoutGenerationLoading(stage: WorkoutGenerationStage?) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Creating your plan", style = MaterialTheme.typography.headlineSmall)
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "generation-stage"
        ) { value ->
            Text(value.label(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        repeat(3) { WorkoutCardSkeleton(showActions = false) }
        Text("Tip: consistency beats perfect programming.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun WorkoutGenerationStage?.label(): String = when (this) {
    WorkoutGenerationStage.ANALYZING_GOALS -> "Analyzing goals"
    WorkoutGenerationStage.CHOOSING_EXERCISES -> "Choosing exercises"
    WorkoutGenerationStage.BUILDING_WEEKLY_SCHEDULE -> "Building the weekly schedule"
    WorkoutGenerationStage.BALANCING_MUSCLE_GROUPS -> "Balancing muscle groups"
    WorkoutGenerationStage.REPAIRING_PLAN -> "Repairing the plan"
    WorkoutGenerationStage.FINALIZING_PLAN -> "Finalizing the plan"
    null -> "Preparing"
}
