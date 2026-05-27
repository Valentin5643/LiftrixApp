package com.example.liftrix.ui.progress.components.widgets.heatmap

import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.analytics.MuscleHeatmapViewSide
import org.junit.Assert.assertEquals
import org.junit.Test

class MuscleHeatmapSvgMappingsTest {

    @Test
    fun maleBackShoulderPathsMapToShoulders() {
        assertEquals(
            MuscleGroup.SHOULDERS,
            muscleGroupForSvgId("left-shoulder-yellow", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.SHOULDERS,
            muscleGroupForSvgId("right-shoulder-yellow", MuscleHeatmapViewSide.BACK)
        )
    }

    @Test
    fun femaleShoulderTrapeziusPathsMapToUpperBack() {
        assertEquals(
            MuscleGroup.UPPER_BACK,
            muscleGroupForSvgId("left-shoulder-trapezius-yellow", MuscleHeatmapViewSide.FRONT)
        )
        assertEquals(
            MuscleGroup.UPPER_BACK,
            muscleGroupForSvgId("right-shoulder-trapezius-yellow", MuscleHeatmapViewSide.FRONT)
        )
        assertEquals(
            MuscleGroup.UPPER_BACK,
            muscleGroupForSvgId("left-trapezius-shoulder-yellow", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.UPPER_BACK,
            muscleGroupForSvgId("right-trapezius-shoulder-yellow", MuscleHeatmapViewSide.BACK)
        )
    }

    @Test
    fun pureShoulderPathsStillMapToShoulders() {
        assertEquals(
            MuscleGroup.SHOULDERS,
            muscleGroupForSvgId("left-shoulder-yellow", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.SHOULDERS,
            muscleGroupForSvgId("right-deltoid-orange", MuscleHeatmapViewSide.FRONT)
        )
    }

    @Test
    fun maleBackLateralLegPathsDoNotMapToLats() {
        assertEquals(
            MuscleGroup.HAMSTRINGS,
            muscleGroupForSvgId("left-lateral-hamstring-orange", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.HAMSTRINGS,
            muscleGroupForSvgId("right-lateral-hamstring-orange", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.CALVES,
            muscleGroupForSvgId("left-lateral-calf-yellow", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.CALVES,
            muscleGroupForSvgId("right-lateral-calf-yellow", MuscleHeatmapViewSide.BACK)
        )
    }

    @Test
    fun maleBackLatPathsStillMapToLats() {
        assertEquals(
            MuscleGroup.LATS,
            muscleGroupForSvgId("left-lat-red", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.LATS,
            muscleGroupForSvgId("right-lat-red", MuscleHeatmapViewSide.BACK)
        )
    }

    @Test
    fun lateralHipFlexorPathsDoNotMapToLats() {
        assertEquals(
            MuscleGroup.QUADRICEPS,
            muscleGroupForSvgId("left-lateral-hip-flexor-yellow", MuscleHeatmapViewSide.FRONT)
        )
        assertEquals(
            MuscleGroup.QUADRICEPS,
            muscleGroupForSvgId("right-lateral-hip-flexor-yellow", MuscleHeatmapViewSide.FRONT)
        )
    }

    @Test
    fun upperArmPathsMapByViewSide() {
        assertEquals(
            MuscleGroup.BICEPS,
            muscleGroupForSvgId("left-upper-arm-orange", MuscleHeatmapViewSide.FRONT)
        )
        assertEquals(
            MuscleGroup.BICEPS,
            muscleGroupForSvgId("right-upper-arm-orange", MuscleHeatmapViewSide.FRONT)
        )
        assertEquals(
            MuscleGroup.TRICEPS,
            muscleGroupForSvgId("left-inner-upper-arm-orange", MuscleHeatmapViewSide.BACK)
        )
        assertEquals(
            MuscleGroup.TRICEPS,
            muscleGroupForSvgId("right-outer-upper-arm-orange", MuscleHeatmapViewSide.BACK)
        )
    }
}
