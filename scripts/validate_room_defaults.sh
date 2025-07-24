#!/bin/bash

# validate_room_defaults.sh
# Static validation script to prevent 'undefined' default values in Room entities

set -e

echo "🔍 Validating Room entity default values..."

# Find all Kotlin files with Room entities
# Check if we're in the root directory or app directory
if [ -d "app/src/main/java" ]; then
    SEARCH_PATH="app/src/main/java"
else
    SEARCH_PATH="src/main/java"
fi

ENTITY_FILES=$(find $SEARCH_PATH -name "*.kt" -type f | xargs grep -l "@Entity" 2>/dev/null || true)

if [ -z "$ENTITY_FILES" ]; then
    echo "✅ No Room entity files found to validate"
    exit 0
fi

VIOLATIONS_FOUND=0

echo "📁 Checking entity files for violations..."

for file in $ENTITY_FILES; do
    echo "   Checking: $file"
    
    # Check for 'undefined' in defaultValue annotations
    UNDEFINED_DEFAULTS=$(grep -n 'defaultValue.*=.*"undefined"' "$file" 2>/dev/null || true)
    UNDEFINED_DEFAULTS_SINGLE=$(grep -n "defaultValue.*=.*'undefined'" "$file" 2>/dev/null || true)
    
    if [ ! -z "$UNDEFINED_DEFAULTS" ] || [ ! -z "$UNDEFINED_DEFAULTS_SINGLE" ]; then
        echo "❌ VIOLATION: Found 'undefined' default value in $file"
        if [ ! -z "$UNDEFINED_DEFAULTS" ]; then
            echo "$UNDEFINED_DEFAULTS"
        fi
        if [ ! -z "$UNDEFINED_DEFAULTS_SINGLE" ]; then
            echo "$UNDEFINED_DEFAULTS_SINGLE"
        fi
        VIOLATIONS_FOUND=1
    fi
    
    # Check for other invalid Room default values
    INVALID_PATTERNS=(
        "defaultValue.*=.*\"null\""
        "defaultValue.*=.*'null'"
        "defaultValue.*=.*\"undefined\""
        "defaultValue.*=.*'undefined'"
        "defaultValue.*=.*\"\""
    )
    
    for pattern in "${INVALID_PATTERNS[@]}"; do
        MATCHES=$(grep -n "$pattern" "$file" 2>/dev/null || true)
        if [ ! -z "$MATCHES" ]; then
            echo "❌ VIOLATION: Invalid default value pattern '$pattern' in $file"
            echo "$MATCHES"
            VIOLATIONS_FOUND=1
        fi
    done
    
    # Check for proper Room defaults
    PROPER_DEFAULTS=$(grep -n "@ColumnInfo.*defaultValue" "$file" 2>/dev/null || true)
    if [ ! -z "$PROPER_DEFAULTS" ]; then
        # Validate that defaults are Room-compliant
        while IFS= read -r line; do
            if echo "$line" | grep -q 'defaultValue.*=.*"[0-9]\+"'; then
                echo "✅ Valid numeric default found"
            elif echo "$line" | grep -q 'defaultValue.*=.*"CURRENT_TIMESTAMP"'; then
                echo "✅ Valid timestamp default found"
            elif echo "$line" | grep -q 'defaultValue.*=.*"[01]"'; then
                echo "✅ Valid boolean default found"
            else
                echo "⚠️  WARNING: Potential invalid default value:"
                echo "    $line"
                echo "    Consider using: 0, 1, CURRENT_TIMESTAMP, or omit defaultValue for nullable fields"
            fi
        done <<< "$PROPER_DEFAULTS"
    fi
done

# Summary
if [ $VIOLATIONS_FOUND -eq 1 ]; then
    echo ""
    echo "❌ VALIDATION FAILED: Found Room entity violations!"
    echo ""
    echo "🔧 FIX GUIDE:"
    echo "   1. Remove defaultValue = \"undefined\" from @ColumnInfo annotations"
    echo "   2. Use proper Room defaults like:"
    echo "      - defaultValue = \"0\" for numeric fields"
    echo "      - defaultValue = \"1\" or \"0\" for boolean fields"
    echo "      - defaultValue = \"CURRENT_TIMESTAMP\" for timestamp fields"
    echo "   3. For nullable fields, omit defaultValue entirely"
    echo "   4. Ensure entity schema matches migration table creation"
    echo ""
    exit 1
else
    echo ""
    echo "✅ VALIDATION PASSED: No Room entity violations found!"
    echo "🎉 All Room entities have proper default values"
    echo ""
fi 