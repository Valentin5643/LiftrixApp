package com.example.liftrix.feature.progress.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.liftrix.domain.model.BodyPart
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.domain.model.ProgressComparison
import com.example.liftrix.domain.model.ProgressPhoto
import com.example.liftrix.domain.model.PhotoType
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.ui.anomaly.AnomalyDashboardScreen
import com.example.liftrix.ui.anomaly.AnomalySettingsScreen
import com.example.liftrix.ui.progress.ProgressComparisonView
import com.example.liftrix.ui.progress.ProgressComparisonViewModel
import com.example.liftrix.ui.progress.ProgressDashboardScreen
import com.example.liftrix.ui.progress.detail.ExerciseRankingDetailScreen
import com.example.liftrix.ui.progress.detail.MuscleGroupDetailScreen
import com.example.liftrix.ui.progress.detail.OneRmProgressionDetailScreen
import com.example.liftrix.ui.progress.detail.VolumeAnalysisDetailScreen
import com.example.liftrix.ui.progress.detail.WorkoutFrequencyDetailScreen
import timber.log.Timber

@Composable
fun ProgressDashboardRoute(
    onNavigateToVolumeDetail: () -> Unit,
    onNavigateToOneRmDetail: () -> Unit,
    onNavigateToMuscleGroupDetail: () -> Unit,
    onNavigateToFrequencyDetail: () -> Unit,
    onNavigateToExerciseRankingDetail: () -> Unit,
    onNavigateToDashboardCustomization: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProgressDashboardScreen(
        modifier = modifier,
        onNavigateToVolumeDetail = onNavigateToVolumeDetail,
        onNavigateToOneRmDetail = onNavigateToOneRmDetail,
        onNavigateToMuscleGroupDetail = onNavigateToMuscleGroupDetail,
        onNavigateToFrequencyDetail = onNavigateToFrequencyDetail,
        onNavigateToExerciseRankingDetail = onNavigateToExerciseRankingDetail,
        onNavigateToDashboardCustomization = onNavigateToDashboardCustomization
    )
}

@Composable
fun AnomalyDashboardRoute(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnomalyDashboardScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier
    )
}

@Composable
fun AnomalySettingsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnomalySettingsScreen(
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun VolumeAnalysisDetailRoute(
    navController: NavController,
    groupBy: VolumeGrouping = VolumeGrouping.BY_WEEK,
    timeRange: TimeRangeType = TimeRangeType.MONTH,
    modifier: Modifier = Modifier
) {
    VolumeAnalysisDetailScreen(
        navController = navController,
        groupBy = groupBy,
        timeRange = timeRange,
        modifier = modifier
    )
}

@Composable
fun OneRmDetailRoute(
    navController: NavController,
    exerciseIds: List<String>? = null,
    timeRange: TimeRangeType = TimeRangeType.MONTH,
    modifier: Modifier = Modifier
) {
    OneRmProgressionDetailScreen(
        navController = navController,
        exerciseIds = exerciseIds,
        timeRange = timeRange,
        modifier = modifier
    )
}

@Composable
fun MuscleGroupDetailRoute(
    navController: NavController,
    muscleGroup: MuscleGroup? = null,
    timeRange: TimeRangeType = TimeRangeType.MONTH,
    modifier: Modifier = Modifier
) {
    MuscleGroupDetailScreen(
        navController = navController,
        muscleGroup = muscleGroup,
        timeRange = timeRange,
        modifier = modifier
    )
}

@Composable
fun ExerciseRankingDetailRoute(
    navController: NavController,
    sortBy: RankingMetric = RankingMetric.PERFORMANCE_SCORE,
    limit: Int = 20,
    modifier: Modifier = Modifier
) {
    ExerciseRankingDetailScreen(
        navController = navController,
        sortBy = sortBy,
        limit = limit,
        modifier = modifier
    )
}

@Composable
fun WorkoutFrequencyDetailRoute(
    navController: NavController,
    timeRange: TimeRangeType = TimeRangeType.MONTH,
    modifier: Modifier = Modifier
) {
    WorkoutFrequencyDetailScreen(
        navController = navController,
        timeRange = timeRange,
        modifier = modifier
    )
}

@Composable
fun ProgressComparisonRoute(
    comparisonId: String,
    shareMode: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ProgressComparisonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(comparisonId, shareMode) {
        viewModel.loadComparison(comparisonId, shareMode)
    }

    val comparison = uiState.comparison ?: placeholderProgressComparison(comparisonId, shareMode)
    ProgressComparisonView(
        comparison = comparison,
        modifier = modifier,
        onImageTap = { photo ->
            Timber.d("Image tap requested for photo: ${photo.id}")
        },
        onComparisonModeToggle = {
            Timber.d("Comparison mode toggle requested")
        }
    )
}

private fun placeholderProgressComparison(
    comparisonId: String,
    shareMode: Boolean
): ProgressComparison {
    val currentTime = System.currentTimeMillis()
    val placeholderBeforePhoto = ProgressPhoto(
        id = "before_photo_$comparisonId",
        userId = "",
        mediaId = "placeholder_media_before",
        bodyPart = BodyPart.FULL_BODY,
        photoType = PhotoType.FRONT,
        isPrivate = !shareMode,
        takenAt = currentTime - (4 * 7 * 24 * 60 * 60 * 1000),
        createdAt = currentTime
    )
    val placeholderAfterPhoto = ProgressPhoto(
        id = "after_photo_$comparisonId",
        userId = "",
        mediaId = "placeholder_media_after",
        bodyPart = BodyPart.FULL_BODY,
        photoType = PhotoType.FRONT,
        isPrivate = !shareMode,
        takenAt = currentTime,
        createdAt = currentTime
    )
    return ProgressComparison(
        id = comparisonId,
        userId = "",
        name = "Loading...",
        bodyPart = BodyPart.FULL_BODY,
        beforePhoto = placeholderBeforePhoto,
        afterPhoto = placeholderAfterPhoto,
        timeDifferenceWeeks = 4,
        createdAt = currentTime
    )
}
