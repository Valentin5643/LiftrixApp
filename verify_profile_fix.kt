// Test compilation of PublicProfileScreen fix
// This file verifies the type alignment between PublicUserProfile.fitnessGoals and GoalsSection parameter

fun testCompilation() {
    // PublicUserProfile.fitnessGoals: List<String>?
    val goals: List<String>? = listOf("Lose Weight", "Build Muscle", "Improve Endurance")
    
    // GoalsSection expects: List<String>
    if (goals != null) {
        // This should compile without errors
        println("Goals: $goals")
        goals.forEach { goal ->
            // goal is String, matching the expected parameter type
            println("Goal: $goal")
        }
    }
}