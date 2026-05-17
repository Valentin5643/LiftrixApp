package com.example.liftrix.ui.settings.data

import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportIncludeOptions
import com.example.liftrix.domain.model.export.ProgressReportPrivacyOptions
import com.example.liftrix.domain.model.export.ProgressReportResult
import java.time.LocalDate

data class ProgressReportExportState(
    val selectedRange: ProgressReportDateRange = ProgressReportDateRange.Last30Days,
    val includeOptions: ProgressReportIncludeOptions = ProgressReportIncludeOptions(),
    val privacyOptions: ProgressReportPrivacyOptions = ProgressReportPrivacyOptions(),
    val customStartDateText: String = LocalDate.now().minusDays(29).toString(),
    val customEndDateText: String = LocalDate.now().toString(),
    val isLoadingUser: Boolean = true,
    val isGenerating: Boolean = false,
    val isSavingToDownloads: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val result: ProgressReportResult? = null
)

sealed class ProgressReportExportEvent {
    data class SelectRange(val range: ProgressReportDateRange) : ProgressReportExportEvent()
    data class UpdateCustomStartDate(val value: String) : ProgressReportExportEvent()
    data class UpdateCustomEndDate(val value: String) : ProgressReportExportEvent()
    data class UpdateIncludeOptions(val includeOptions: ProgressReportIncludeOptions) : ProgressReportExportEvent()
    data class UpdatePrivacyOptions(val privacyOptions: ProgressReportPrivacyOptions) : ProgressReportExportEvent()
    data object GeneratePdf : ProgressReportExportEvent()
    data object OpenPdf : ProgressReportExportEvent()
    data object SharePdf : ProgressReportExportEvent()
    data object SaveToDownloads : ProgressReportExportEvent()
    data object DismissError : ProgressReportExportEvent()
}

sealed class ProgressReportFileActionEvent {
    data class Open(val uriString: String, val mimeType: String) : ProgressReportFileActionEvent()
    data class Share(val uriString: String, val mimeType: String, val fileName: String) : ProgressReportFileActionEvent()
}
