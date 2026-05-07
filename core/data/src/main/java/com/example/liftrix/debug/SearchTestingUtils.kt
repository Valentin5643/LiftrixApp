package com.example.liftrix.debug

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility functions for testing user search functionality.
 * 
 * Provides easy-to-use methods for populating test data and validating
 * search functionality during development and testing.
 */
@Singleton
class SearchTestingUtils @Inject constructor(
    private val testDataGenerator: UserSearchTestDataGenerator
) {

    /**
     * Populates the database with test users for search validation.
     * 
     * @param lifecycleScope The coroutine scope to launch the operation in
     * @param onComplete Callback when operation completes with count of users created
     */
    fun populateTestUsers(
        lifecycleScope: LifecycleCoroutineScope,
        onComplete: (Int) -> Unit = {}
    ) {
        lifecycleScope.launch {
            try {
                Timber.d("SearchTestingUtils: Starting test user population")
                val count = testDataGenerator.generateTestUsers()
                
                if (count > 0) {
                    Timber.d("SearchTestingUtils: Successfully created $count test users")
                    onComplete(count)
                } else {
                    Timber.w("SearchTestingUtils: No test users were created")
                    onComplete(0)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "SearchTestingUtils: Failed to populate test users")
                onComplete(0)
            }
        }
    }
    
    /**
     * Removes all test users from the database.
     * 
     * @param lifecycleScope The coroutine scope to launch the operation in
     * @param onComplete Callback when operation completes with count of users removed
     */
    fun cleanupTestUsers(
        lifecycleScope: LifecycleCoroutineScope,
        onComplete: (Int) -> Unit = {}
    ) {
        lifecycleScope.launch {
            try {
                Timber.d("SearchTestingUtils: Starting test user cleanup")
                val count = testDataGenerator.cleanupTestUsers()
                
                Timber.d("SearchTestingUtils: Successfully removed $count test users")
                onComplete(count)
                
            } catch (e: Exception) {
                Timber.e(e, "SearchTestingUtils: Failed to cleanup test users")
                onComplete(0)
            }
        }
    }
    
    /**
     * Provides a list of search queries to test various search scenarios.
     * 
     * @return List of test search queries covering different search patterns
     */
    fun getTestSearchQueries(): List<String> {
        return listOf(
            // Username searches
            "john_fit",
            "sarah_strength", 
            "mike_cardio",
            "lisa_yoga",
            "alex_newbie",
            "tom_crossfit",
            "emma_bodyweight",
            "david_powerlifter",
            "jenny_hiit",
            "ryan_swimmer",
            
            // Partial username searches
            "john",
            "sarah",
            "mike",
            "lisa",
            "alex",
            "tom",
            "emma",
            "david",
            "jenny",
            "ryan",
            
            // Display name searches
            "John Fitness",
            "Sarah Strong",
            "Mike Runner",
            "Lisa Zen",
            "Alex Beginner",
            "Tom CrossFit",
            "Emma Calisthenics",
            "David Power",
            "Jenny HIIT",
            "Ryan Waters",
            
            // Partial display name searches
            "Fitness",
            "Strong",
            "Runner",
            "Zen",
            "Beginner",
            "CrossFit",
            "Calisthenics",
            "Power",
            "HIIT",
            "Waters",
            
            // Fitness level searches (these might not work until we implement content-based search)
            "INTERMEDIATE",
            "ADVANCED",
            "BEGINNER",
            
            // Generic searches
            "fit",
            "strength",
            "cardio",
            "yoga",
            "crossfit",
            "bodyweight",
            "powerlifter",
            "hiit",
            "swimmer"
        )
    }
    
    /**
     * Provides recommendations for testing the search functionality.
     * 
     * @return List of testing recommendations
     */
    fun getTestingRecommendations(): List<String> {
        return listOf(
            "1. First run populateTestUsers() to create test data",
            "2. Wait 30-60 seconds for Firebase sync to complete", 
            "3. Test exact username matches (e.g., 'test_user_john_fit')",
            "4. Test partial username matches (e.g., 'john')",
            "5. Test display name searches (e.g., 'John Fitness')",
            "6. Test partial display name matches (e.g., 'Fitness')",
            "7. Test case-insensitive searches (e.g., 'JOHN' vs 'john')",
            "8. Test empty query handling",
            "9. Test queries with no results",
            "10. Verify search results include profile images and stats",
            "11. Test search caching by searching the same term twice",
            "12. Run cleanupTestUsers() when testing is complete"
        )
    }
}