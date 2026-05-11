package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal

object OfficialLiftrixAccountCatalog {
    const val COACH_ID = "official_liftrix_coach"
    const val CHALLENGE_ID = "official_liftrix_challenge"
    const val BEGINNER_ID = "official_liftrix_beginner"
    const val POWERLIFTING_ID = "official_liftrix_powerlifting"
    const val CALISTHENICS_ID = "official_liftrix_calisthenics"

    private val orderedIds = listOf(
        COACH_ID,
        CHALLENGE_ID,
        BEGINNER_ID,
        POWERLIFTING_ID,
        CALISTHENICS_ID
    )

    val accounts: List<OfficialAccount> = listOf(
        OfficialAccount(
            id = COACH_ID,
            handle = "liftrixcoach",
            displayName = "Liftrix Coach",
            bio = "Official Liftrix coaching cues for stronger, safer training.",
            profilePhotoUrl = "file:///android_asset/official_profiles/coach.png",
            postImageUrl = "file:///android_asset/official_posts/coach.png",
            posts = listOf(
                OfficialPost("Warm-Up Primer", "Five minutes of easy movement plus two ramp-up sets makes the first working set feel better.", listOf("Dynamic Warm-Up", "Ramp-Up Sets"), 25, 1200.0),
                OfficialPost("Form First", "Leave one rep in reserve when a new exercise still feels technical.", listOf("Technique Practice", "Controlled Reps"), 30, 1800.0),
                OfficialPost("Rest With Intent", "Heavy sets deserve real rest. Use the timer before adding weight.", listOf("Squat", "Bench Press"), 45, 5400.0),
                OfficialPost("Progressive Overload", "Add reps before load when the movement pattern is still settling in.", listOf("Dumbbell Press", "Row"), 40, 4200.0),
                OfficialPost("Recovery Check", "Sleep, food, and stress decide how hard today's session should be.", listOf("Mobility Flow", "Zone 2 Walk"), 35, 900.0)
            )
        ),
        OfficialAccount(
            id = CHALLENGE_ID,
            handle = "liftrixchallenge",
            displayName = "Liftrix Challenge",
            bio = "Official weekly challenges for consistency and variety.",
            profilePhotoUrl = "file:///android_asset/official_profiles/challenge.png",
            postImageUrl = "file:///android_asset/official_posts/challenge.png",
            posts = listOf(
                OfficialPost("Push-Up Ladder", "This week: 5, 8, 11, 14, 17 push-ups across five short sets.", listOf("Push-Up", "Plank"), 20, 0.0),
                OfficialPost("Step Streak", "Stack three ten-minute walks today and log the consistency win.", listOf("Brisk Walk", "Calf Raise"), 30, 0.0),
                OfficialPost("Squat Ladder", "Build from bodyweight squats to goblet squats while keeping depth consistent.", listOf("Bodyweight Squat", "Goblet Squat"), 25, 1800.0),
                OfficialPost("Mobility Week", "Spend one focused set on hips, shoulders, and ankles after training.", listOf("Hip Opener", "Shoulder CARs"), 15, 0.0),
                OfficialPost("Three-Day Streak", "Pick three non-consecutive days this week and finish the plan.", listOf("Full Body Circuit", "Stretching"), 40, 2600.0)
            )
        ),
        OfficialAccount(
            id = BEGINNER_ID,
            handle = "liftrixbeginner",
            displayName = "Liftrix Beginner",
            bio = "Official beginner-friendly training ideas and first-program guidance.",
            profilePhotoUrl = "file:///android_asset/official_profiles/beginner.png",
            postImageUrl = "file:///android_asset/official_posts/beginner.png",
            posts = listOf(
                OfficialPost("First Full Body", "Start simple: squat, push, pull, hinge, carry.", listOf("Goblet Squat", "Push-Up", "Dumbbell Row"), 35, 2600.0),
                OfficialPost("Bodyweight Starter", "Use slow reps and clean range before making bodyweight work harder.", listOf("Squat", "Incline Push-Up", "Glute Bridge"), 30, 0.0),
                OfficialPost("Dumbbell Basics", "Choose a load you can control for every rep, then add one rep next time.", listOf("Dumbbell Press", "Dumbbell Row"), 40, 3200.0),
                OfficialPost("Cardio Starter", "A steady walk after lifting is enough to build the habit.", listOf("Walk", "Breathing Reset"), 30, 0.0),
                OfficialPost("Choose Weights", "If form changes halfway through the set, the weight is not the target yet.", listOf("Romanian Deadlift", "Split Squat"), 35, 2400.0)
            )
        ),
        OfficialAccount(
            id = POWERLIFTING_ID,
            handle = "liftrixpowerlifting",
            displayName = "Liftrix Powerlifting",
            bio = "Official squat, bench, and deadlift technique reminders.",
            profilePhotoUrl = "file:///android_asset/official_profiles/powerlifting.png",
            postImageUrl = "file:///android_asset/official_posts/powerlifting.png",
            posts = listOf(
                OfficialPost("Squat Setup", "Brace before the walkout, then make every warm-up rep look the same.", listOf("Back Squat", "Paused Squat"), 45, 7200.0),
                OfficialPost("Deadlift Brace", "Pull slack from the bar before the plates leave the floor.", listOf("Deadlift", "Lat Pulldown"), 50, 8600.0),
                OfficialPost("Bench Pause", "Own the pause on the chest before pressing through the rack line.", listOf("Bench Press", "Paused Bench"), 40, 5400.0),
                OfficialPost("Accessory Rows", "Rows support the big lifts when the torso stays locked in.", listOf("Barbell Row", "Chest-Supported Row"), 40, 5000.0),
                OfficialPost("Deload Reminder", "Lower stress before performance drops. Deloads are training, not time off.", listOf("Tempo Squat", "Close-Grip Bench"), 35, 3600.0)
            )
        ),
        OfficialAccount(
            id = CALISTHENICS_ID,
            handle = "liftrixcalisthenics",
            displayName = "Liftrix Calisthenics",
            bio = "Official bodyweight progressions for control and strength.",
            profilePhotoUrl = "file:///android_asset/official_profiles/calisthenics.png",
            postImageUrl = "file:///android_asset/official_posts/calisthenics.png",
            posts = listOf(
                OfficialPost("Push-Up Progression", "Find the angle that lets every rep finish with a locked plank.", listOf("Incline Push-Up", "Push-Up"), 30, 0.0),
                OfficialPost("Hollow Hold", "Short clean holds beat long loose holds for core tension.", listOf("Hollow Body Hold", "Dead Bug"), 15, 0.0),
                OfficialPost("Pull-Up Negatives", "Step to the top and own the descent before chasing full reps.", listOf("Pull-Up Negative", "Scap Pull"), 25, 0.0),
                OfficialPost("Dip Support", "Build shoulder control with support holds before adding deep dips.", listOf("Dip Support Hold", "Bench Dip"), 20, 0.0),
                OfficialPost("Pistol Prep", "Train range and balance separately before combining them.", listOf("Box Pistol Squat", "Split Squat"), 35, 0.0)
            )
        )
    )

    fun matchAccountIds(
        selectedEquipment: Set<String>,
        selectedGoals: Set<String>
    ): List<String> {
        val equipment = selectedEquipment.mapNotNullTo(mutableSetOf()) { it.toEquipmentOrNull() }
        val goals = selectedGoals.mapNotNullTo(mutableSetOf()) { it.toFitnessGoalOrNull() }
        return matchAccountIds(equipment, goals)
    }

    fun matchAccountIds(
        selectedEquipment: Collection<Equipment>,
        selectedGoals: Collection<FitnessGoal>
    ): List<String> {
        val equipment = selectedEquipment.toSet()
        val goals = selectedGoals.toSet()
        val matches = linkedSetOf(COACH_ID, CHALLENGE_ID)

        if (
            FitnessGoal.GENERAL_FITNESS in goals ||
            FitnessGoal.LOSE_WEIGHT in goals ||
            Equipment.BODYWEIGHT_ONLY in equipment ||
            equipment.size in 1..2
        ) {
            matches += BEGINNER_ID
        }

        if (
            Equipment.BARBELL in equipment ||
            (Equipment.BENCH in equipment && (FitnessGoal.INCREASE_STRENGTH in goals || FitnessGoal.BUILD_MUSCLE in goals))
        ) {
            matches += POWERLIFTING_ID
        }

        if (Equipment.BODYWEIGHT_ONLY in equipment || Equipment.PULL_UP_BAR in equipment) {
            matches += CALISTHENICS_ID
        }

        if (matches.size < 3) {
            matches += BEGINNER_ID
        }

        return orderedIds.filter { it in matches }
    }

    fun accountById(id: String): OfficialAccount? = accounts.firstOrNull { it.id == id }

    private fun String.toEquipmentOrNull(): Equipment? =
        enumValues<Equipment>().firstOrNull { equipment ->
            equals(equipment.name, ignoreCase = true) || equals(equipment.displayName, ignoreCase = true)
        }

    private fun String.toFitnessGoalOrNull(): FitnessGoal? =
        enumValues<FitnessGoal>().firstOrNull { goal ->
            equals(goal.name, ignoreCase = true) || equals(goal.displayName, ignoreCase = true)
        }
}

data class OfficialAccount(
    val id: String,
    val handle: String,
    val displayName: String,
    val bio: String,
    val profilePhotoUrl: String,
    val postImageUrl: String,
    val posts: List<OfficialPost>,
    val verified: Boolean = true
)

data class OfficialPost(
    val workoutName: String,
    val caption: String,
    val exercises: List<String>,
    val durationMinutes: Int,
    val totalVolume: Double
)
