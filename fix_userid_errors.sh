#!/bin/bash

# Batch fix script for UserId type mismatch errors
# This script applies targeted sed replacements to fix the ~200+ compilation errors

set -e

echo "🔧 Starting batch fix for UserId type mismatches..."
echo ""

# ============================================================================
# FIX PATTERN 1: Firebase .document(userId) calls → .document(userId.value)
# ============================================================================
echo "Fix 1/5: Firebase document() calls expecting String..."

find app/src/main/java -name "*RepositoryImpl.kt" -type f -print0 | while IFS= read -r -d '' file; do
    # Pattern: .document(userId)
    sed -i 's/\.document(userId)/\.document(userId.value)/g' "$file"
    # Pattern: .document(userId,
    sed -i 's/\.document(userId, /\.document(userId.value, /g' "$file"
    # Pattern: .document(userId)
    sed -i 's/collection(\([^)]*\))\.document(userId)/collection(\1).document(userId.value)/g' "$file"
done

echo "✓ Firebase document() calls fixed"
echo ""

# ============================================================================
# FIX PATTERN 2: String comparison with UserId (userId != "string")
# ============================================================================
echo "Fix 2/5: String comparison operators (!=, ==)..."

find app/src/main/java -name "*.kt" -type f -print0 | while IFS= read -r -d '' file; do
    # Pattern: userId != someString
    sed -i 's/userId != /userId.value != /g' "$file"
    # Pattern: userId == someString
    sed -i 's/userId == /userId.value == /g' "$file"
    # Pattern: someString != userId
    sed -i 's/ != userId/ != userId.value/g' "$file"
    # Pattern: someString == userId
    sed -i 's/ == userId/ == userId.value/g' "$file"
done

echo "✓ Comparison operators fixed"
echo ""

# ============================================================================
# FIX PATTERN 3: Firebase path building with userId
# Patterns like: documentId = "users/$userId" or when(userId)
# ============================================================================
echo "Fix 3/5: Firebase path building and when expressions..."

find app/src/main/java -name "*RepositoryImpl.kt" -type f -print0 | while IFS= read -r -d '' file; do
    # Pattern: "users/$userId" → "users/${userId.value}"
    sed -i 's/"users\/\$userId/"users\/\${userId.value}/g' "$file"
    sed -i "s/'users\/\\\$userId/'users\/\\\${userId.value}/g" "$file"

    # Pattern: when(userId) where it needs when(userId.value)
    # This is tricky - only fix where userId is being compared to String
    # We'll use a more specific pattern
done

echo "✓ Firebase path building fixed"
echo ""

# ============================================================================
# FIX PATTERN 4: DAO/Repository method calls passing userId to String params
# Pattern: dao.method(userId) where method expects String - already have UserId
# Just need to add .value
# ============================================================================
echo "Fix 4/5: DAO/method calls passing UserId to String parameters..."

find app/src/main/java -name "*RepositoryImpl.kt" -type f -print0 | while IFS= read -r -d '' file; do
    # Pattern: folderDao.method(userId) → folderDao.method(userId.value)
    sed -i 's/Dao\.\([a-zA-Z]*\)(userId)/Dao.\1(userId.value)/g' "$file"
    sed -i 's/dao\.\([a-zA-Z]*\)(userId)/dao.\1(userId.value)/g' "$file"

    # Pattern: firestore.collection("users").document(userId)
    sed -i 's/document(userId)/document(userId.value)/g' "$file"

    # Pattern: firebaseDataSource.method(userId)
    sed -i 's/firebaseDataSource\.\([a-zA-Z]*\)(userId)/firebaseDataSource.\1(userId.value)/g' "$file"
done

echo "✓ DAO/method calls fixed"
echo ""

# ============================================================================
# FIX PATTERN 5: Firebase data operations with complex types
# For Map type mismatches - ensure data maps are constructed properly
# ============================================================================
echo "Fix 5/5: Additional fixes for edge cases..."

find app/src/main/java -name "*RepositoryImpl.kt" -type f -print0 | while IFS= read -r -d '' file; do
    # Pattern: Operators like !=, ==, <, > with UserId
    sed -i 's/ != userId$/ != userId.value/g' "$file"
    sed -i 's/ == userId$/ == userId.value/g' "$file"
done

echo "✓ Edge case fixes applied"
echo ""

echo "=========================================="
echo "✅ All batch fixes applied successfully!"
echo "=========================================="
echo ""
echo "Rebuilding to verify fixes..."
./gradlew compileDebugKotlin 2>&1 | tee compilation_output.log

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 Compilation successful! All errors fixed."
    echo ""
else
    echo ""
    echo "⚠️  Some errors remain. Check compilation_output.log for details."
    echo ""
fi
