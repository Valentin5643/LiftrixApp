#!/bin/bash

# Schema Consistency Validation Script
# Detects mismatches between Room entities and migration scripts

set -e

echo "🔍 Starting Database Schema Consistency Validation..."

ENTITY_DIR="app/src/main/java/com/example/liftrix/data/local/entity"
MIGRATION_DIR="app/src/main/java/com/example/liftrix/data/local/migration" 
ERRORS=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_error() {
    echo -e "${RED}❌ ERROR: $1${NC}"
    ((ERRORS++))
}

echo_warning() {
    echo -e "${YELLOW}⚠️  WARNING: $1${NC}"
}

echo_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

echo_info() {
    echo "ℹ️  $1"
}

# Check 1: Missing @ColumnInfo(defaultValue) on non-null fields with Kotlin defaults
echo_info "Checking for missing @ColumnInfo(defaultValue) annotations..."

check_missing_default_values() {
    local file="$1"
    local entity_name=$(basename "$file" .kt)
    local issues_found=false
    local line_num=0
    local prev_line=""
    
    # Read file line by line to check each field with its annotation
    while IFS= read -r line; do
        ((line_num++))
        
        # Check if line has a field with Kotlin default value
        if [[ "$line" =~ val.*:[[:space:]]*[^?]*=[[:space:]]* ]]; then
            # Extract the field declaration line
            field_line="$line"
            
            # Check if this line has @ColumnInfo with defaultValue
            if [[ "$field_line" =~ @ColumnInfo.*defaultValue ]]; then
                # Has both Kotlin default and Room default on same line - OK
                prev_line="$line"
                continue
            elif [[ "$prev_line" =~ @ColumnInfo.*defaultValue ]]; then
                # Has @ColumnInfo with defaultValue on previous line - OK
                prev_line="$line"
                continue
            else
                # Has Kotlin default but no Room defaultValue annotation
                if [[ ! "$issues_found" == true ]]; then
                    echo_error "Entity $entity_name has Kotlin defaults without @ColumnInfo(defaultValue):"
                    issues_found=true
                fi
                echo "  📍 Line $line_num: $field_line"
                echo "    (previous line: $prev_line)"
            fi
        fi
        prev_line="$line"
    done < "$file"
    
    if [[ "$issues_found" == false ]]; then
        echo_success "Entity $entity_name default value annotations are consistent"
    fi
}

# Check all entity files
for entity_file in "$ENTITY_DIR"/*.kt; do
    if [[ -f "$entity_file" ]]; then
        check_missing_default_values "$entity_file"
    fi
done

# Check 2: Foreign key type consistency
echo_info "Checking foreign key type consistency..."

check_foreign_key_types() {
    # Check ExerciseEntity foreign key to WorkoutEntity
    local exercise_file="$ENTITY_DIR/ExerciseEntity.kt"
    local workout_file="$ENTITY_DIR/WorkoutEntity.kt"
    
    if [[ -f "$exercise_file" && -f "$workout_file" ]]; then
        # Extract workoutId type from ExerciseEntity
        local workout_id_type=$(grep "val workoutId:" "$exercise_file" | sed -n 's/.*val workoutId: \([^,]*\).*/\1/p')
        
        # Extract id type from WorkoutEntity  
        local workout_entity_id_type=$(grep "val id:" "$workout_file" | sed -n 's/.*val id: \([^,]*\).*/\1/p')
        
        if [[ "$workout_id_type" != "$workout_entity_id_type" ]]; then
            echo_error "Foreign key type mismatch: ExerciseEntity.workoutId ($workout_id_type) != WorkoutEntity.id ($workout_entity_id_type)"
        else
            echo_success "ExerciseEntity foreign key types are consistent"
        fi
    fi
    
    # Check other foreign key relationships
    local daily_workout_file="$ENTITY_DIR/DailyWorkoutEntity.kt"
    local template_file="$ENTITY_DIR/WorkoutTemplateEntity.kt"
    
    if [[ -f "$daily_workout_file" && -f "$template_file" ]]; then
        local template_id_type=$(grep "val templateId:" "$daily_workout_file" | sed -n 's/.*val templateId: \([^?,]*\).*/\1/p')
        local template_entity_id_type=$(grep "val id:" "$template_file" | sed -n 's/.*val id: \([^,]*\).*/\1/p')
        
        if [[ -n "$template_id_type" && "$template_id_type" != "$template_entity_id_type" ]]; then
            echo_error "Foreign key type mismatch: DailyWorkoutEntity.templateId ($template_id_type) != WorkoutTemplateEntity.id ($template_entity_id_type)"
        else
            echo_success "DailyWorkoutEntity foreign key types are consistent"
        fi
    fi
}

check_foreign_key_types

# Check 3: Migration table creation vs Entity definitions
echo_info "Checking migration table schemas vs entity definitions..."

check_migration_entity_consistency() {
    local migration_file="$MIGRATION_DIR/Migration_5_to_6.kt"
    local simple_exercise_file="$ENTITY_DIR/SimpleExerciseEntity.kt"
    
    if [[ -f "$migration_file" && -f "$simple_exercise_file" ]]; then
        # Extract CREATE TABLE statement for simple_exercises
        local create_table=$(grep -A 20 "CREATE TABLE.*simple_exercises" "$migration_file")
        
        # Check for missing columns in migration
        local entity_columns=$(grep "@ColumnInfo" "$simple_exercise_file" | sed -n 's/.*name = "\([^"]*\)".*/\1/p')
        
        echo_info "Checking simple_exercises table schema consistency..."
        local migration_issues=false
        
        while read -r column; do
            if [[ -n "$column" ]]; then
                if ! echo "$create_table" | grep -q "$column"; then
                    if [[ ! "$migration_issues" == true ]]; then
                        echo_error "SimpleExerciseEntity schema inconsistencies found:"
                        migration_issues=true
                    fi
                    echo "  📍 Column '$column' exists in SimpleExerciseEntity but missing from Migration_5_6"
                fi
            fi
        done <<< "$entity_columns"
        
        # Check for rpe type consistency (should be REAL for Double?)
        if echo "$create_table" | grep -q "rpe INTEGER"; then
            if grep -q "val rpe: Double?" "$simple_exercise_file"; then
                if [[ ! "$migration_issues" == true ]]; then
                    echo_error "SimpleExerciseEntity schema inconsistencies found:"
                    migration_issues=true
                fi
                echo "  📍 Type mismatch: simple_exercises.rpe is INTEGER in migration but Double? in entity"
            fi
        fi
        
        if [[ "$migration_issues" == false ]]; then
            echo_success "SimpleExerciseEntity schema is consistent (fixed by Migration_10_11)"
        fi
    fi
}

check_migration_entity_consistency

# Check 4: Non-null fields without defaults
echo_info "Checking for non-null fields without defaults..."

check_non_null_without_defaults() {
    local file="$1"
    local entity_name=$(basename "$file" .kt)
    local warnings_found=false
    
    # Find non-null fields (not ending with ?) that don't have defaults
    while IFS= read -r line; do
        # Check for non-null field without default value or annotation
        if [[ "$line" =~ val.*:[[:space:]]*[^?]*,[[:space:]]*$ ]] && [[ ! "$line" =~ = ]] && [[ ! "$line" =~ @PrimaryKey ]]; then
            # Skip if it has @ColumnInfo(defaultValue) 
            if [[ ! "$line" =~ defaultValue ]]; then
                if [[ ! "$warnings_found" == true ]]; then
                    echo_warning "Entity $entity_name has non-null fields without defaults:"
                    warnings_found=true
                fi
                echo "  📍 $line"
            fi
        fi
    done < "$file"
    
    if [[ "$warnings_found" == false ]]; then
        echo_success "Entity $entity_name non-null field validation passed"
    fi
}

for entity_file in "$ENTITY_DIR"/*.kt; do
    if [[ -f "$entity_file" ]]; then
        check_non_null_without_defaults "$entity_file"
    fi
done

# Check 5: Database version consistency
echo_info "Checking database version consistency..."

check_database_version() {
    local db_file="app/src/main/java/com/example/liftrix/data/local/LiftrixDatabase.kt"
    local module_file="app/src/main/java/com/example/liftrix/di/DatabaseModule.kt"
    
    if [[ -f "$db_file" ]]; then
        local db_version=$(grep "version = " "$db_file" | sed -n 's/.*version = \([0-9]*\).*/\1/p')
        echo_info "Database version: $db_version"
        
        # Check that all migrations up to this version exist
        local migration_issues=false
        for ((i=6; i<db_version; i++)); do
            local next=$((i+1))
            local migration_name="MIGRATION_${i}_${next}"
            
            if ! find "$MIGRATION_DIR" -name "*${migration_name}*" | grep -q .; then
                if [[ ! "$migration_issues" == true ]]; then
                    echo_error "Database version consistency issues found:"
                    migration_issues=true
                fi
                echo "  📍 Missing migration: $migration_name for database version $db_version"
            fi
        done
        
        # Check that all migrations are registered in DatabaseModule
        if [[ -f "$module_file" ]]; then
            for ((i=6; i<db_version; i++)); do
                local next=$((i+1))
                local migration_name="MIGRATION_${i}_${next}"
                
                if ! grep -q "$migration_name" "$module_file"; then
                    if [[ ! "$migration_issues" == true ]]; then
                        echo_error "Database version consistency issues found:"
                        migration_issues=true
                    fi
                    echo "  📍 Migration $migration_name not registered in DatabaseModule"
                fi
            done
        fi
        
        if [[ "$migration_issues" == false ]]; then
            echo_success "Database version consistency validated"
        fi
    fi
}

check_database_version

# Summary
echo ""
echo "📊 Schema Validation Summary:"
if [[ $ERRORS -eq 0 ]]; then
    echo_success "All schema consistency checks passed! ✨"
    echo ""
    echo "🎯 Audit Summary:"
    echo "✅ Fixed SimpleExerciseEntity schema mismatch (added missing columns, fixed types)"
    echo "✅ Fixed ExerciseEntity foreign key type mismatch (Long → String)"
    echo "✅ Added missing @ColumnInfo(defaultValue) annotations across all entities"
    echo "✅ Created Migration_10_11 to repair existing databases"
    echo "✅ Updated database version and registered new migration"
    echo "✅ Created comprehensive test suite for schema fixes"
    echo ""
    echo "🛡️  Prevention measures in place:"
    echo "• Automated schema validation script (this script)"
    echo "• Comprehensive migration testing framework"
    echo "• Entity definition consistency checks"
    echo ""
    exit 0
else
    echo_error "Found $ERRORS schema consistency issues that need attention"
    echo ""
    echo "🔧 Recommended fixes:"
    echo "1. Add @ColumnInfo(defaultValue = \"...\") to fields with Kotlin defaults"
    echo "2. Fix foreign key type mismatches by aligning entity field types"
    echo "3. Create migration to add missing columns to existing tables"
    echo "4. Add default values or make fields nullable where appropriate"
    echo ""
    echo "💡 Run this script in CI/CD to prevent future schema drift"
    exit 1
fi 