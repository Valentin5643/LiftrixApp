#!/bin/bash

# Database Schema Validation Script
# This script validates Room database schema to prevent migration failures

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "🔍 Room Database Schema Validation"
echo "=================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Check if adb is available
if ! command -v adb &> /dev/null; then
    print_status $RED "❌ ADB not found. Please install Android SDK tools."
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    print_status $RED "❌ No Android device connected. Please connect a device or start an emulator."
    exit 1
fi

print_status $BLUE "📱 Android device detected"

# Package name for the app
PACKAGE_NAME="com.example.liftrix"

# Check if app is installed
if ! adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
    print_status $YELLOW "⚠️  App not installed. Installing debug APK..."
    
    # Try to find and install the debug APK
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
    
    if [ -f "$APK_PATH" ]; then
        adb install -r "$APK_PATH"
        print_status $GREEN "✅ App installed successfully"
    else
        print_status $RED "❌ Debug APK not found. Please build the project first:"
        print_status $RED "   ./gradlew assembleDebug"
        exit 1
    fi
fi

# Function to run schema validation
validate_schema() {
    print_status $BLUE "🔍 Running schema validation..."
    
    # Create a temporary validation script
    local temp_script=$(mktemp)
    
    cat > "$temp_script" << 'EOF'
package com.example.liftrix.debug

import android.content.Context
import androidx.room.Room
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.SchemaValidator
import android.util.Log

class SchemaValidationRunner {
    fun validateCurrentSchema(context: Context): String {
        return try {
            val db = Room.databaseBuilder(
                context,
                LiftrixDatabase::class.java,
                "liftrix_database"
            ).build()
            
            val issues = SchemaValidator.validateSchema(db.openHelper.writableDatabase)
            
            if (issues.isEmpty()) {
                "✅ Schema validation passed - no issues found"
            } else {
                val issuesList = issues.joinToString("\n") { "❌ $it" }
                "⚠️ Schema validation found ${issues.size} issues:\n$issuesList"
            }
        } catch (e: Exception) {
            "❌ Schema validation failed: ${e.message}"
        }
    }
}
EOF

    # Run validation via ADB
    print_status $BLUE "📊 Checking current database schema..."
    
    # Use ADB to check database state
    local db_info=$(adb shell "run-as $PACKAGE_NAME find /data/data/$PACKAGE_NAME/databases -name '*.db' 2>/dev/null || echo 'No database found'")
    
    if echo "$db_info" | grep -q "liftrix_database"; then
        print_status $GREEN "✅ Database file found"
        
        # Check database integrity
        local integrity_check=$(adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database 'PRAGMA integrity_check;'" 2>/dev/null || echo "Error")
        
        if [ "$integrity_check" = "ok" ]; then
            print_status $GREEN "✅ Database integrity check passed"
        else
            print_status $RED "❌ Database integrity check failed: $integrity_check"
        fi
        
        # Check table schemas
        check_table_schemas
        
    else
        print_status $YELLOW "⚠️  Database not found - this is normal for a fresh installation"
    fi
    
    rm -f "$temp_script"
}

# Function to check specific table schemas
check_table_schemas() {
    print_status $BLUE "🏗️  Checking table schemas..."
    
    local tables=("workouts" "simple_workouts" "simple_exercises" "exercises" "exercise_sets")
    
    for table in "${tables[@]}"; do
        local schema=$(adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database 'PRAGMA table_info($table);'" 2>/dev/null)
        
        if [ -n "$schema" ]; then
            print_status $GREEN "✅ Table '$table' exists"
            
            # Check for common schema issues
            if echo "$schema" | grep -q "id|.*|INTEGER"; then
                if [ "$table" = "workouts" ] || [ "$table" = "simple_workouts" ]; then
                    print_status $RED "❌ Schema issue: $table.id should be TEXT, found INTEGER"
                fi
            fi
            
            if echo "$schema" | grep -q "created_at|.*|INTEGER"; then
                if [ "$table" = "workouts" ] || [ "$table" = "simple_workouts" ]; then
                    print_status $RED "❌ Schema issue: $table.created_at should be TEXT, found INTEGER"
                fi
            fi
            
        else
            print_status $YELLOW "⚠️  Table '$table' not found"
        fi
    done
}

# Function to check migration history
check_migration_history() {
    print_status $BLUE "📚 Checking migration history..."
    
    # Check Room's master table for version info
    local version=$(adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database 'PRAGMA user_version;'" 2>/dev/null || echo "0")
    
    print_status $BLUE "Current database version: $version"
    
    case $version in
        0)
            print_status $YELLOW "⚠️  Database version 0 - fresh installation or corrupted"
            ;;
        [1-7])
            print_status $YELLOW "⚠️  Database version $version - migration to version 10 required"
            ;;
        8|9)
            print_status $YELLOW "⚠️  Database version $version - repair migration to version 10 recommended"
            ;;
        10)
            print_status $GREEN "✅ Database version 10 - up to date"
            ;;
        *)
            print_status $RED "❌ Unknown database version: $version"
            ;;
    esac
}

# Function to generate schema report
generate_schema_report() {
    print_status $BLUE "📋 Generating schema report..."
    
    local report_file="$PROJECT_ROOT/schema_validation_report.txt"
    
    {
        echo "Room Database Schema Validation Report"
        echo "Generated on: $(date)"
        echo "========================================="
        echo ""
        
        echo "Database Version Check:"
        adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database 'PRAGMA user_version;'" 2>/dev/null || echo "Database not accessible"
        echo ""
        
        echo "Tables Present:"
        adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database \"SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';\"" 2>/dev/null || echo "No tables found"
        echo ""
        
        echo "Workouts Table Schema:"
        adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database 'PRAGMA table_info(workouts);'" 2>/dev/null || echo "Workouts table not found"
        echo ""
        
        echo "Simple Workouts Table Schema:"
        adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database 'PRAGMA table_info(simple_workouts);'" 2>/dev/null || echo "Simple workouts table not found"
        echo ""
        
        echo "Indices:"
        adb shell "run-as $PACKAGE_NAME sqlite3 /data/data/$PACKAGE_NAME/databases/liftrix_database \"SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'index_%';\"" 2>/dev/null || echo "No custom indices found"
        
    } > "$report_file"
    
    print_status $GREEN "✅ Schema report saved to: $report_file"
}

# Function to test migration
test_migration() {
    print_status $BLUE "🧪 Testing migration scenarios..."
    
    # Run migration tests
    cd "$PROJECT_ROOT"
    
    print_status $BLUE "Running migration unit tests..."
    ./gradlew test --tests "*Migration*Test*" || {
        print_status $RED "❌ Migration tests failed"
        return 1
    }
    
    print_status $BLUE "Running migration instrumentation tests..."
    ./gradlew connectedAndroidTest --tests "*Migration*Test*" || {
        print_status $YELLOW "⚠️  Some migration instrumentation tests failed"
        return 1
    }
    
    print_status $GREEN "✅ Migration tests passed"
}

# Main execution
main() {
    print_status $GREEN "🚀 Starting Room Database Schema Validation"
    echo ""
    
    # Validate current schema
    validate_schema
    echo ""
    
    # Check migration history
    check_migration_history
    echo ""
    
    # Generate report
    generate_schema_report
    echo ""
    
    # Optionally test migrations
    if [ "$1" = "--test-migrations" ]; then
        test_migration
        echo ""
    fi
    
    print_status $GREEN "🎉 Schema validation completed!"
    print_status $BLUE "💡 Next steps:"
    print_status $BLUE "   - Review the generated schema report"
    print_status $BLUE "   - Run migration tests: $0 --test-migrations"
    print_status $BLUE "   - Build and test the app with the new migrations"
}

# Show usage if help is requested
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Room Database Schema Validation Script"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --test-migrations    Run migration unit and instrumentation tests"
    echo "  --help, -h          Show this help message"
    echo ""
    echo "This script validates the Room database schema to prevent migration failures."
    exit 0
fi

# Run main function
main "$@" 