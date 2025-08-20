// Quick script to update username for testing
// Run this in your app's debug console or create a test function

suspend fun updateUsernameForTesting(
    userAccountDao: UserAccountDao,
    userId: String,
    newUsername: String
) {
    try {
        // Update username directly in database
        userAccountDao.updateUsername(userId, newUsername)
        println("✅ Username updated to: $newUsername")
        
        // Verify the update
        val updatedUsername = userAccountDao.getUsernameForUser(userId)
        println("📝 Current username: $updatedUsername")
        
    } catch (e: Exception) {
        println("❌ Failed to update username: ${e.message}")
    }
}

// Example test usernames you can use:
val testUsernames = listOf(
    "test_user_123",
    "fitness_fan_456",
    "gym_buddy_789",
    "strength_seeker",
    "cardio_queen",
    "iron_warrior"
)