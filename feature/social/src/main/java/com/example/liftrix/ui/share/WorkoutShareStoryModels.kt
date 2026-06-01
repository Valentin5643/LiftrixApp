package com.example.liftrix.ui.share

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class WorkoutShareStoryStats(
    val workoutName: String,
    val displayDate: String,
    val totalVolume: String,
    val exerciseCount: String,
    val duration: String?,
    val prCount: Int,
    val prSummary: String,
    val prLabels: List<String>
)

@Immutable
data class WorkoutShareTemplate(
    val id: String,
    val displayName: String,
    val assetPath: String
)

enum class WorkoutShareAction {
    InstagramStory,
    WhatsApp,
    Save,
    NativeShare
}

data class WorkoutShareExportResult(
    val filePath: String,
    val width: Int,
    val height: Int
)

sealed class WorkoutShareEffect {
    data class LaunchShare(val intent: Intent) : WorkoutShareEffect()
    data class SaveSucceeded(val uri: Uri) : WorkoutShareEffect()
    data class ShowError(val message: String) : WorkoutShareEffect()
}
