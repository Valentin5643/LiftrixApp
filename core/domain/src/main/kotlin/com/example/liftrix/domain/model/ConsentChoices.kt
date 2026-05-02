package com.example.liftrix.domain.model

data class ConsentChoices(
    val privacyPolicy: Boolean,
    val healthData: Boolean,
    val aiChat: Boolean,
    val analytics: Boolean
)
