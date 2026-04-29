package com.example.liftrix.domain.usecase.ai

import javax.inject.Inject

class WorkoutGenerationIntentClassifier @Inject constructor() {

    fun classify(message: String): ChatIntent {
        val normalized = " ${message.lowercase().trim()} "
            .replace(Regex("[^a-z0-9/\\s-]"), " ")
            .replace(Regex("\\s+"), " ")

        if (normalized.isBlank()) return ChatIntent.GeneralChat
        if (negativeSignals.any { normalized.contains(it) }) return ChatIntent.GeneralChat

        val hasPlanningSignal = planningSignals.any { normalized.contains(it) } ||
            splitSignals.any { normalized.contains(it) } ||
            Regex("\\b\\d\\s*(-| )?day\\b").containsMatchIn(normalized)
        val hasDomainSignal = domainSignals.any { normalized.contains(it) }

        if (hasPlanningSignal && hasDomainSignal) return ChatIntent.GenerateWorkout

        val ambiguousHomeTraining = normalized.contains(" train at home ") ||
            normalized.contains(" home training ") ||
            normalized.contains(" at home ")
        return if (ambiguousHomeTraining && hasDomainSignal) {
            ChatIntent.NeedsClarification
        } else {
            ChatIntent.GeneralChat
        }
    }

    companion object {
        private val planningSignals = listOf(
            " create ",
            " build ",
            " generate ",
            " plan ",
            " program ",
            " routine ",
            " workouts for ",
            " make me ",
            " put together "
        )
        private val splitSignals = listOf(
            " split ",
            " push/pull ",
            " push pull ",
            " upper/lower ",
            " upper lower ",
            " full body ",
            " ppl "
        )
        private val domainSignals = listOf(
            " workout ",
            " workouts ",
            " training ",
            " gym ",
            " exercise ",
            " exercises ",
            " dumbbell ",
            " dumbbells ",
            " bodyweight ",
            " home ",
            " split ",
            " days ",
            " routine ",
            " train "
        )
        private val negativeSignals = listOf(
            " form ",
            " technique ",
            " nutrition ",
            " protein ",
            " creatine ",
            " supplement ",
            " calories ",
            " pain ",
            " injury "
        )
    }
}

sealed class ChatIntent {
    data object GenerateWorkout : ChatIntent()
    data object GeneralChat : ChatIntent()
    data object NeedsClarification : ChatIntent()
}
