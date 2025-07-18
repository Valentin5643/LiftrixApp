package com.example.liftrix.di

import com.example.liftrix.data.export.AnalyticsExporter
import com.example.liftrix.data.export.CsvExporter
import com.example.liftrix.data.export.PdfExporter
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
}