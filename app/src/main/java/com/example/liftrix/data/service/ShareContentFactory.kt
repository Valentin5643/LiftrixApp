package com.example.liftrix.data.service

import com.example.liftrix.domain.usecase.sharing.ShareRequest
import com.example.liftrix.domain.usecase.sharing.ShareableContent
import com.example.liftrix.domain.usecase.sharing.SharePlatform
import com.example.liftrix.domain.usecase.sharing.ShareContentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating platform-specific shareable content
 * 
 * This service formats workout data and other content types for optimal display
 * on different social media platforms, respecting their unique requirements:
 * - Instagram: Visual-focused with hashtags and story-friendly formatting
 * - WhatsApp: Text-focused with emojis and structured information
 * - Generic: Clean, universal formatting for any platform
 */
@Singleton
class ShareContentFactory @Inject constructor() {
    
    /**
     * Creates platform-optimized shareable content from a share request
     * 
     * @param request The share request containing platform and content details
     * @return ShareableContent formatted for the target platform
     */
    fun createShareableContent(request: ShareRequest): ShareableContent {
        return when (request.platform) {
            SharePlatform.INSTAGRAM_STORY -> createInstagramContent(request)
            SharePlatform.WHATSAPP -> createWhatsAppContent(request)
            SharePlatform.GENERIC -> createGenericContent(request)
        }
    }
    
    /**
     * Creates Instagram Story optimized content with hashtags and visual appeal
     */
    private fun createInstagramContent(request: ShareRequest): ShareableContent {
        return when (request.contentType) {
            ShareContentType.WORKOUT_SUMMARY -> {
                val workoutData = request.workoutData
                ShareableContent(
                    title = "💪 Workout Complete!",
                    message = buildInstagramWorkoutMessage(workoutData, request.customMessage),
                    imageUrl = workoutData?.imageUrl,
                    linkUrl = generateWorkoutLink(request.workoutId),
                    hashtags = listOf(
                        "workout", "fitness", "liftrix", "strength", "training", 
                        "gymlife", "personalrecord", "fitnessmotivation"
                    )
                )
            }
            ShareContentType.WORKOUT_LINK -> {
                ShareableContent(
                    title = "🔗 Check out my workout!",
                    message = "Just finished an amazing workout session! ${request.customMessage ?: ""}",
                    linkUrl = generateWorkoutLink(request.workoutId),
                    hashtags = listOf("workout", "fitness", "liftrix", "training")
                )
            }
            ShareContentType.PERSONAL_RECORD -> {
                ShareableContent(
                    title = "🏆 New Personal Record!",
                    message = "Just hit a new PR! ${request.customMessage ?: ""} #NewPR #StrongerEveryDay",
                    linkUrl = generateWorkoutLink(request.workoutId),
                    hashtags = listOf("personalrecord", "PR", "strength", "fitness", "liftrix", "progress")
                )
            }
            ShareContentType.ACHIEVEMENT -> {
                ShareableContent(
                    title = "🎯 Achievement Unlocked!",
                    message = "Another milestone reached! ${request.customMessage ?: ""}",
                    hashtags = listOf("achievement", "fitness", "progress", "liftrix", "motivation")
                )
            }
        }
    }
    
    /**
     * Creates WhatsApp optimized content with emojis and structured text
     */
    private fun createWhatsAppContent(request: ShareRequest): ShareableContent {
        return when (request.contentType) {
            ShareContentType.WORKOUT_SUMMARY -> {
                val workoutData = request.workoutData
                ShareableContent(
                    title = "💪 Workout Complete!",
                    message = buildWhatsAppWorkoutMessage(workoutData, request.customMessage),
                    linkUrl = generateWorkoutLink(request.workoutId)
                )
            }
            ShareContentType.WORKOUT_LINK -> {
                ShareableContent(
                    title = "🔗 My Workout",
                    message = buildString {
                        appendLine("Hey! Check out my latest workout session 💪")
                        if (request.customMessage != null) {
                            appendLine()
                            appendLine(request.customMessage)
                        }
                        appendLine()
                        appendLine("View workout: ${generateWorkoutLink(request.workoutId)}")
                        appendLine()
                        appendLine("Sent via Liftrix 📱")
                    }.trim(),
                    linkUrl = generateWorkoutLink(request.workoutId)
                )
            }
            ShareContentType.PERSONAL_RECORD -> {
                ShareableContent(
                    title = "🏆 New Personal Record!",
                    message = buildString {
                        appendLine("🚀 Just hit a new Personal Record!")
                        appendLine()
                        if (request.customMessage != null) {
                            appendLine("${request.customMessage}")
                            appendLine()
                        }
                        appendLine("Check it out: ${generateWorkoutLink(request.workoutId)}")
                        appendLine()
                        appendLine("💪 Getting stronger every day!")
                        appendLine("Tracked with Liftrix")
                    }.trim(),
                    linkUrl = generateWorkoutLink(request.workoutId)
                )
            }
            ShareContentType.ACHIEVEMENT -> {
                ShareableContent(
                    title = "🎯 Achievement Unlocked!",
                    message = buildString {
                        appendLine("🎉 Achievement Unlocked!")
                        appendLine()
                        if (request.customMessage != null) {
                            appendLine(request.customMessage)
                            appendLine()
                        }
                        appendLine("Another step forward in my fitness journey 🚀")
                        appendLine("Tracking progress with Liftrix")
                    }.trim()
                )
            }
        }
    }
    
    /**
     * Creates generic, platform-agnostic content
     */
    private fun createGenericContent(request: ShareRequest): ShareableContent {
        return when (request.contentType) {
            ShareContentType.WORKOUT_SUMMARY -> {
                val workoutData = request.workoutData
                ShareableContent(
                    title = "Workout Complete",
                    message = buildGenericWorkoutMessage(workoutData, request.customMessage),
                    linkUrl = generateWorkoutLink(request.workoutId)
                )
            }
            ShareContentType.WORKOUT_LINK -> {
                ShareableContent(
                    title = "My Workout",
                    message = buildString {
                        appendLine("Check out my latest workout session!")
                        if (request.customMessage != null) {
                            appendLine()
                            appendLine(request.customMessage)
                        }
                        appendLine()
                        appendLine("View workout: ${generateWorkoutLink(request.workoutId)}")
                    }.trim(),
                    linkUrl = generateWorkoutLink(request.workoutId)
                )
            }
            ShareContentType.PERSONAL_RECORD -> {
                ShareableContent(
                    title = "New Personal Record!",
                    message = buildString {
                        appendLine("Just achieved a new Personal Record!")
                        if (request.customMessage != null) {
                            appendLine(request.customMessage)
                        }
                        appendLine()
                        appendLine("View workout: ${generateWorkoutLink(request.workoutId)}")
                    }.trim(),
                    linkUrl = generateWorkoutLink(request.workoutId)
                )
            }
            ShareContentType.ACHIEVEMENT -> {
                ShareableContent(
                    title = "Achievement Unlocked!",
                    message = request.customMessage ?: "Another milestone reached in my fitness journey!"
                )
            }
        }
    }
    
    /**
     * Builds Instagram-specific workout summary message
     */
    private fun buildInstagramWorkoutMessage(workoutData: com.example.liftrix.domain.usecase.sharing.ShareWorkoutData?, customMessage: String?): String {
        return buildString {
            if (customMessage != null) {
                appendLine(customMessage)
                appendLine()
            }
            
            workoutData?.let { data ->
                appendLine("⏱️ Duration: ${data.duration}")
                appendLine("📊 Volume: ${data.totalVolume}")
                appendLine("💪 Exercises: ${data.exercises.take(3).joinToString(", ")}${if (data.exercises.size > 3) "..." else ""}")
                
                if (data.personalRecords.isNotEmpty()) {
                    appendLine()
                    appendLine("🏆 PRs: ${data.personalRecords.joinToString(", ")}")
                }
            }
            
            appendLine()
            appendLine("#StrongerEveryDay #FitnessJourney")
        }.trim()
    }
    
    /**
     * Builds WhatsApp-specific workout summary message
     */
    private fun buildWhatsAppWorkoutMessage(workoutData: com.example.liftrix.domain.usecase.sharing.ShareWorkoutData?, customMessage: String?): String {
        return buildString {
            appendLine("💪 Just finished a great workout!")
            appendLine()
            
            if (customMessage != null) {
                appendLine(customMessage)
                appendLine()
            }
            
            workoutData?.let { data ->
                appendLine("📋 Workout Summary:")
                appendLine("• Duration: ${data.duration}")
                appendLine("• Total Volume: ${data.totalVolume}")
                appendLine("• Exercises: ${data.exercises.joinToString(", ")}")
                
                if (data.personalRecords.isNotEmpty()) {
                    appendLine()
                    appendLine("🏆 Personal Records:")
                    data.personalRecords.forEach { pr ->
                        appendLine("• $pr")
                    }
                }
            }
            
            appendLine()
            appendLine("Getting stronger every day! 💪")
            appendLine("Tracked with Liftrix")
        }.trim()
    }
    
    /**
     * Builds generic workout summary message
     */
    private fun buildGenericWorkoutMessage(workoutData: com.example.liftrix.domain.usecase.sharing.ShareWorkoutData?, customMessage: String?): String {
        return buildString {
            appendLine("Workout completed successfully!")
            appendLine()
            
            if (customMessage != null) {
                appendLine(customMessage)
                appendLine()
            }
            
            workoutData?.let { data ->
                appendLine("Workout Summary:")
                appendLine("Duration: ${data.duration}")
                appendLine("Total Volume: ${data.totalVolume}")
                appendLine("Exercises: ${data.exercises.joinToString(", ")}")
                
                if (data.personalRecords.isNotEmpty()) {
                    appendLine()
                    appendLine("Personal Records:")
                    data.personalRecords.forEach { pr ->
                        appendLine("- $pr")
                    }
                }
            }
        }.trim()
    }
    
    /**
     * Generates a shareable workout link
     */
    private fun generateWorkoutLink(workoutId: String?): String {
        return if (workoutId != null) {
            "https://liftrix.app/workout/$workoutId"
        } else {
            "https://liftrix.app"
        }
    }
}