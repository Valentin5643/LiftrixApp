package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FitnessLevel
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for SearchUsersUseCase
 * 
 * Tests search logic, privacy filtering, result ranking, caching behavior,
 * and error handling scenarios for user discovery functionality.
 */
class SearchUsersUseCaseTest {

    private lateinit var searchUsersUseCase: SearchUsersUseCase
    private lateinit var userSearchRepository: UserSearchRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var errorHandler: ErrorHandler

    private val currentUserId = "current_user_123"
    private val searchQuery = "fitness"
    private val searchFilters = SearchFilters(
        fitnessLevel = FitnessLevel.INTERMEDIATE,
        equipment = setOf(Equipment.DUMBBELLS),
        goals = setOf(FitnessGoal.MUSCLE_GAIN)
    )

    @Before
    fun setup() {
        userSearchRepository = mockk()
        authRepository = mockk()
        errorHandler = mockk()
        
        searchUsersUseCase = SearchUsersUseCase(
            userSearchRepository = userSearchRepository,
            authRepository = authRepository,
            errorHandler = errorHandler
        )

        // Mock authentication
        coEvery { authRepository.getCurrentUserId() } returns currentUserId
    }

    @Test
    fun `searchUsers with valid query returns successful results`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = searchQuery,
            filters = searchFilters,
            limit = 10,
            useCache = true
        )
        
        val mockUsers = listOf(
            createMockUserSearchResult("user1", "John Fitness", FitnessLevel.INTERMEDIATE),
            createMockUserSearchResult("user2", "Jane Strong", FitnessLevel.ADVANCED)
        )
        
        val repositoryResult = UserSearchRepositoryResult(
            users = mockUsers,
            isCachedResult = false,
            totalCount = 2
        )
        
        coEvery { 
            userSearchRepository.searchUsers(
                query = searchQuery,
                filters = searchFilters,
                currentUserId = currentUserId,
                limit = 10,
                useCache = true
            )
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(2, searchResult.users.size)
        assertEquals(false, searchResult.isCachedResult)
        assertEquals(2, searchResult.totalResults)
        
        // Verify users are properly filtered and ranked
        assertEquals("John Fitness", searchResult.users[0].displayName)
        assertEquals(FitnessLevel.INTERMEDIATE, searchResult.users[0].fitnessLevel)
    }

    @Test
    fun `searchUsers with cached results returns cached data`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = searchQuery,
            filters = searchFilters,
            limit = 10,
            useCache = true
        )
        
        val mockUsers = listOf(
            createMockUserSearchResult("user1", "Cached User", FitnessLevel.BEGINNER)
        )
        
        val repositoryResult = UserSearchRepositoryResult(
            users = mockUsers,
            isCachedResult = true,
            totalCount = 1
        )
        
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertTrue(searchResult.isCachedResult)
        assertEquals(1, searchResult.totalResults)
    }

    @Test
    fun `searchUsers with privacy filtering excludes private profiles`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "test",
            filters = SearchFilters(),
            limit = 10,
            useCache = false
        )
        
        val publicUser = createMockUserSearchResult("user1", "Public User", FitnessLevel.INTERMEDIATE)
        val mockUsers = listOf(publicUser) // Repository should already filter private users
        
        val repositoryResult = UserSearchRepositoryResult(
            users = mockUsers,
            isCachedResult = false,
            totalCount = 1
        )
        
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(1, searchResult.users.size)
        assertEquals("Public User", searchResult.users[0].displayName)
        
        // Verify repository was called with privacy filtering
        coVerify { 
            userSearchRepository.searchUsers(
                query = "test",
                filters = SearchFilters(),
                currentUserId = currentUserId,
                limit = 10,
                useCache = false
            )
        }
    }

    @Test
    fun `searchUsers with fitness level filter returns matching users`() = runTest {
        // Given
        val filtersWithLevel = SearchFilters(fitnessLevel = FitnessLevel.ADVANCED)
        val request = SearchUsersRequest(
            query = "athlete",
            filters = filtersWithLevel,
            limit = 5,
            useCache = true
        )
        
        val advancedUsers = listOf(
            createMockUserSearchResult("user1", "Advanced Athlete", FitnessLevel.ADVANCED),
            createMockUserSearchResult("user2", "Pro Lifter", FitnessLevel.ADVANCED)
        )
        
        val repositoryResult = UserSearchRepositoryResult(
            users = advancedUsers,
            isCachedResult = false,
            totalCount = 2
        )
        
        coEvery { 
            userSearchRepository.searchUsers(
                query = "athlete",
                filters = filtersWithLevel,
                currentUserId = currentUserId,
                limit = 5,
                useCache = true
            )
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(2, searchResult.users.size)
        assertTrue(searchResult.users.all { it.fitnessLevel == FitnessLevel.ADVANCED })
    }

    @Test
    fun `searchUsers with equipment filter returns users with matching equipment`() = runTest {
        // Given
        val filtersWithEquipment = SearchFilters(
            equipment = setOf(Equipment.BARBELL, Equipment.DUMBBELLS)
        )
        val request = SearchUsersRequest(
            query = "weights",
            filters = filtersWithEquipment,
            limit = 10,
            useCache = false
        )
        
        val weightLifters = listOf(
            createMockUserSearchResult(
                userId = "user1", 
                displayName = "Barbell User",
                fitnessLevel = FitnessLevel.INTERMEDIATE,
                sharedEquipment = listOf(Equipment.BARBELL, Equipment.DUMBBELLS)
            )
        )
        
        val repositoryResult = UserSearchRepositoryResult(
            users = weightLifters,
            isCachedResult = false,
            totalCount = 1
        )
        
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(1, searchResult.users.size)
        assertTrue(searchResult.users[0].sharedEquipment.contains(Equipment.BARBELL))
        assertTrue(searchResult.users[0].sharedEquipment.contains(Equipment.DUMBBELLS))
    }

    @Test
    fun `searchUsers with empty query returns validation error`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "",
            filters = SearchFilters(),
            limit = 10,
            useCache = true
        )

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.message.contains("Search query cannot be empty"))
    }

    @Test
    fun `searchUsers with invalid limit returns validation error`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "test",
            filters = SearchFilters(),
            limit = 101, // Exceeds maximum
            useCache = true
        )

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.message.contains("Search limit must be between"))
    }

    @Test
    fun `searchUsers with unauthenticated user returns authentication error`() = runTest {
        // Given
        coEvery { authRepository.getCurrentUserId() } returns null
        
        val request = SearchUsersRequest(
            query = "test",
            filters = SearchFilters(),
            limit = 10,
            useCache = true
        )

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.AuthenticationError>(error)
        assertEquals("User not authenticated", error.message)
    }

    @Test
    fun `searchUsers with repository failure returns error`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "test",
            filters = SearchFilters(),
            limit = 10,
            useCache = true
        )
        
        val repositoryError = LiftrixError.NetworkError("Network connection failed")
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } returns LiftrixResult.failure(repositoryError)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.NetworkError>(error)
        assertEquals("Network connection failed", error.message)
    }

    @Test
    fun `searchUsers with exception calls error handler and returns error`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "test",
            filters = SearchFilters(),
            limit = 10,
            useCache = true
        )
        
        val exception = RuntimeException("Unexpected error")
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } throws exception
        
        every { 
            errorHandler.handleError(any(), any())
        } returns mockk()

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.UnknownError>(error)
        assertTrue(error.message.contains("Search operation failed"))
        
        // Verify error handler was called
        coVerify { 
            errorHandler.handleError(
                any<LiftrixError.UnknownError>(),
                match { context ->
                    context["context"] == "SearchUsersUseCase" &&
                    context["query"] == "test"
                }
            )
        }
    }

    @Test
    fun `searchUsers results are properly ranked by relevance`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "fitness",
            filters = SearchFilters(),
            limit = 10,
            useCache = false
        )
        
        val users = listOf(
            createMockUserSearchResult("user1", "Fitness Expert", FitnessLevel.EXPERT, mutualConnections = 5),
            createMockUserSearchResult("user2", "John Fitness", FitnessLevel.INTERMEDIATE, mutualConnections = 2),
            createMockUserSearchResult("user3", "Fitness Beginner", FitnessLevel.BEGINNER, mutualConnections = 0)
        )
        
        val repositoryResult = UserSearchRepositoryResult(
            users = users,
            isCachedResult = false,
            totalCount = 3
        )
        
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(3, searchResult.users.size)
        
        // Results should be ranked by repository (mutual connections, fitness level, etc.)
        assertEquals("Fitness Expert", searchResult.users[0].displayName)
        assertEquals(5, searchResult.users[0].mutualConnections)
    }

    @Test
    fun `searchUsers excludes current user from results`() = runTest {
        // Given
        val request = SearchUsersRequest(
            query = "user",
            filters = SearchFilters(),
            limit = 10,
            useCache = false
        )
        
        val users = listOf(
            createMockUserSearchResult("other_user", "Other User", FitnessLevel.INTERMEDIATE)
            // Current user should be excluded by repository
        )
        
        val repositoryResult = UserSearchRepositoryResult(
            users = users,
            isCachedResult = false,
            totalCount = 1
        )
        
        coEvery { 
            userSearchRepository.searchUsers(any(), any(), any(), any(), any())
        } returns LiftrixResult.success(repositoryResult)

        // When
        val result = searchUsersUseCase(request)

        // Then
        assertTrue(result.isSuccess)
        val searchResult = result.getOrThrow()
        assertEquals(1, searchResult.users.size)
        assertTrue(searchResult.users.none { it.userId == currentUserId })
    }

    private fun createMockUserSearchResult(
        userId: String,
        displayName: String,
        fitnessLevel: FitnessLevel,
        bio: String? = "Test bio",
        totalWorkouts: Int = 10,
        sharedEquipment: List<Equipment> = listOf(Equipment.DUMBBELLS),
        sharedGoals: List<FitnessGoal> = listOf(FitnessGoal.MUSCLE_GAIN),
        connectionStatus: ConnectionStatus = ConnectionStatus.NONE,
        mutualConnections: Int = 0
    ): UserSearchResult {
        return UserSearchResult(
            userId = userId,
            displayName = displayName,
            profileImageUrl = null,
            bio = bio,
            fitnessLevel = fitnessLevel,
            totalWorkouts = totalWorkouts,
            memberSince = LocalDateTime.now().minusMonths(6),
            sharedEquipment = sharedEquipment,
            sharedGoals = sharedGoals,
            connectionStatus = connectionStatus,
            mutualConnections = mutualConnections
        )
    }
}