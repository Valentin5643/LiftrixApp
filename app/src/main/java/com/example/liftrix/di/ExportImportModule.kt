package com.example.liftrix.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportImportModule {
    @Binds
    @Singleton
    abstract fun bindExerciseMappingService(
        impl: com.example.liftrix.domain.service.ExerciseMappingServiceImpl
    ): com.example.liftrix.domain.service.ExerciseMappingService

    @Binds
    @Singleton
    abstract fun bindPdfGenerationService(
        impl: com.example.liftrix.data.service.PdfGenerationServiceImpl
    ): com.example.liftrix.domain.service.PdfGenerationService

    @Binds
    @Singleton
    abstract fun bindDownloadManagerService(
        impl: com.example.liftrix.data.service.DownloadManagerServiceImpl
    ): com.example.liftrix.domain.service.DownloadManagerService

    @Binds
    @Singleton
    abstract fun bindProgressReportFileManager(
        impl: com.example.liftrix.service.export.ProgressReportFileManagerImpl
    ): com.example.liftrix.domain.service.ProgressReportFileManager

    companion object {
        @Provides
        @Singleton
        fun provideFormatDetector(): com.example.liftrix.domain.service.parser.FormatDetector =
            com.example.liftrix.domain.service.parser.FormatDetectorImpl()

        @Provides
        @Singleton
        fun provideJsonParser(): com.example.liftrix.domain.service.parser.JsonParser =
            com.example.liftrix.domain.service.parser.JsonParser()

        @Provides
        @Singleton
        fun provideCsvParser(): com.example.liftrix.domain.service.parser.CsvParser =
            com.example.liftrix.domain.service.parser.CsvParser()

        @Provides
        @Singleton
        fun provideTcxParser(): com.example.liftrix.domain.service.parser.TcxParser =
            com.example.liftrix.domain.service.parser.TcxParser()

        @Provides
        @Singleton
        fun provideGpxParser(): com.example.liftrix.domain.service.parser.GpxParser =
            com.example.liftrix.domain.service.parser.GpxParser()

        @Provides
        @Singleton
        fun provideFitParser(): com.example.liftrix.domain.service.parser.FitParser =
            com.example.liftrix.domain.service.parser.FitParser()

        @Provides
        @Singleton
        fun provideWorkoutParserFactory(
            jsonParser: com.example.liftrix.domain.service.parser.JsonParser,
            csvParser: com.example.liftrix.domain.service.parser.CsvParser,
            tcxParser: com.example.liftrix.domain.service.parser.TcxParser,
            gpxParser: com.example.liftrix.domain.service.parser.GpxParser,
            fitParser: com.example.liftrix.domain.service.parser.FitParser
        ): com.example.liftrix.domain.service.parser.WorkoutParserFactory =
            com.example.liftrix.domain.service.parser.WorkoutParserFactory(
                jsonParser = jsonParser,
                csvParser = csvParser,
                tcxParser = tcxParser,
                gpxParser = gpxParser,
                fitParser = fitParser
            )

        @Provides
        @Singleton
        fun provideExportWorkoutsUseCase(
            workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
            dataExportDao: com.example.liftrix.data.local.dao.DataExportDao
        ): com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCase =
            com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCaseImpl(workoutDao, dataExportDao)

        @Provides
        @Singleton
        fun provideDataImportUseCase(
            formatDetector: com.example.liftrix.domain.service.parser.FormatDetector,
            parserFactory: com.example.liftrix.domain.service.parser.WorkoutParserFactory,
            exerciseMappingService: com.example.liftrix.domain.service.ExerciseMappingService
        ): com.example.liftrix.domain.usecase.data_import.DataImportUseCase =
            com.example.liftrix.domain.usecase.data_import.DataImportUseCaseImpl(
                formatDetector = formatDetector,
                parserFactory = parserFactory,
                exerciseMappingService = exerciseMappingService
            )

        @Provides
        @Singleton
        fun providePdfExporter(): com.example.liftrix.data.export.PdfExporter =
            com.example.liftrix.data.export.PdfExporter()

        @Provides
        @Singleton
        fun provideCsvExporter(): com.example.liftrix.data.export.CsvExporter =
            com.example.liftrix.data.export.CsvExporter()

        @Provides
        @Singleton
        fun provideAnalyticsExporter(
            pdfExporter: com.example.liftrix.data.export.PdfExporter,
            csvExporter: com.example.liftrix.data.export.CsvExporter
        ): com.example.liftrix.data.export.AnalyticsExporter =
            com.example.liftrix.data.export.AnalyticsExporter(pdfExporter, csvExporter)
    }
}
