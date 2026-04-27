package com.example.liftrix.domain.model.sharing

import com.example.liftrix.domain.model.WorkoutTemplate

data class SharedTemplatePreview(
    val shareEvent: TemplateShareEvent,
    val template: WorkoutTemplate?,
    val senderName: String
)

