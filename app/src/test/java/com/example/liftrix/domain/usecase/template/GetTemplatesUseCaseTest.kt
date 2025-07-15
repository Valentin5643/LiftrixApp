package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetTemplatesUseCaseTest {
    
    private lateinit var templateRepository: TemplateRepository
    private lateinit var errorHandler: ErrorHandler
    private lateinit var getTemplatesUseCase: GetTemplatesUseCase
    
    @Before
    fun setup() {
        templateRepository = mockk()
        errorHandler = mockk()
        getTemplatesUseCase = GetTemplatesUseCase(templateRepository, errorHandler)
    }
    
    @Test
    fun `when valid request provided, should return templates successfully`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123")
        val templates = listOf(
            mockk<WorkoutTemplate>(),
            mockk<WorkoutTemplate>()
        )
        
        coEvery { templateRepository.getRecentlyUsedTemplates("user123", 20) } returns flowOf(LiftrixResult.success(templates))
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isSuccess)
        val templateResult = result.getOrNull()!!
        assertEquals(templates, templateResult.templates)
        assertEquals(2, templateResult.totalCount)
        coVerify { templateRepository.getRecentlyUsedTemplates("user123", 20) }
    }
    
    @Test
    fun `when user ID is blank, should return validation error`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "")
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("User ID is required") })
    }
    
    @Test
    fun `when search query is too short, should return validation error`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123", searchQuery = "a")
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("must be at least 2 characters") })
    }
    
    @Test
    fun `when difficulty level is invalid, should return validation error`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123", difficultyLevel = 6)
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("must be between 1 and 5") })
    }
    
    @Test
    fun `when limit is zero, should return validation error`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123", limit = 0)
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.ValidationError>(error)
        assertTrue(error.violations.any { it.contains("must be greater than 0") })
    }
    
    @Test
    fun `when sortBy is MOST_USED, should call most used templates repository method`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123", sortBy = TemplateSortBy.MOST_USED)
        val templates = listOf(mockk<WorkoutTemplate>())
        
        coEvery { templateRepository.getMostUsedTemplates("user123", 20) } returns flowOf(LiftrixResult.success(templates))
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isSuccess)
        coVerify { templateRepository.getMostUsedTemplates("user123", 20) }
    }
    
    @Test
    fun `when sortBy is ALPHABETICAL, should call templates by user and sort`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123", sortBy = TemplateSortBy.ALPHABETICAL)
        val templates = listOf(mockk<WorkoutTemplate>())
        
        coEvery { templateRepository.getTemplatesByUser("user123") } returns flowOf(LiftrixResult.success(templates))
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isSuccess)
        coVerify { templateRepository.getTemplatesByUser("user123") }
    }
    
    @Test
    fun `when repository returns error, should propagate error`() = runTest {
        // Given
        val request = GetTemplatesRequest(userId = "user123")
        val repositoryError = LiftrixError.DatabaseError(errorMessage = "Database connection failed")
        
        coEvery { templateRepository.getRecentlyUsedTemplates("user123", 20) } returns flowOf(liftrixFailure(repositoryError))
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isFailure)
        assertEquals(repositoryError, result.exceptionOrNull())
    }
    
    @Test
    fun `when applied filters are set correctly`() = runTest {
        // Given
        val request = GetTemplatesRequest(
            userId = "user123",
            searchQuery = "push",
            folderId = "folder123",
            difficultyLevel = 3
        )
        val templates = listOf(mockk<WorkoutTemplate>())
        
        coEvery { templateRepository.getTemplatesByUser("user123") } returns flowOf(LiftrixResult.success(templates))
        coEvery { templateRepository.searchTemplates("user123", "push") } returns LiftrixResult.success(templates)
        
        // When
        val resultFlow = getTemplatesUseCase(request)
        val results = resultFlow.toList()
        
        // Then
        assertEquals(1, results.size)
        val result = results.first()
        assertTrue(result.isSuccess)
        val templateResult = result.getOrNull()!!
        val filters = templateResult.appliedFilters
        assertEquals("push", filters.searchQuery)
        assertEquals("folder123", filters.folderId)
        assertEquals(3, filters.difficultyLevel)
        assertTrue(filters.hasFilters)
    }
}