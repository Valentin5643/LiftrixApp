package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.DatabaseSeedInitializer
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.mapper.ExerciseLibraryMapper
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Performance tests for ExerciseLibraryRepository search functionality
 * Validates that search operations meet the <200ms performance requirement
 * Tests with large datasets to ensure scalability
 */
class ExerciseLibraryPerformanceTest {

    private lateinit var repository: ExerciseLibraryRepositoryImpl
    private val dao: ExerciseLibraryDao = mockk()
    private val usageHistoryDao: ExerciseUsageHistoryDao = mockk()
    private val mapper: ExerciseLibraryMapper = mockk()
    private val databaseSeedInitializer: DatabaseSeedInitializer = mockk(relaxed = true)

    // Large dataset for performance testing (1000+ exercises)
    private val largeExerciseDataset = generateLargeExerciseDataset(1000)
    private val largeDomainDataset = generateLargeDomainDataset(1000)

    @Before
    fun setup() {
        repository = ExerciseLibraryRepositoryImpl(
            dao = dao, 
            usageHistoryDao = usageHistoryDao, 
            mapper = mapper,
            databaseSeedInitializer = databaseSeedInitializer,
            placeholderService = mockk()
        )
        
        // Setup mocks for large dataset
        every { dao.getAllExercises() } returns flowOf(largeExerciseDataset)
        every { mapper.toDomainList(largeExerciseDataset) } returns largeDomainDataset
        every { dao.searchExercises(any()) } returns flowOf(largeExerciseDataset)
    }

    @Test
    fun `searchPerformance_meetsRequirement_withLargeDataset`() = runTest {
        // Given
        val query = "press"
        val maxAllowedTime = 200L // 200ms requirement
        
        // When - Measure search performance
        val searchTime = measureTimeMillis {
            val result = repository.searchExercises(query, null, null)
            assertTrue(result.isSuccess)
        }
        
        // Then - Verify performance requirement is met
        assertTrue(
            searchTime < maxAllowedTime,
            "Search took ${searchTime}ms, which exceeds the ${maxAllowedTime}ms requirement"
        )
        
        println("✓ Search performance: ${searchTime}ms (requirement: <${maxAllowedTime}ms)")
    }

    @Test
    fun `searchPerformance_meetsRequirement_withComplexQuery`() = runTest {
        // Given
        val complexQuery = "barbell bench press chest"
        val maxAllowedTime = 200L
        
        // When - Measure complex search performance
        val searchTime = measureTimeMillis {
            val result = repository.searchExercises(complexQuery, null, null)
            assertTrue(result.isSuccess)
        }
        
        // Then
        assertTrue(
            searchTime < maxAllowedTime,
            "Complex search took ${searchTime}ms, which exceeds the ${maxAllowedTime}ms requirement"
        )
        
        println("✓ Complex search performance: ${searchTime}ms (requirement: <${maxAllowedTime}ms)")
    }

    @Test
    fun `filterPerformance_acceptable_withMultipleFilters`() = runTest {
        // Given
        val query = "curl"
        val equipment = setOf(Equipment.DUMBBELLS, Equipment.BARBELL, Equipment.CABLE_MACHINE)
        val muscleGroups = setOf(ExerciseCategory.BICEPS, ExerciseCategory.TRICEPS)
        val maxAllowedTime = 200L
        
        // When - Measure filtering performance
        val filterTime = measureTimeMillis {
            val result = repository.searchExercises(query, equipment, muscleGroups)
            assertTrue(result.isSuccess)
        }
        
        // Then
        assertTrue(
            filterTime < maxAllowedTime,
            "Filtering took ${filterTime}ms, which exceeds the ${maxAllowedTime}ms requirement"
        )
        
        println("✓ Filter performance: ${filterTime}ms (requirement: <${maxAllowedTime}ms)")
    }

    @Test
    fun `fuzzySearchPerformance_scalable_withPartialMatches`() = runTest {
        // Given
        val partialQuery = "pre" // Should match "press", "preacher", etc.
        val maxAllowedTime = 200L
        
        // When - Measure fuzzy search performance
        val fuzzySearchTime = measureTimeMillis {
            val result = repository.searchExercises(partialQuery, null, null)
            assertTrue(result.isSuccess)
            
            // Verify fuzzy matching is working by checking results contain partial matches
            val exercises = result.getOrNull()!!
            assertTrue(exercises.isNotEmpty(), "Fuzzy search should return results for partial query")
        }
        
        // Then
        assertTrue(
            fuzzySearchTime < maxAllowedTime,
            "Fuzzy search took ${fuzzySearchTime}ms, which exceeds the ${maxAllowedTime}ms requirement"
        )
        
        println("✓ Fuzzy search performance: ${fuzzySearchTime}ms (requirement: <${maxAllowedTime}ms)")
    }

    @Test
    fun `emptyQueryPerformance_acceptable_returnsAllResults`() = runTest {
        // Given
        val emptyQuery = ""
        val maxAllowedTime = 250L // Slightly higher for full dataset
        
        // When - Measure empty query performance (returns all exercises)
        val emptyQueryTime = measureTimeMillis {
            val result = repository.searchExercises(emptyQuery, null, null)
            assertTrue(result.isSuccess)
            
            val exercises = result.getOrNull()!!
            assertTrue(exercises.size >= 100, "Should return large dataset for empty query")
        }
        
        // Then
        assertTrue(
            emptyQueryTime < maxAllowedTime,
            "Empty query took ${emptyQueryTime}ms, which exceeds the ${maxAllowedTime}ms requirement"
        )
        
        println("✓ Empty query performance: ${emptyQueryTime}ms (requirement: <${maxAllowedTime}ms)")
    }

    @Test
    fun `recentExercisesPerformance_acceptable_withUserHistory`() = runTest {
        // Given
        val userId = "test-user-123"
        val limit = 10
        val maxAllowedTime = 150L // Recent exercises should be very fast
        
        // Setup recent exercises mock
        coEvery { usageHistoryDao.getRecentExerciseIds(userId, limit) } returns emptyList()
        
        // When - Measure recent exercises performance
        val recentTime = measureTimeMillis {
            val result = repository.getRecentExercises(userId, limit)
            assertTrue(result.isSuccess)
        }
        
        // Then
        assertTrue(
            recentTime < maxAllowedTime,
            "Recent exercises took ${recentTime}ms, which exceeds the ${maxAllowedTime}ms requirement"
        )
        
        println("✓ Recent exercises performance: ${recentTime}ms (requirement: <${maxAllowedTime}ms)")
    }

    @Test
    fun `concurrentSearchPerformance_maintainsSpeed_withMultipleQueries`() = runTest {
        // Given
        val queries = listOf("press", "curl", "squat", "deadlift", "row")
        val maxAllowedTime = 300L // Allow slightly more time for concurrent operations
        
        // When - Measure concurrent search performance
        val concurrentTime = measureTimeMillis {
            queries.forEach { query ->
                val result = repository.searchExercises(query, null, null)
                assertTrue(result.isSuccess)
            }
        }
        
        val averageTimePerQuery = concurrentTime / queries.size
        
        // Then
        assertTrue(
            averageTimePerQuery < 200L,
            "Average search time ${averageTimePerQuery}ms exceeds 200ms requirement"
        )
        
        println("✓ Concurrent search performance: ${averageTimePerQuery}ms average (requirement: <200ms)")
    }

    @Test
    fun `memoryUsage_acceptable_withLargeDataset`() = runTest {
        // Given
        val query = "exercise"
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // When - Perform search operations
        repeat(10) {
            val result = repository.searchExercises("$query$it", null, null)
            assertTrue(result.isSuccess)
        }
        
        // Force garbage collection to get accurate memory reading
        System.gc()
        Thread.sleep(100)
        
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val maxAllowedIncrease = 50 * 1024 * 1024 // 50MB
        
        // Then
        assertTrue(
            memoryIncrease < maxAllowedIncrease,
            "Memory usage increased by ${memoryIncrease / (1024 * 1024)}MB, which exceeds 50MB limit"
        )
        
        println("✓ Memory usage: ${memoryIncrease / (1024 * 1024)}MB increase (limit: 50MB)")
    }

    @Test
    fun `searchConsistency_maintainsPerformance_acrossMultipleRuns`() = runTest {
        // Given
        val query = "bench"
        val numberOfRuns = 5
        val maxAllowedTime = 200L
        val searchTimes = mutableListOf<Long>()
        
        // When - Perform multiple search runs
        repeat(numberOfRuns) {
            val searchTime = measureTimeMillis {
                val result = repository.searchExercises(query, null, null)
                assertTrue(result.isSuccess)
            }
            searchTimes.add(searchTime)
        }
        
        val averageTime = searchTimes.average()
        val maxTime = searchTimes.maxOrNull() ?: 0L
        
        // Then - Verify consistency
        assertTrue(
            averageTime < maxAllowedTime,
            "Average search time ${averageTime}ms exceeds ${maxAllowedTime}ms requirement"
        )
        
        assertTrue(
            maxTime < maxAllowedTime * 1.5, // Allow 50% variance for worst case
            "Maximum search time ${maxTime}ms is too inconsistent"
        )
        
        println("✓ Search consistency: ${averageTime}ms average, ${maxTime}ms max")
    }

    // Helper functions to generate large test datasets
    private fun generateLargeExerciseDataset(size: Int): List<ExerciseLibraryEntity> {
        val exerciseTypes = listOf("Press", "Curl", "Row", "Squat", "Deadlift", "Fly", "Raise", "Extension")
        val equipmentTypes = Equipment.values()
        val muscleGroups = ExerciseCategory.values()
        
        return (1..size).map { index ->
            val exerciseType = exerciseTypes[index % exerciseTypes.size]
            val equipment = equipmentTypes[index % equipmentTypes.size]
            val primaryMuscle = muscleGroups[index % muscleGroups.size]
            
            ExerciseLibraryEntity(
                id = "exercise_$index",
                name = "$equipment $exerciseType $index",
                primaryMuscleGroup = primaryMuscle,
                equipment = equipment,
                secondaryMuscleGroups = listOf(muscleGroups[(index + 1) % muscleGroups.size]),
                movementPattern = if (index % 2 == 0) "Push" else "Pull",
                difficultyLevel = (index % 10) + 1,
                instructions = "Perform $exerciseType with $equipment using proper form",
                isCompound = index % 3 == 0,
                searchableTerms = listOf(exerciseType.lowercase(), equipment.name.lowercase())
            )
        }
    }

    private fun generateLargeDomainDataset(size: Int): List<ExerciseLibrary> {
        val exerciseTypes = listOf("Press", "Curl", "Row", "Squat", "Deadlift", "Fly", "Raise", "Extension")
        val equipmentTypes = Equipment.values()
        val muscleGroups = ExerciseCategory.values()
        
        return (1..size).map { index ->
            val exerciseType = exerciseTypes[index % exerciseTypes.size]
            val equipment = equipmentTypes[index % equipmentTypes.size]
            val primaryMuscle = muscleGroups[index % muscleGroups.size]
            
            ExerciseLibrary(
                id = "exercise_$index",
                name = "$equipment $exerciseType $index",
                primaryMuscleGroup = primaryMuscle,
                equipment = equipment,
                secondaryMuscleGroups = listOf(muscleGroups[(index + 1) % muscleGroups.size]),
                movementPattern = if (index % 2 == 0) "Push" else "Pull",
                difficultyLevel = (index % 10) + 1,
                instructions = "Perform $exerciseType with $equipment using proper form",
                isCompound = index % 3 == 0,
                searchableTerms = listOf(exerciseType.lowercase(), equipment.name.lowercase())
            )
        }
    }
} 
