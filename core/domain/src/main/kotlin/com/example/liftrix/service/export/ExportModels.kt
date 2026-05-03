package com.example.liftrix.service.export

sealed class ExportProgress {
    data object Queued : ExportProgress()
    data class InProgress(val progress: Int, val currentStep: String) : ExportProgress()
    data class Completed(val filePath: String, val fileSize: Long) : ExportProgress()
    data class Failed(val errorMessage: String) : ExportProgress()
    data object Cancelled : ExportProgress()
}

enum class ExportFormat {
    PDF,
    CSV
}

enum class RawDataFormat {
    JSON,
    CSV,
    FIT,
    TCX
}

enum class RawDataType {
    WORKOUTS,
    PROGRESS,
    PREFERENCES,
    ANALYTICS
}
