package com.example.liftrix.ui.progress

sealed class ProgressExportProgress {
    data class InProgress(val percent: Int) : ProgressExportProgress()
    data object Completed : ProgressExportProgress()
    data class Failed(val message: String) : ProgressExportProgress()
}
