package com.example.liftrix.ui.settings.data

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCase
import com.example.liftrix.domain.usecase.export.ExportFormat
import com.example.liftrix.domain.usecase.export.ExportRequest
import com.example.liftrix.domain.usecase.export.ExportResult
import com.example.liftrix.domain.usecase.export.ExportProgress
import com.example.liftrix.domain.usecase.export.DataType
import com.example.liftrix.domain.usecase.export.DateRange
import com.example.liftrix.domain.usecase.data_import.ImportWorkoutsUseCase
import com.example.liftrix.domain.usecase.data_import.ImportValidation
import com.example.liftrix.domain.usecase.data_import.ImportResult
import com.example.liftrix.domain.usecase.data_import.ImportProgress
import com.example.liftrix.domain.usecase.data_import.ImportOptions
import com.example.liftrix.domain.usecase.data_import.ConflictStrategy
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.state.isLoading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for data portability features including import/export of workout data.
 * 
 * This ViewModel implements the MVI pattern to manage data import and export operations
 * with comprehensive progress tracking, error handling, and format support.
 * 
 * Key Features:
 * - Export workouts in multiple formats (JSON, CSV, FIT, TCX)
 * - Import validation with format auto-detection
 * - Real-time progress tracking for long operations
 * - Comprehensive error handling with recovery options
 * - Date range selection for targeted exports
 * - Conflict resolution during imports
 * 
 * Supported Formats:
 * - JSON: Complete workout data with all metadata
 * - CSV: Tabular format for analysis in spreadsheets
 * - FIT: Fitness industry standard format
 * - TCX: Training Center XML format
 * 
 * @property exportWorkoutsUseCase Use case for exporting workout data
 * @property importWorkoutsUseCase Use case for importing workout data
 * @property getCurrentUserIdUseCase Use case for getting current user ID
 * @property errorHandler Centralized error handler
 */
@HiltViewModel
class DataPortabilityViewModel @Inject constructor(
    private val exportWorkoutsUseCase: ExportWorkoutsUseCase,
    private val importWorkoutsUseCase: ImportWorkoutsUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<DataPortabilityUiState, DataPortabilityEvent>(errorHandler) {

    override val _uiState = MutableStateFlow(
        DataPortabilityUiState(
            exportState = UiState.Success(ExportData()),
            importState = UiState.Success(ImportData())
        )
    )

    private var currentUserId: String? = null
    private var currentExportId: String? = null
    private var currentImportId: String? = null

    init {
        loadCurrentUser()
    }

    override fun handleEvent(event: DataPortabilityEvent) {
        when (event) {
            is DataPortabilityEvent.LoadData -> loadData()
            is DataPortabilityEvent.SelectExportFormat -> selectExportFormat(event.format)
            is DataPortabilityEvent.SelectDataTypes -> selectDataTypes(event.dataTypes)
            is DataPortabilityEvent.SelectDateRange -> selectDateRange(event.dateRange)
            is DataPortabilityEvent.StartExport -> startExport()
            is DataPortabilityEvent.CancelExport -> cancelExport()
            is DataPortabilityEvent.ValidateImportFile -> validateImportFile(event.uri, event.inputStream)
            is DataPortabilityEvent.SelectConflictStrategy -> selectConflictStrategy(event.strategy)
            is DataPortabilityEvent.StartImport -> startImport(event.uri, event.inputStream)
            is DataPortabilityEvent.CancelImport -> cancelImport()
            is DataPortabilityEvent.ShareExport -> shareExport(event.exportResult)
            is DataPortabilityEvent.DismissError -> dismissError()
            is DataPortabilityEvent.Reset -> reset()
        }
    }

    override fun setLoadingState() {
        updateState { currentState ->
            currentState.copy(
                exportState = UiState.Loading,
                importState = UiState.Loading
            )
        }
    }

    override fun updateErrorState(error: LiftrixError) {
        updateState { currentState ->
            currentState.copy(
                exportState = if (currentState.exportState.isLoading()) {
                    UiState.Error(error, currentState.exportState.dataOrNull())
                } else currentState.exportState,
                importState = if (currentState.importState.isLoading()) {
                    UiState.Error(error, currentState.importState.dataOrNull())
                } else currentState.importState
            )
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase()
                if (userId != null) {
                    currentUserId = userId
                    Timber.d("Current user loaded: $userId")
                } else {
                    Timber.e("Failed to get current user: User not authenticated")
                    updateState { currentState ->
                        currentState.copy(
                            exportState = UiState.Error(
                                LiftrixError.AuthenticationError(
                                    errorMessage = "User not authenticated",
                                    analyticsContext = mapOf("operation" to "LOAD_USER")
                                )
                            ),
                            importState = UiState.Error(
                                LiftrixError.AuthenticationError(
                                    errorMessage = "User not authenticated",
                                    analyticsContext = mapOf("operation" to "LOAD_USER")
                                )
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user")
                updateState { currentState ->
                    currentState.copy(
                        exportState = UiState.Error(
                            LiftrixError.BusinessLogicError(
                                code = "LOAD_USER_FAILED",
                                errorMessage = e.message ?: "Failed to load current user"
                            )
                        ),
                        importState = UiState.Error(
                            LiftrixError.BusinessLogicError(
                                code = "LOAD_USER_FAILED",
                                errorMessage = e.message ?: "Failed to load current user"
                            )
                        )
                    )
                }
            }
        }
    }

    private fun loadData() {
        // Reset to initial state
        updateState {
            DataPortabilityUiState(
                exportState = UiState.Success(ExportData()),
                importState = UiState.Success(ImportData())
            )
        }
        
        if (currentUserId == null) {
            loadCurrentUser()
        }
    }

    private fun selectExportFormat(format: ExportFormat) {
        updateState { currentState ->
            val exportData = (currentState.exportState.dataOrNull() ?: ExportData()).copy(selectedFormat = format)
            currentState.copy(exportState = UiState.Success(exportData))
        }
    }

    private fun selectDataTypes(dataTypes: Set<DataType>) {
        updateState { currentState ->
            val exportData = (currentState.exportState.dataOrNull() ?: ExportData()).copy(selectedDataTypes = dataTypes)
            currentState.copy(exportState = UiState.Success(exportData))
        }
    }

    private fun selectDateRange(dateRange: DateRange?) {
        updateState { currentState ->
            val exportData = (currentState.exportState.dataOrNull() ?: ExportData()).copy(selectedDateRange = dateRange)
            currentState.copy(exportState = UiState.Success(exportData))
        }
    }

    private fun startExport() {
        val userId = currentUserId
        if (userId == null) {
            handleError(LiftrixError.AuthenticationError("User not authenticated"))
            return
        }

        val currentExportData = when (val state = _uiState.value.exportState) {
            is UiState.Success -> state.data
            else -> null
        }
        if (currentExportData == null) {
            handleError(LiftrixError.ValidationError(
                field = "export_configuration",
                violations = listOf("Export configuration not available"),
                analyticsContext = mapOf("operation" to "START_EXPORT")
            ))
            return
        }

        // Validate export configuration
        if (currentExportData.selectedDataTypes.isEmpty()) {
            handleError(LiftrixError.ValidationError(
                field = "data_types",
                violations = listOf("At least one data type must be selected"),
                analyticsContext = mapOf("operation" to "START_EXPORT")
            ))
            return
        }

        val exportRequest = ExportRequest(
            format = currentExportData.selectedFormat,
            dataTypes = currentExportData.selectedDataTypes,
            dateRange = currentExportData.selectedDateRange
        )

        // Update state to show export in progress
        updateState { currentState ->
            val exportData = currentExportData.copy(
                isExporting = true,
                exportProgress = 0,
                exportError = null
            )
            currentState.copy(exportState = UiState.Success(exportData))
        }

        viewModelScope.launch {
            val result = exportWorkoutsUseCase.invoke(userId, exportRequest)
            result.fold(
                onSuccess = { exportResult ->
                    currentExportId = exportResult.exportId
                    
                    // Track export progress
                    exportWorkoutsUseCase.getExportProgress(exportResult.exportId)
                        .onEach { progress ->
                            updateExportProgress(progress)
                        }
                        .launchIn(viewModelScope)
                    
                    // Update state with successful export
                    updateState { currentState ->
                        val exportData = (currentState.exportState.dataOrNull() ?: ExportData()).copy(
                            isExporting = false,
                            exportProgress = 100,
                            lastExportResult = exportResult,
                            exportError = null
                        )
                        currentState.copy(exportState = UiState.Success(exportData))
                    }
                    
                    Timber.d("Export completed successfully: ${exportResult.exportId}")
                },
                onFailure = { error ->
                    updateState { currentState ->
                        val exportData = (currentState.exportState.dataOrNull() ?: ExportData()).copy(
                            isExporting = false,
                            exportProgress = 0,
                            exportError = error as? LiftrixError
                        )
                        currentState.copy(exportState = UiState.Success(exportData))
                    }
                    Timber.e("Export failed: $error")
                }
            )
        }
    }

    private fun updateExportProgress(progress: ExportProgress) {
        updateState { currentState ->
            val exportData = (currentState.exportState.dataOrNull() ?: ExportData()).copy(
                exportProgress = progress.progressPercentage,
                exportStatusMessage = progress.statusMessage
            )
            currentState.copy(exportState = UiState.Success(exportData))
        }
    }

    private fun cancelExport() {
        val userId = currentUserId
        val exportId = currentExportId
        
        if (userId == null || exportId == null) {
            Timber.w("Cannot cancel export: missing user ID or export ID")
            return
        }

        viewModelScope.launch {
            try {
                exportWorkoutsUseCase.cancelExport(exportId, userId)
                
                updateState { currentState ->
                    when (val state = currentState.exportState) {
                        is UiState.Success -> {
                            val exportData = state.data.copy(
                                isExporting = false,
                                exportProgress = 0,
                                exportStatusMessage = "Export cancelled"
                            )
                            currentState.copy(exportState = UiState.Success(exportData))
                        }
                        else -> currentState
                    }
                }
                
                currentExportId = null
                Timber.d("Export cancelled successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel export")
                handleError(LiftrixError.BusinessLogicError(
                    code = "CANCEL_EXPORT_FAILED",
                    errorMessage = "Failed to cancel export",
                    analyticsContext = mapOf(
                        "export_id" to exportId,
                        "operation" to "CANCEL_EXPORT"
                    )
                ))
            }
        }
    }

    private fun validateImportFile(uri: Uri, inputStream: InputStream) {
        updateState { currentState ->
            val importData = when (val state = currentState.importState) {
                is UiState.Success -> state.data.copy(
                    isValidating = true,
                    validationError = null
                )
                else -> ImportData(isValidating = true)
            }
            currentState.copy(importState = UiState.Success(importData))
        }

        viewModelScope.launch {
            val result = importWorkoutsUseCase.validateImportFile(uri, inputStream)
            result.fold(
                onSuccess = { validation ->
                    updateState { currentState ->
                        val importData = (currentState.importState.dataOrNull() ?: ImportData()).copy(
                            isValidating = false,
                            validation = validation,
                            selectedUri = uri,
                            validationError = null
                        )
                        currentState.copy(importState = UiState.Success(importData))
                    }
                    
                    Timber.d("Import validation completed: ${validation.totalWorkouts} workouts found")
                },
                onFailure = { error ->
                    updateState { currentState ->
                        val importData = (currentState.importState.dataOrNull() ?: ImportData()).copy(
                            isValidating = false,
                            validationError = error as? LiftrixError
                        )
                        currentState.copy(importState = UiState.Success(importData))
                    }
                    Timber.e("Import validation failed: $error")
                }
            )
        }
    }

    private fun selectConflictStrategy(strategy: ConflictStrategy) {
        updateState { currentState ->
            val importData = (currentState.importState.dataOrNull() ?: ImportData()).copy(selectedConflictStrategy = strategy)
            currentState.copy(importState = UiState.Success(importData))
        }
    }

    private fun startImport(uri: Uri, inputStream: InputStream) {
        val userId = currentUserId
        if (userId == null) {
            handleError(LiftrixError.AuthenticationError("User not authenticated"))
            return
        }

        val currentImportData = when (val state = _uiState.value.importState) {
            is UiState.Success -> state.data
            else -> null
        }
        if (currentImportData?.validation == null) {
            handleError(LiftrixError.ValidationError(
                field = "import_validation",
                violations = listOf("File must be validated before import"),
                analyticsContext = mapOf("operation" to "START_IMPORT")
            ))
            return
        }

        val importOptions = ImportOptions(
            detectedFormat = currentImportData.validation?.format,
            sourceApp = null,
            conflictStrategy = currentImportData.selectedConflictStrategy,
            allowValidationErrors = false
        )

        // Update state to show import in progress
        updateState { currentState ->
            val importData = currentImportData.copy(
                isImporting = true,
                importProgress = 0,
                importError = null
            )
            currentState.copy(importState = UiState.Success(importData))
        }

        viewModelScope.launch {
            val result = importWorkoutsUseCase.importWorkouts(userId, uri, inputStream, importOptions)
            result.fold(
                onSuccess = { importResult ->
                    currentImportId = importResult.importId
                    
                    // Track import progress
                    importWorkoutsUseCase.getImportProgress(importResult.importId)
                        .onEach { progress ->
                            updateImportProgress(progress)
                        }
                        .launchIn(viewModelScope)
                    
                    // Update state with successful import
                    updateState { currentState ->
                        val importData = (currentState.importState.dataOrNull() ?: ImportData()).copy(
                            isImporting = false,
                            importProgress = 100,
                            lastImportResult = importResult,
                            importError = null
                        )
                        currentState.copy(importState = UiState.Success(importData))
                    }
                    
                    Timber.d("Import completed successfully: ${importResult.importId}")
                },
                onFailure = { error ->
                    updateState { currentState ->
                        val importData = (currentState.importState.dataOrNull() ?: ImportData()).copy(
                            isImporting = false,
                            importProgress = 0,
                            importError = error as? LiftrixError
                        )
                        currentState.copy(importState = UiState.Success(importData))
                    }
                    Timber.e("Import failed: $error")
                }
            )
        }
    }

    private fun updateImportProgress(progress: ImportProgress) {
        updateState { currentState ->
            val importData = (currentState.importState.dataOrNull() ?: ImportData()).copy(
                importProgress = progress.progressPercentage,
                importStatusMessage = progress.statusMessage
            )
            currentState.copy(importState = UiState.Success(importData))
        }
    }

    private fun cancelImport() {
        val importId = currentImportId
        
        if (importId == null) {
            Timber.w("Cannot cancel import: missing import ID")
            return
        }

        // Note: Cancel import implementation would go here
        // For now, just update the UI state
        updateState { currentState ->
            when (val state = currentState.importState) {
                is UiState.Success -> {
                    val importData = state.data.copy(
                        isImporting = false,
                        importProgress = 0,
                        importStatusMessage = "Import cancelled"
                    )
                    currentState.copy(importState = UiState.Success(importData))
                }
                else -> currentState
            }
        }
        
        currentImportId = null
        Timber.d("Import cancelled")
    }

    private fun shareExport(exportResult: ExportResult) {
        viewModelScope.launch {
            try {
                // Create a share intent for the exported file
                val shareIntent = createShareIntent(exportResult)
                
                // Update state to indicate sharing is in progress
                updateState { currentState ->
                    val exportData = when (val state = currentState.exportState) {
                        is UiState.Success -> state.data.copy(
                            exportStatusMessage = "Opening share dialog..."
                        )
                        else -> ExportData(exportStatusMessage = "Opening share dialog...")
                    }
                    currentState.copy(exportState = UiState.Success(exportData))
                }
                
                // Emit sharing event for the UI to handle
                _shareExportEvent.emit(ShareExportEvent(exportResult, shareIntent))
                
                Timber.d("Share initiated for export: ${exportResult.exportId}")
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to share export")
                updateState { currentState ->
                    currentState.copy(
                        exportState = UiState.Error(
                            error = LiftrixError.BusinessLogicError(
                                code = "SHARE_FAILED",
                                errorMessage = "Failed to share export: ${exception.message}",
                                analyticsContext = mapOf("export_id" to exportResult.exportId)
                            ),
                            previousData = when (val state = currentState.exportState) {
                                is UiState.Success -> state.data
                                is UiState.Error -> state.previousData
                                else -> null
                            }
                        )
                    )
                }
            }
        }
    }
    
    private fun createShareIntent(exportResult: ExportResult): ShareIntentData {
        val mimeType = when (exportResult.format) {
            ExportFormat.JSON -> "application/json"
            ExportFormat.CSV -> "text/csv"
            ExportFormat.FIT -> "application/octet-stream"
            ExportFormat.TCX -> "application/xml"
        }
        
        val fileName = "liftrix_export_${exportResult.exportId}.${exportResult.format.name.lowercase()}"
        
        return ShareIntentData(
            fileUri = exportResult.file.absolutePath,
            mimeType = mimeType,
            fileName = fileName,
            chooserTitle = "Share Liftrix Export"
        )
    }
    
    // Add a SharedFlow for share events that the UI can collect
    private val _shareExportEvent = MutableSharedFlow<ShareExportEvent>()
    val shareExportEvent: SharedFlow<ShareExportEvent> = _shareExportEvent.asSharedFlow()

    private fun dismissError() {
        updateState { currentState ->
            currentState.copy(
                exportState = currentState.exportState.let { state ->
                    when (state) {
                        is UiState.Error -> UiState.Success(state.previousData ?: ExportData())
                        else -> state
                    }
                },
                importState = currentState.importState.let { state ->
                    when (state) {
                        is UiState.Error -> UiState.Success(state.previousData ?: ImportData())
                        else -> state
                    }
                }
            )
        }
    }

    private fun reset() {
        currentExportId = null
        currentImportId = null
        
        updateState {
            DataPortabilityUiState(
                exportState = UiState.Success(ExportData()),
                importState = UiState.Success(ImportData())
            )
        }
    }
}

/**
 * UI state for data portability features.
 * 
 * @property exportState State of export operations and configuration
 * @property importState State of import operations and validation
 */
data class DataPortabilityUiState(
    val exportState: UiState<ExportData>,
    val importState: UiState<ImportData>
)

/**
 * Data class containing export configuration and state.
 * 
 * @property selectedFormat Currently selected export format
 * @property selectedDataTypes Set of data types to export
 * @property selectedDateRange Optional date range filter
 * @property isExporting Whether export is currently in progress
 * @property exportProgress Progress percentage (0-100)
 * @property exportStatusMessage Current status message
 * @property lastExportResult Result of the last successful export
 * @property exportError Error from failed export operation
 */
data class ExportData(
    val selectedFormat: ExportFormat = ExportFormat.JSON,
    val selectedDataTypes: Set<DataType> = setOf(DataType.WORKOUTS),
    val selectedDateRange: DateRange? = null,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportStatusMessage: String = "",
    val lastExportResult: ExportResult? = null,
    val exportError: LiftrixError? = null
)

/**
 * Data class containing import configuration and state.
 * 
 * @property selectedUri URI of the selected import file
 * @property isValidating Whether file validation is in progress
 * @property validation Result of file validation
 * @property selectedConflictStrategy Strategy for handling conflicts
 * @property isImporting Whether import is currently in progress
 * @property importProgress Progress percentage (0-100)
 * @property importStatusMessage Current status message
 * @property lastImportResult Result of the last successful import
 * @property validationError Error from failed validation
 * @property importError Error from failed import operation
 */
data class ImportData(
    val selectedUri: Uri? = null,
    val isValidating: Boolean = false,
    val validation: ImportValidation? = null,
    val selectedConflictStrategy: ConflictStrategy = ConflictStrategy.SKIP,
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importStatusMessage: String = "",
    val lastImportResult: ImportResult? = null,
    val validationError: LiftrixError? = null,
    val importError: LiftrixError? = null
)

/**
 * Events for data portability operations.
 */
sealed class DataPortabilityEvent : ViewModelEvent {
    object LoadData : DataPortabilityEvent()
    
    // Export events
    data class SelectExportFormat(val format: ExportFormat) : DataPortabilityEvent()
    data class SelectDataTypes(val dataTypes: Set<DataType>) : DataPortabilityEvent()
    data class SelectDateRange(val dateRange: DateRange?) : DataPortabilityEvent()
    object StartExport : DataPortabilityEvent()
    object CancelExport : DataPortabilityEvent()
    data class ShareExport(val exportResult: ExportResult) : DataPortabilityEvent()
    
    // Import events
    data class ValidateImportFile(val uri: Uri, val inputStream: InputStream) : DataPortabilityEvent()
    data class SelectConflictStrategy(val strategy: ConflictStrategy) : DataPortabilityEvent()
    data class StartImport(val uri: Uri, val inputStream: InputStream) : DataPortabilityEvent()
    object CancelImport : DataPortabilityEvent()
    
    // Common events
    object DismissError : DataPortabilityEvent()
    object Reset : DataPortabilityEvent()
}

/**
 * Data class for share intent information.
 */
data class ShareIntentData(
    val fileUri: String,
    val mimeType: String,
    val fileName: String,
    val chooserTitle: String
)

/**
 * Event for sharing an exported file.
 */
data class ShareExportEvent(
    val exportResult: ExportResult,
    val shareIntent: ShareIntentData
)

/**
 * ★ Insight ─────────────────────────────────────
 * - DataPortabilityViewModel follows the established MVI pattern with BaseViewModel inheritance
 * - Separates export and import concerns with distinct state objects and comprehensive progress tracking
 * - Integrates with use cases for business logic while maintaining reactive state management
 * ─────────────────────────────────────────────────
 */