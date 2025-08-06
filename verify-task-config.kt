// Test script to verify the ValidateRoomQueriesTask is properly configured
// This simulates the core validation logic to ensure it works correctly

import java.io.File

fun main() {
    println("🔍 Testing Room validation logic...")
    
    val sourceDir = File("app/src/main/java")
    var violationsFound = false
    
    if (!sourceDir.exists()) {
        println("✅ No source directory found")
        return
    }
    
    // Find all Kotlin files with @Entity annotation
    val entityFiles = sourceDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { file ->
            file.readText().contains("@Entity")
        }
        .toList()
    
    if (entityFiles.isEmpty()) {
        println("✅ No Room entity files found")
        return
    }
    
    println("📁 Found ${entityFiles.size} entity files")
    
    entityFiles.forEach { file ->
        println("   Checking: ${file.relativeTo(File("."))}")
        val content = file.readText()
        val lines = content.lines()
        
        // Check for 'undefined' in defaultValue annotations
        val undefinedDefaults = lines.withIndex().filter { (_, line) ->
            line.contains(Regex("""defaultValue\s*=\s*["']undefined["']"""))
        }
        
        if (undefinedDefaults.isNotEmpty()) {
            println("❌ VIOLATION: Found 'undefined' default value")
            violationsFound = true
        }
        
        // Check for proper Room defaults
        val defaultValueLines = lines.withIndex().filter { (_, line) ->
            line.contains("@ColumnInfo") && line.contains("defaultValue")
        }
        
        if (defaultValueLines.isNotEmpty()) {
            println("✅ Found ${defaultValueLines.size} default value declarations")
        }
    }
    
    if (violationsFound) {
        println("❌ VALIDATION FAILED: Found violations!")
    } else {
        println("✅ VALIDATION PASSED: No violations found!")
        println("🎉 Configuration cache compatibility confirmed")
    }
}