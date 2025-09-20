import kotlinx.datetime.LocalDate
import java.util.Calendar
import java.util.Date

fun main() {
    // Problematic workout timestamp from logs
    val workoutTimestamp = 1758316901826L
    val workoutDate = LocalDate.fromEpochDays((workoutTimestamp / (24 * 60 * 60 * 1000)).toInt())
    
    println("🔍 TIMESTAMP ANALYSIS:")
    println("Workout timestamp: $workoutTimestamp")
    println("Workout date: $workoutDate")
    
    // Calculate when this timestamp represents
    val workoutDateJava = Date(workoutTimestamp)
    println("Workout Java Date: $workoutDateJava")
    
    // Calculate TimeRange.lastMonth() equivalent
    val calendar = Calendar.getInstance()
    val endDate = calendar.time
    calendar.add(Calendar.MONTH, -1)
    val startDate = calendar.time
    
    println("\nTimeRange.lastMonth():")
    println("Start: $startDate")
    println("End: $endDate")
    
    val startDateLocalDate = LocalDate.fromEpochDays((startDate.time / (24 * 60 * 60 * 1000)).toInt())
    val endDateLocalDate = LocalDate.fromEpochDays((endDate.time / (24 * 60 * 60 * 1000)).toInt())
    
    println("\nAs LocalDate:")
    println("Start: $startDateLocalDate")
    println("End: $endDateLocalDate")
    
    val isInRange = workoutDate >= startDateLocalDate && workoutDate <= endDateLocalDate
    println("\nIs workout date in range? $isInRange")
    
    if (!isInRange) {
        println("\n🚨 ROOT CAUSE IDENTIFIED:")
        println("The workout was created at timestamp $workoutTimestamp which is:")
        if (workoutTimestamp > System.currentTimeMillis()) {
            println("- IN THE FUTURE! This is likely a test workout with incorrect timestamp")
            val daysInFuture = (workoutTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
            println("- $daysInFuture days in the future")
        } else {
            val daysAgo = (System.currentTimeMillis() - workoutTimestamp) / (24 * 60 * 60 * 1000)
            println("- $daysAgo days in the past (outside last month range)")
        }
    }
    
    // Show what date range would include this workout
    val workoutDateAsJavaLocalDate = java.time.LocalDate.of(workoutDate.year, workoutDate.monthNumber, workoutDate.dayOfMonth)
    val requiredStartDate = workoutDateAsJavaLocalDate.minusDays(1)
    val requiredEndDate = workoutDateAsJavaLocalDate.plusDays(1)
    
    println("\nTo include this workout, the date range should be:")
    println("Start: $requiredStartDate or earlier")
    println("End: $requiredEndDate or later")
}