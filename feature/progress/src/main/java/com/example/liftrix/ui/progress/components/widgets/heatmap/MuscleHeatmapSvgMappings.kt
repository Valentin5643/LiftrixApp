package com.example.liftrix.ui.progress.components.widgets.heatmap

import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.analytics.MuscleHeatmapGender
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import com.example.liftrix.feature.progress.R

internal fun muscleHeatmapResource(
    gender: MuscleHeatmapGender,
    viewSide: MuscleHeatmapViewSide
): Int {
    return when (gender) {
        MuscleHeatmapGender.MALE -> when (viewSide) {
            MuscleHeatmapViewSide.FRONT -> R.raw.muscle_heatmap_male_front
            MuscleHeatmapViewSide.BACK -> R.raw.muscle_heatmap_male_back
        }
        MuscleHeatmapGender.FEMALE -> when (viewSide) {
            MuscleHeatmapViewSide.FRONT -> R.raw.muscle_heatmap_female_front
            MuscleHeatmapViewSide.BACK -> R.raw.muscle_heatmap_female_back
        }
    }
}

internal fun muscleGroupForSvgId(
    pathId: String,
    viewSide: MuscleHeatmapViewSide
): MuscleGroup? {
    val id = pathId.lowercase()
    return when {
        id.contains("pectoral") || id.contains("chest") -> MuscleGroup.CHEST
        id.contains("lower-back") -> MuscleGroup.LOWER_BACK
        id.isLatPathId() -> MuscleGroup.LATS
        id.contains("trapezius") ||
            id.contains("trap") ||
            id.contains("upper-back") ||
            id.contains("mid-back") ||
            id.contains("rhomboid") -> MuscleGroup.UPPER_BACK
        id.contains("deltoid") || id.contains("shoulder") -> MuscleGroup.SHOULDERS
        id.contains("tricep") -> MuscleGroup.TRICEPS
        id.contains("bicep") -> MuscleGroup.BICEPS
        id.contains("upper-arm") -> when (viewSide) {
            MuscleHeatmapViewSide.FRONT -> MuscleGroup.BICEPS
            MuscleHeatmapViewSide.BACK -> MuscleGroup.TRICEPS
        }
        id.contains("forearm") -> MuscleGroup.FOREARMS
        id.contains("ab") || id.contains("oblique") || id.contains("core") -> MuscleGroup.CORE
        id.contains("hamstring") -> MuscleGroup.HAMSTRINGS
        id.contains("glute") -> MuscleGroup.GLUTES
        id.contains("calf") || id.contains("lower-leg") -> MuscleGroup.CALVES
        id.contains("quad") || id.contains("shin") || id.contains("hip-flexor") -> MuscleGroup.QUADRICEPS
        id.contains("thigh") -> when (viewSide) {
            MuscleHeatmapViewSide.FRONT -> MuscleGroup.QUADRICEPS
            MuscleHeatmapViewSide.BACK -> MuscleGroup.HAMSTRINGS
        }
        else -> null
    }
}

private fun String.isLatPathId(): Boolean {
    return contains("latissimus") || split('-').any { it == "lat" || it == "lats" }
}
