package com.example.liftrix.di

import com.example.liftrix.data.export.AnalyticsExporter
import com.example.liftrix.data.export.CsvExporter
import com.example.liftrix.data.export.PdfExporter
import com.example.liftrix.data.local.dao.DataExportDao
import com.example.liftrix.data.local.dao.DataImportDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCase
import com.example.liftrix.domain.usecase.data_import.DataImportUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for export-related dependencies
 * 
 * Provides dependency injection bindings for:
 * - AnalyticsExporter: Coordinated export functionality
 * - PdfExporter: PDF report generation using iText7
 * - CsvExporter: CSV data export using Apache Commons CSV
 * 
 * Architecture:
 * - All providers scoped as @Singleton for performance
 * - AnalyticsExporter depends on both PdfExporter and CsvExporter
 * - Clean separation of concerns between export formats
 * - Integration with existing service layer through proper DI
 * 
 * Performance:
 * - Singleton scope prevents repeated instantiation
 * - Lazy initialization through Dagger/Hilt
 * - Memory efficient with shared instances
 */
@Module
@InstallIn(SingletonComponent::class)
object ExportModule {
    
    /**
     * Provides PdfExporter for PDF report generation
     * 
     * @return PdfExporter instance with iText7 integration
     */
    @Provides
    @Singleton
    fun providePdfExporter(): PdfExporter = PdfExporter()
    
    /**
     * Provides CsvExporter for CSV data export
     * 
     * @return CsvExporter instance with Apache Commons CSV integration
     */
    @Provides
    @Singleton
    fun provideCsvExporter(): CsvExporter = CsvExporter()
    
    /**
     * Provides AnalyticsExporter for coordinated export functionality
     * 
     * @param pdfExporter PDF generation service
     * @param csvExporter CSV generation service
     * @return AnalyticsExporter instance coordinating both export formats
     */
    @Provides
    @Singleton
    fun provideAnalyticsExporter(
        pdfExporter: PdfExporter,
        csvExporter: CsvExporter
    ): AnalyticsExporter = AnalyticsExporter(pdfExporter, csvExporter)
    
    /**
     * Provides ExportWorkoutsUseCase for exporting workout data
     * 
     * @param workoutDao DAO for accessing workout data
     * @param dataExportDao DAO for managing export records
     * @return ExportWorkoutsUseCase instance for handling exports
     */
    @Provides
    @Singleton
    fun provideExportWorkoutsUseCase(
        workoutDao: WorkoutDao,
        dataExportDao: DataExportDao
    ): ExportWorkoutsUseCase = ExportWorkoutsUseCase(workoutDao, dataExportDao)
    
    /**
     * Provides DataImportUseCase for importing and validating workout data
     *
     * @param formatDetector Service for detecting file formats
     * @param parserFactory Factory for creating workout parsers
     * @param exerciseMappingService Service for mapping exercises
     * @param workoutDao DAO for storing imported workout data
     * @param dataImportDao DAO for managing import records
     * @return DataImportUseCase instance for handling imports and validation
     */
    @Provides
    @Singleton
    fun provideDataImportUseCase(
        formatDetector: com.example.liftrix.domain.service.parser.FormatDetector,
        parserFactory: com.example.liftrix.domain.service.parser.WorkoutParserFactory,
        exerciseMappingService: com.example.liftrix.domain.service.ExerciseMappingService,
        workoutDao: WorkoutDao,
        dataImportDao: DataImportDao
    ): DataImportUseCase = DataImportUseCase(
        formatDetector = formatDetector,
        parserFactory = parserFactory,
        exerciseMappingService = exerciseMappingService,
        workoutDao = workoutDao,
        dataImportDao = dataImportDao
    )
}