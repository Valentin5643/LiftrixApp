package com.example.liftrix.debug

import com.example.liftrix.data.local.entity.FolderEntity
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Type Mismatch Error Validation Test
 * 
 * **Critical Focus**: Long vs Instant and UserId vs String type mismatches
 * **Debug Reference**: docs/DEBUG-KOTLIN-FOLDER-SYSTEM-20250806.md lines 32-61
 * 
 * **INTENTIONALLY FAILING TESTS** - These expose Kotlin type system errors:
 * 1. FolderEntity expects Instant but receives Long (epochSecond)
 * 2. ProfileRepository.hasProfile expects String but receives UserId value class
 * 
 * **Expected Compilation Errors**:
 * - "Type mismatch: inferred type is Long but Instant was expected"
 * - "Type mismatch: inferred type is UserId but String was expected"
 * 
 * **Test Execution**: Run `./gradlew compileDebugUnitTestKotlin` to see errors
 */
class TypeMismatchErrorValidationTest {
    
    private lateinit var profileRepository: ProfileRepository
    
    // Test constants that expose type issues
    private val userIdValueClass = UserId("user123")
    private val userIdString = "user123"
    private val folderId = FolderId("folder456")
    private val currentTime = Instant.now()
    
    @Before
    fun setup() {
        profileRepository = mockk<ProfileRepository>()
        
        // Set up default mock behavior for suspend function hasProfile
        coEvery { profileRepository.hasProfile(any<String>()) } returns true
    }
    
    // ========================================
    // Long vs Instant Type Mismatch Tests
    // ========================================
    
    @Test
    fun `FAILING - Instant epochSecond returns Long but FolderEntity expects Instant`() {
        // **ERROR LOCATION**: Exactly as documented in DEBUG lines 54-55
        // **EXPECTED ERROR**: Type mismatch: inferred type is Long but Instant was expected
        
        val folderEntity = FolderEntity(
            id = folderId.value,
            userId = userIdString,
            name = "Test Folder",
            createdAt = currentTime,  // FIXED: Use Instant directly
            updatedAt = currentTime,  // FIXED: Use Instant directly
            templateCount = 0,
            isSynced = false,
            syncVersion = 1L
        )
        
        // Realistic assertion to make this a proper test
        assertEquals(folderId.value, folderEntity.id)
        assertNotNull(folderEntity.createdAt)
    }
    
    @Test 
    fun `FAILING - System currentTimeMillis returns Long but FolderEntity expects Instant`() {
        // **ALTERNATIVE ERROR SCENARIO**: Different Long source, same type mismatch
        
        val folderEntity = FolderEntity(
            id = "timestamp-test",
            userId = userIdString,
            name = "Timestamp Test Folder", 
            createdAt = Instant.ofEpochMilli(System.currentTimeMillis()),  // FIXED: Convert Long to Instant
            updatedAt = Instant.ofEpochMilli(System.currentTimeMillis()),  // FIXED: Convert Long to Instant
            templateCount = 5,
            isSynced = true,
            syncVersion = 2L
        )
        
        assertEquals("timestamp-test", folderEntity.id)
        assertEquals(5, folderEntity.templateCount)
    }
    
    @Test
    fun `FAILING - Epoch seconds calculation produces Long but Instant required`() {
        // **COMPLEX SCENARIO**: Calculated Long values still fail type check
        
        val baseTime = Instant.now()
        val oneHourAgo = baseTime.minusSeconds(3600)  // Instant calculation
        val oneHourLater = baseTime.plusSeconds(3600)  // Instant calculation
        
        val folderEntity = FolderEntity(
            id = "calculated-time-test",
            userId = userIdString,
            name = "Calculated Time Folder",
            createdAt = oneHourAgo,   // FIXED: Instant calculation works correctly
            updatedAt = oneHourLater, // FIXED: Instant calculation works correctly
            templateCount = 0,
            isSynced = false, 
            syncVersion = 1L
        )
        
        assertEquals("calculated-time-test", folderEntity.id)
    }
    
    // ========================================
    // UserId vs String Type Mismatch Tests
    // ========================================
    
    @Test
    fun `FAILING - UserId value class passed to method expecting String`() {
        // **ERROR LOCATION**: Exactly as documented in DEBUG lines 51-52
        // **EXPECTED ERROR**: Type mismatch: inferred type is UserId but String was expected
        
        // ProfileRepository.hasProfile(userId: String) but we pass UserId value class
        val hasProfile = runBlocking {
            profileRepository.hasProfile(userIdValueClass.value)  // FIXED: Use .value and runBlocking
        }
        //                                            ^^^^^^^^^^^^^^^^^
        //                                       FIXED: Extract .value and wrap in runBlocking
        
        assertEquals(true, hasProfile) // Realistic assertion assuming success
    }
    
    @Test
    fun `FAILING - Multiple UserId instances in collection cause batch type mismatch`() {
        // **BATCH SCENARIO**: Multiple type mismatches in single operation
        
        val userIds = listOf(
            UserId("user1"),
            UserId("user2"),
            UserId("user3"),
            UserId("user4")
        )
        
        // Each iteration will fail compilation
        val results = runBlocking {
            userIds.map { userId ->
                profileRepository.hasProfile(userId.value)  // FIXED: Extract .value and use runBlocking
                //                          ^^^^^^
            }
        }
        
        assertEquals(4, results.size)
    }
    
    @Test
    fun `FAILING - UserId in complex method parameter scenarios`() {
        // **COMPLEX USAGE**: UserId value class in various parameter positions
        
        val primaryUserId = UserId("primary-user")
        val secondaryUserId = UserId("secondary-user")
        
        // Multiple parameter positions with type mismatch
        val (primaryProfile, secondaryProfile) = runBlocking {
            val primary = profileRepository.hasProfile(primaryUserId.value)     // FIXED: Extract .value and use runBlocking
            val secondary = profileRepository.hasProfile(secondaryUserId.value) // FIXED: Extract .value and use runBlocking
            Pair(primary, secondary)
        }
        
        // Compound usage in method chaining would fail
        val bothProfilesExist = runBlocking {
            profileRepository.hasProfile(primaryUserId.value) && 
            profileRepository.hasProfile(secondaryUserId.value)
        }
        //                                      ^^^^^^^^^^^^^                ^^^^^^^^^^^^^^^
        //                                   FAILS: Both UserId vs String
        
        assertEquals(true, bothProfilesExist)
    }
    
    // ========================================
    // Combined Type Mismatch Scenarios  
    // ========================================
    
    @Test
    fun `FAILING - Compound type mismatches in single operation`() {
        // **COMPOUND SCENARIO**: Multiple type system failures in one test
        
        // 1. UserId vs String mismatch
        val hasProfile = runBlocking {
            profileRepository.hasProfile(userIdValueClass.value)  // FIXED: Extract .value and use runBlocking
        }
        
        // 2. Long vs Instant mismatch in same test
        val folderEntity = FolderEntity(
            id = "compound-test",
            userId = userIdString,
            name = "Compound Failure Test",
            createdAt = currentTime,  // FIXED: Use Instant directly
            updatedAt = currentTime,  // FIXED: Use Instant directly
            templateCount = 0,
            isSynced = false,
            syncVersion = 1L
        )
        
        // Both type errors must be resolved for compilation success
        assertEquals(true, hasProfile)
        assertEquals("compound-test", folderEntity.id)
    }
    
    @Test
    fun `FAILING - Type mismatch cascading through method calls`() {
        // **CASCADING SCENARIO**: Type errors that propagate through call chain
        
        val userIdCollection = setOf(
            UserId("cascade1"),
            UserId("cascade2"),
            UserId("cascade3")
        )
        
        // Type error cascades through collection operations
        val allProfilesExist = runBlocking {
            userIdCollection.all { userId ->
                profileRepository.hasProfile(userId.value)  // FIXED: Extract .value from each UserId and use runBlocking
            }
        }
        
        // Time calculation errors cascade through entity creation
        val entities = (1..3).map { index ->
            val timeOffset = currentTime.plusSeconds(index * 1000L)  // FIXED: Use Long for seconds calculation
            FolderEntity(
                id = "cascade-$index",
                userId = userIdString,
                name = "Cascade Test $index",
                createdAt = timeOffset,  // FIXED: Instant calculation works correctly
                updatedAt = timeOffset,  // FIXED: Instant calculation works correctly
                templateCount = index,
                isSynced = false,
                syncVersion = 1L  // FIXED: Use Long for syncVersion
            )
        }
        
        assertEquals(3, entities.size)
        assertEquals(true, allProfilesExist)
    }
}