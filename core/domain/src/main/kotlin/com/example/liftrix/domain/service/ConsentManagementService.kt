package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

interface ConsentManagementService {
    suspend fun recordConsent(
        userId: String,
        privacyPolicyVersion: String,
        healthDataConsent: Boolean,
        aiChatConsent: Boolean,
        analyticsConsent: Boolean,
        marketingConsent: Boolean
    ): LiftrixResult<Unit>
}
