package com.example.liftrix.domain.usecase.ai

import javax.inject.Inject

class WorkoutGenerationIntentClassifier @Inject constructor() {

    fun classify(message: String): ChatIntent {
        val normalized = " ${message.lowercase().trim()} "
            .replace(Regex("[^a-z0-9/\\s-]"), " ")
            .replace(Regex("\\s+"), " ")

        if (normalized.isBlank()) return ChatIntent.GeneralChat
        if (negativeSignals.any { normalized.contains(it) }) return ChatIntent.GeneralChat

        val hasSourceSignal = sourceSignals.any { normalized.contains(it) }
        val hasProgressionSignal = progressionSignals.any { normalized.contains(it) }
        val hasWorkoutTargetSignal = workoutTargetSignals.any { normalized.contains(it) }
        val hasEditModificationSignal = editModificationSignals.any { normalized.contains(it) }
        val hasGoalRetargetSignal = goalRetargetSignals.any { normalized.contains(it) }
        val hasModificationSignal = (hasEditModificationSignal || (hasSourceSignal && hasGoalRetargetSignal)) &&
            !normalized.contains(" make me ")

        if (hasProgressionSignal && hasWorkoutTargetSignal) return ChatIntent.UpdatePlanFromProgress
        if (hasModificationSignal && hasSourceSignal) return ChatIntent.ModifyWorkout
        if (hasModificationSignal && !hasSourceSignal) return ChatIntent.NeedsClarification

        val hasPlanningSignal = planningSignals.any { normalized.contains(it) } ||
            splitSignals.any { normalized.contains(it) } ||
            Regex("\\b\\d\\s*(-| )?day\\b").containsMatchIn(normalized)
        val hasDomainSignal = domainSignals.any { normalized.contains(it) }
        val hasGoalSignal = goalRetargetSignals.any { normalized.contains(it) }

        if (hasPlanningSignal && hasDomainSignal) return ChatIntent.GenerateWorkout
        if (hasGoalSignal && hasDomainSignal) return ChatIntent.GenerateWorkout

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
            " put together ",
            " give me ",
            " recommend ",
            " suggest ",
            " i want ",
            " want ",
            " need "
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
            " full body ",
            " full-body ",
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
        private val editModificationSignals = listOf(
            " adjust ",
            " modify ",
            " edit ",
            " change ",
            " update ",
            " increase ",
            " decrease ",
            " reduce ",
            " swap ",
            " replace ",
            " harder ",
            " easier "
        )
        private val goalRetargetSignals = listOf(
            " hypertrophy ",
            " strength ",
            " endurance ",
            " cardio ",
            " fat loss ",
            " muscle ",
            " intensity ",
            " volume "
        )
        private val sourceSignals = listOf(
            " this workout ",
            " that workout ",
            " current workout ",
            " selected workout ",
            " recent workout ",
            " last workout ",
            " latest workout ",
            " previous workout ",
            " this plan ",
            " current plan ",
            " last plan ",
            " latest plan ",
            " previous plan ",
            " this routine ",
            " my workout ",
            " my plan ",
            " my routine ",
            " template "
        )
        private val progressionSignals = listOf(
            " based on progress ",
            " from progress ",
            " my progress ",
            " progression ",
            " progressive overload ",
            " adapt my plan ",
            " update my plan ",
            " update the plan "
        )
        private val workoutTargetSignals = listOf(
            " workout ",
            " plan ",
            " program ",
            " routine ",
            " training "
        )
    }
}

sealed class ChatIntent {
    data object GenerateWorkout : ChatIntent()
    data object ModifyWorkout : ChatIntent()
    data object UpdatePlanFromProgress : ChatIntent()
    data object GeneralChat : ChatIntent()
    data object NeedsClarification : ChatIntent()
}
