package com.example.liftrix.ui.progress.detail.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for exporting data to CSV and sharing via Intent
 */
object CsvExportUtil {
    
    /**
     * Exports CSV data to a file and shares it via Intent
     * 
     * @param context Android context for file operations and intent
     * @param csvContent The CSV content to export
     * @param fileName Name for the exported file
     * @param shareTitle Title for the share dialog
     * @return LiftrixResult indicating success or failure
     */
    suspend fun exportAndShare(
        context: Context,
        csvContent: String,
        fileName: String,
        shareTitle: String = "Export Data"
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.ExportError(
                errorMessage = "Failed to export CSV: ${throwable.message}",
                operation = "exportAndShare",
                format = "csv"
            )
        }
    ) {
        // Create export directory in app's cache
        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        // Generate unique filename with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "${fileName}_$timestamp.csv")
        
        // Write CSV content to file
        FileWriter(file).use { writer ->
            writer.write(csvContent)
        }
        
        // Create share intent
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        // Launch share chooser
        val chooserIntent = Intent.createChooser(shareIntent, shareTitle)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
        
        Timber.d("CSV exported successfully: $fileName")
    }
    
    /**
     * Saves CSV data to internal storage
     * 
     * @param context Android context for file operations
     * @param csvContent The CSV content to save
     * @param fileName Name for the saved file
     * @return LiftrixResult with the file path or error
     */
    suspend fun saveToInternalStorage(
        context: Context,
        csvContent: String,
        fileName: String
    ): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.FileSystemError(
                errorMessage = "Failed to save CSV: ${throwable.message}",
                operation = "saveToInternalStorage"
            )
        }
    ) {
        // Create export directory in app's files
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        // Generate unique filename with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "${fileName}_$timestamp.csv")
        
        // Write CSV content to file
        FileWriter(file).use { writer ->
            writer.write(csvContent)
        }
        
        Timber.d("CSV saved to internal storage: ${file.absolutePath}")
        file.absolutePath
    }
}