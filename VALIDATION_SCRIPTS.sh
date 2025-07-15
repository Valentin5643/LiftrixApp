#!/bin/bash

# 🧪 Repository Migration Validation Scripts
# Execute these scripts to validate the repository migration at each phase

set -e  # Exit on any error

echo "🚀 Repository Migration Validation Scripts"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to run command and check exit code
run_test() {
    local test_name="$1"
    local command="$2"
    
    log_info "Running: $test_name"
    
    if eval "$command"; then
        log_success "$test_name - PASSED"
        return 0
    else
        log_error "$test_name - FAILED"
        return 1
    fi
}

# ========================================
# PHASE 1: PRE-MIGRATION VALIDATION
# ========================================
phase1_pre_migration_validation() {
    echo ""
    echo "📋 PHASE 1: Pre-Migration Validation"
    echo "===================================="
    
    local failures=0
    
    # Validate existing tests pass with legacy repositories
    run_test "Legacy Repository Tests" \
        "./gradlew test --tests='*ExerciseLibraryRepositoryImplTest' --tests='*WorkoutRepositoryImplTest' --tests='*SubscriptionRepositoryImplTest'" || ((failures++))
    
    # Validate use case tests pass
    run_test "Legacy Use Case Tests" \
        "./gradlew test --tests='*UseCase*Test'" || ((failures++))
    
    # Database migration tests
    run_test "Database Migration Tests" \
        "./gradlew connectedAndroidTest --tests='*Migration*Test'" || ((failures++))
    
    # Performance baseline establishment
    run_test "Performance Baseline Tests" \
        "./gradlew connectedAndroidTest --tests='*PerformanceTest'" || ((failures++))
    
    if [ $failures -eq 0 ]; then
        log_success "Phase 1: All pre-migration validations passed"
        return 0
    else
        log_error "Phase 1: $failures validation(s) failed"
        return 1
    fi
}

# ========================================
# PHASE 2: SIDE-BY-SIDE EQUIVALENCE TESTING
# ========================================
phase2_equivalence_testing() {
    echo ""
    echo "🔄 PHASE 2: Side-by-Side Equivalence Testing"
    echo "============================================"
    
    local failures=0
    
    # Exercise Repository Equivalence
    run_test "Exercise Repository Equivalence" \
        "./gradlew test --tests='*ExerciseRepositoryEquivalenceTest'" || ((failures++))
    
    # Workout Repository Equivalence
    run_test "Workout Repository Equivalence" \
        "./gradlew test --tests='*WorkoutRepositoryEquivalenceTest'" || ((failures++))
    
    # Template Repository Equivalence
    run_test "Template Repository Equivalence" \
        "./gradlew test --tests='*TemplateRepositoryEquivalenceTest'" || ((failures++))
    
    # Session Repository Validation (new functionality)
    run_test "Session Repository Validation" \
        "./gradlew test --tests='*SessionRepositoryTest'" || ((failures++))
    
    if [ $failures -eq 0 ]; then
        log_success "Phase 2: All equivalence tests passed"
        return 0
    else
        log_error "Phase 2: $failures equivalence test(s) failed"
        return 1
    fi
}

# ========================================
# PHASE 3: NEW REPOSITORY PATTERN VALIDATION
# ========================================
phase3_new_pattern_validation() {
    echo ""
    echo "🆕 PHASE 3: New Repository Pattern Validation"
    echo "============================================="
    
    local failures=0
    
    # New repository tests with LiftrixResult<T>
    run_test "New Exercise Repository Tests" \
        "./gradlew test --tests='*exercise.ExerciseRepositoryImplTest'" || ((failures++))
    
    run_test "New Workout Repository Tests" \
        "./gradlew test --tests='*workout.WorkoutRepositoryImplTest'" || ((failures++))
    
    run_test "New Template Repository Tests" \
        "./gradlew test --tests='*template.TemplateRepositoryImplTest'" || ((failures++))
    
    run_test "New Session Repository Tests" \
        "./gradlew test --tests='*session.SessionRepositoryImplTest'" || ((failures++))
    
    # Error handling validation
    run_test "LiftrixResult Error Handling Tests" \
        "./gradlew test --tests='*LiftrixResultTest' --tests='*LiftrixErrorTest'" || ((failures++))
    
    if [ $failures -eq 0 ]; then
        log_success "Phase 3: All new pattern validations passed"
        return 0
    else
        log_error "Phase 3: $failures new pattern validation(s) failed"
        return 1
    fi
}

# ========================================
# PHASE 4: USE CASE MIGRATION VALIDATION
# ========================================
phase4_use_case_validation() {
    echo ""
    echo "🔧 PHASE 4: Use Case Migration Validation"
    echo "========================================="
    
    local failures=0
    
    # Exercise use cases
    run_test "Exercise Use Case Tests" \
        "./gradlew test --tests='*exercise.*UseCaseTest'" || ((failures++))
    
    # Workout use cases
    run_test "Workout Use Case Tests" \
        "./gradlew test --tests='*workout.*UseCaseTest'" || ((failures++))
    
    # Template use cases
    run_test "Template Use Case Tests" \
        "./gradlew test --tests='*template.*UseCaseTest'" || ((failures++))
    
    # Session use cases (new)
    run_test "Session Use Case Tests" \
        "./gradlew test --tests='*session.*UseCaseTest'" || ((failures++))
    
    # Unified session use cases
    run_test "Unified Session Use Case Tests" \
        "./gradlew test --tests='*StartWorkoutSessionUseCaseTest' --tests='*AddExerciseToSessionUseCaseTest'" || ((failures++))
    
    if [ $failures -eq 0 ]; then
        log_success "Phase 4: All use case validations passed"
        return 0
    else
        log_error "Phase 4: $failures use case validation(s) failed"
        return 1
    fi
}

# ========================================
# PHASE 5: INTEGRATION TESTING
# ========================================
phase5_integration_testing() {
    echo ""
    echo "🔗 PHASE 5: Integration Testing"
    echo "==============================="
    
    local failures=0
    
    # End-to-end data flow testing
    run_test "Repository Integration Tests" \
        "./gradlew connectedAndroidTest --tests='*RepositoryIntegrationTest'" || ((failures++))
    
    # Session management integration
    run_test "Session Management Integration" \
        "./gradlew connectedAndroidTest --tests='*SessionIntegrationTest'" || ((failures++))
    
    # Unified workflow integration
    run_test "Unified Workflow Integration" \
        "./gradlew connectedAndroidTest --tests='*UnifiedWorkoutSessionIntegrationTest'" || ((failures++))
    
    # Navigation integration
    run_test "Navigation Integration Tests" \
        "./gradlew connectedAndroidTest --tests='*NavigationIntegrationTest'" || ((failures++))
    
    if [ $failures -eq 0 ]; then
        log_success "Phase 5: All integration tests passed"
        return 0
    else
        log_error "Phase 5: $failures integration test(s) failed"
        return 1
    fi
}

# ========================================
# PHASE 6: PERFORMANCE REGRESSION TESTING
# ========================================
phase6_performance_testing() {
    echo ""
    echo "⚡ PHASE 6: Performance Regression Testing"
    echo "=========================================="
    
    local failures=0
    
    # Repository performance tests
    run_test "Repository Performance Tests" \
        "./gradlew connectedAndroidTest --tests='*RepositoryPerformanceTest'" || ((failures++))
    
    # Database query performance
    run_test "Database Performance Tests" \
        "./gradlew connectedAndroidTest --tests='*DatabasePerformanceTest'" || ((failures++))
    
    # Memory usage validation
    run_test "Memory Usage Tests" \
        "./gradlew connectedAndroidTest --tests='*MemoryUsageTest'" || ((failures++))
    
    # UI performance validation
    run_test "UI Performance Tests" \
        "./gradlew connectedAndroidTest --tests='*UIPerformanceTest'" || ((failures++))
    
    if [ $failures -eq 0 ]; then
        log_success "Phase 6: All performance tests passed"
        return 0
    else
        log_error "Phase 6: $failures performance test(s) failed"
        return 1
    fi
}

# ========================================
# COMPREHENSIVE VALIDATION SUITE
# ========================================
run_comprehensive_validation() {
    echo ""
    echo "🎯 COMPREHENSIVE REPOSITORY MIGRATION VALIDATION"
    echo "==============================================="
    
    local total_phases=6
    local passed_phases=0
    
    # Execute all phases
    if phase1_pre_migration_validation; then
        ((passed_phases++))
    fi
    
    if phase2_equivalence_testing; then
        ((passed_phases++))
    fi
    
    if phase3_new_pattern_validation; then
        ((passed_phases++))
    fi
    
    if phase4_use_case_validation; then
        ((passed_phases++))
    fi
    
    if phase5_integration_testing; then
        ((passed_phases++))
    fi
    
    if phase6_performance_testing; then
        ((passed_phases++))
    fi
    
    # Final summary
    echo ""
    echo "📊 VALIDATION SUMMARY"
    echo "===================="
    echo "Phases Passed: $passed_phases/$total_phases"
    
    if [ $passed_phases -eq $total_phases ]; then
        log_success "🎉 ALL VALIDATION PHASES PASSED! Migration is validated."
        echo ""
        echo "✅ Repository migration is ready for production deployment"
        echo "✅ All functionality preserved"
        echo "✅ Performance targets met"
        echo "✅ Integration tests passed"
        return 0
    else
        log_error "❌ VALIDATION FAILED: $((total_phases - passed_phases)) phase(s) failed"
        echo ""
        echo "❌ Repository migration requires fixes before deployment"
        echo "❌ Review failed tests and address issues"
        return 1
    fi
}

# ========================================
# QUICK VALIDATION SCRIPTS
# ========================================
quick_repository_test() {
    echo "🚀 Quick Repository Test"
    echo "======================="
    
    run_test "Quick Repository Validation" \
        "./gradlew test --tests='*Repository*Test' --parallel"
}

quick_use_case_test() {
    echo "🚀 Quick Use Case Test"
    echo "====================="
    
    run_test "Quick Use Case Validation" \
        "./gradlew test --tests='*UseCase*Test' --parallel"
}

quick_integration_test() {
    echo "🚀 Quick Integration Test"
    echo "========================"
    
    run_test "Quick Integration Validation" \
        "./gradlew connectedAndroidTest --tests='*IntegrationTest' --parallel"
}

# ========================================
# TEST DATA SETUP AND CLEANUP
# ========================================
setup_test_environment() {
    echo "🔧 Setting up test environment..."
    
    # Clean previous test artifacts
    ./gradlew clean
    
    # Build project
    ./gradlew assembleDebug assembleDebugAndroidTest
    
    # Setup test database
    log_info "Test environment ready"
}

cleanup_test_environment() {
    echo "🧹 Cleaning up test environment..."
    
    # Clear test data
    ./gradlew clean
    
    log_info "Test environment cleaned"
}

# ========================================
# PERFORMANCE BENCHMARKING
# ========================================
benchmark_repositories() {
    echo "📊 Repository Performance Benchmarking"
    echo "====================================="
    
    # Create benchmark report directory
    mkdir -p build/reports/benchmarks
    
    # Run performance tests with detailed reporting
    ./gradlew connectedAndroidTest \
        --tests='*PerformanceTest' \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.output.enable=true
    
    log_info "Performance benchmarks completed. Check build/reports/benchmarks/"
}

# ========================================
# CONTINUOUS VALIDATION
# ========================================
continuous_validation_monitor() {
    echo "🔄 Continuous Validation Monitor"
    echo "==============================="
    
    # Watch for file changes and run relevant tests
    while true; do
        log_info "Monitoring for changes... (Ctrl+C to stop)"
        
        # Run quick validation every 30 seconds
        sleep 30
        
        if quick_repository_test && quick_use_case_test; then
            log_success "Continuous validation: PASSED"
        else
            log_warning "Continuous validation: Issues detected"
        fi
    done
}

# ========================================
# COMMAND LINE INTERFACE
# ========================================
show_help() {
    echo "Repository Migration Validation Scripts"
    echo "======================================"
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  comprehensive    Run complete validation suite (all phases)"
    echo "  phase1          Pre-migration validation"
    echo "  phase2          Side-by-side equivalence testing"
    echo "  phase3          New repository pattern validation"
    echo "  phase4          Use case migration validation"
    echo "  phase5          Integration testing"
    echo "  phase6          Performance regression testing"
    echo "  quick-repo      Quick repository validation"
    echo "  quick-usecase   Quick use case validation"
    echo "  quick-integration Quick integration validation"
    echo "  setup           Setup test environment"
    echo "  cleanup         Cleanup test environment"
    echo "  benchmark       Run performance benchmarks"
    echo "  monitor         Continuous validation monitoring"
    echo "  help            Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 comprehensive     # Run full validation suite"
    echo "  $0 phase2            # Run equivalence testing only"
    echo "  $0 quick-repo        # Quick repository test"
}

# Main command dispatcher
case "${1:-help}" in
    comprehensive)
        setup_test_environment
        run_comprehensive_validation
        ;;
    phase1)
        setup_test_environment
        phase1_pre_migration_validation
        ;;
    phase2)
        setup_test_environment
        phase2_equivalence_testing
        ;;
    phase3)
        setup_test_environment
        phase3_new_pattern_validation
        ;;
    phase4)
        setup_test_environment
        phase4_use_case_validation
        ;;
    phase5)
        setup_test_environment
        phase5_integration_testing
        ;;
    phase6)
        setup_test_environment
        phase6_performance_testing
        ;;
    quick-repo)
        quick_repository_test
        ;;
    quick-usecase)
        quick_use_case_test
        ;;
    quick-integration)
        quick_integration_test
        ;;
    setup)
        setup_test_environment
        ;;
    cleanup)
        cleanup_test_environment
        ;;
    benchmark)
        benchmark_repositories
        ;;
    monitor)
        continuous_validation_monitor
        ;;
    help|*)
        show_help
        ;;
esac

exit_code=$?

echo ""
if [ $exit_code -eq 0 ]; then
    log_success "Script completed successfully"
else
    log_error "Script completed with errors"
fi

exit $exit_code