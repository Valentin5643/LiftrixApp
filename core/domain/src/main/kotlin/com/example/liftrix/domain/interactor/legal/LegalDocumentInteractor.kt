package com.example.liftrix.domain.interactor.legal

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.legal.DownloadPdfResult
import com.example.liftrix.domain.usecase.legal.DownloadPdfUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LegalDocumentInteractor @Inject constructor(
    private val downloadPdfUseCase: DownloadPdfUseCase
) {
    suspend fun downloadPdf(
        userId: String,
        documentType: String,
        displayName: String = documentType
    ): Flow<DownloadPdfResult> =
        downloadPdfUseCase(userId, documentType, displayName)

    suspend fun validateDownloadConditions(userId: String): LiftrixResult<Unit> =
        downloadPdfUseCase.validateDownloadConditions(userId)
}
