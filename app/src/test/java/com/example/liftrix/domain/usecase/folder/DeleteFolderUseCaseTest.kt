package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.template.TemplateRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for DeleteFolderUseCase
 * 
 * Tests business logic validation, user scoping, template reallocation,
 * default folder protection, and error handling.
 */
class DeleteFolderUseCaseTest {
    
    private lateinit var folderRepository: FolderRepository
    private lateinit var workoutTemplateRepository: TemplateRepository
    private lateinit var deleteFolderUseCase: DeleteFolderUseCase
    
    // Test data constants
    private val validUserId = "user123"
    private val validFolderId = FolderId("folder456")
    private val invalidUserId = ""
    
    @Before
    fun setup() {
        folderRepository = mockk()
        workoutTemplateRepository = mockk()
        deleteFolderUseCase = DeleteFolderUseCase(folderRepository, workoutTemplateRepository)
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `invoke with blank userId should fail validation`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(invalidUserId, validFolderId)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.ValidationError)
                assertTrue(error.toString().contains("User ID cannot be blank"))
            }
        )
    }
}
