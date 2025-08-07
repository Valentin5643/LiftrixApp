# Folder System Failing Test Suite - README

## Overview
This directory contains **intentionally failing tests** designed to expose and validate compilation errors identified in `docs/DEBUG-KOTLIN-FOLDER-SYSTEM-20250806.md`.

**CRITICAL**: These tests are NOT meant to pass. They serve as systematic validation that the documented bugs exist and provide a TDD framework for implementing fixes.

## Test Files

### 1. FolderSystemFailingTestSuite.kt
**Primary failing test suite** - Comprehensive coverage of all error categories
- 8 test methods exposing 28+ compilation errors
- Integration scenarios combining multiple error types
- Realistic test data and proper assertions
- Edge case stress testing with compound failures

### 2. CompilationErrorValidationFramework.kt  
**Validation and tracking framework** - Systematic error analysis
- Error categorization with 4 main types
- 9 documented compilation errors with expected messages
- TDD protocol validation for systematic fixes
- Quality assurance and integration testing framework

### 3. TypeMismatchErrorValidationTest.kt
**Focused type system error validation** - Long vs Instant, UserId vs String
- 6 test methods with specific type mismatch scenarios  
- Kotlin type system correctness validation
- Value class integration error reproduction
- Cascading type error propagation testing

### 4. UnresolvedReferenceErrorValidationTest.kt
**Missing API element validation** - DAO methods and domain properties
- 6 test methods covering unresolved references
- Missing WorkoutTemplateDao methods (updateFolderId, etc.)
- Missing domain model properties (exerciseCount, estimatedDuration, value)
- Realistic folder management workflow scenarios

## Compilation Error Validation

### Expected Errors (Verified ✅)
Based on `./gradlew :app:compileDebugUnitTestKotlin` execution:

#### Type Mismatch Errors
```
e: Argument type mismatch: actual type is 'kotlin.Long', but 'java.time.Instant' was expected.
e: Argument type mismatch: actual type is 'com.example.liftrix.domain.model.UserId', but 'kotlin.String' was expected.
```

#### Unresolved Reference Errors  
```
e: Unresolved reference 'updateFolderId'.
e: Unresolved reference 'exerciseCount'.
e: Unresolved reference 'estimatedDuration'.  
e: Unresolved reference 'value'.
```

#### Type Inference Errors
```
e: Cannot infer type for this parameter. Please specify it explicitly.
```

### Error Distribution
- **FolderRepositoryImplIntegrationTest.kt**: 6 errors (lines 54, 55, 334, 335)
- **CompilationErrorValidationTest.kt**: 4 errors (lines 123, 133, 143, 144)  
- **FolderSystemFailingTestSuite.kt**: 18+ errors across all test methods
- **TypeMismatchErrorValidationTest.kt**: Multiple type system errors
- **UnresolvedReferenceErrorValidationTest.kt**: Multiple API reference errors

## Test Execution Guide

### Validate Compilation Errors
```bash
# Compile all tests to see full error list
./gradlew :app:compileDebugUnitTestKotlin

# Expected result: BUILD FAILED with 28+ compilation errors
# This confirms the bugs exist and tests are working correctly
```

### Individual Test File Analysis
```bash  
# Focus on specific error categories using file isolation
# (Note: All will fail compilation, but isolates specific patterns)

# Type system errors
# Examine: TypeMismatchErrorValidationTest.kt

# Missing API errors  
# Examine: UnresolvedReferenceErrorValidationTest.kt

# Comprehensive scenarios
# Examine: FolderSystemFailingTestSuite.kt
```

## TDD Fix Protocol

### Phase 1: Error Reproduction ✅ COMPLETE
All documented compilation errors successfully reproduced with realistic test scenarios.

### Phase 2: Systematic Fixes (NEXT)
**DO NOT FIX COMPILATION ERRORS YET** - These tests validate bug existence.

When ready to implement fixes:

1. **Type Mismatch Fixes**:
   ```kotlin
   // Before (failing)
   createdAt = currentTime.epochSecond,
   
   // After (working) 
   createdAt = currentTime,
   ```

2. **UserId Integration Fixes**:
   ```kotlin
   // Before (failing)
   profileRepository.hasProfile(validUserId)
   
   // After (working)
   profileRepository.hasProfile(validUserId.value)
   ```

3. **Missing DAO Method Fixes**:
   ```kotlin
   @Query("UPDATE workout_templates SET folder_id = :folderId WHERE id = :templateId AND user_id = :userId")
   suspend fun updateFolderId(templateId: String, folderId: String, userId: String): Int
   ```

### Phase 3: Validation (POST-FIX)
After implementing fixes:
1. All tests should compile successfully
2. Meaningful assertions should pass
3. No regression in existing functionality
4. Integration tests validate end-to-end workflows

## Quality Assurance

### Test Structure Validation
- ✅ Follows existing Kotlin/Android testing patterns
- ✅ Proper MockK integration with `coEvery`/`coVerify`  
- ✅ Realistic assertions with meaningful expected values
- ✅ Proper coroutine testing with `runTest`
- ✅ Integration with existing test suite structure

### Systematic Error Coverage
- ✅ All 9 documented compilation errors reproduced
- ✅ Realistic test scenarios with proper domain objects
- ✅ Integration scenarios combining multiple error types
- ✅ Edge cases and stress testing with compound failures
- ✅ Clear documentation linking tests to DEBUG analysis

### TDD Compliance
- ✅ Each error has dedicated test validation
- ✅ Clear fix protocol for each error category  
- ✅ Independent error resolution (no circular dependencies)
- ✅ Systematic validation framework for post-fix testing

## Integration Notes

### Existing Test Suite Compatibility
- Tests integrate seamlessly with existing structure
- Follow established naming conventions and patterns
- Use existing dependency injection and mock setup
- Maintain compatibility with CI/CD pipeline

### Debug Document Linkage
- All tests reference specific lines from `docs/DEBUG-KOTLIN-FOLDER-SYSTEM-20250806.md`
- Error messages match documented expectations
- Systematic categorization aligns with root cause analysis
- TDD protocol follows documented fix strategies

---

## Usage Summary

**Current Status**: Tests intentionally fail with documented compilation errors
**Purpose**: Validate bug existence and provide TDD framework for fixes
**Next Action**: Implement systematic fixes using documented protocols
**Success Criteria**: All tests compile and pass after fixes are applied

**Note**: Do not attempt to fix compilation errors until systematic fix protocol is initiated. These failing tests are the foundation for proper TDD implementation of the folder system bug fixes.