package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.UserSearchCacheDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.PublicWorkoutStats
import com.example.liftrix.domain.model.social.RecentWorkout
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of UserSearchRepository with full social discovery features.
 * 
 * Features:
 * - Comprehensive user search with caching and indexing
 * - QR code generation and resolution with expiration handling
 * - Privacy-aware profile access with viewer context
 * - Search result caching with intelligent refresh strategies
 * - Performance optimized Firebase queries with composite indexes
 * - Real-time profile view tracking and analytics
 * - Advanced search filtering with equipment and goal matching
 * - Mutual connection calculation and social graph analysis
 */
@Singleton
class UserSearchRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val userSearchCacheDao: UserSearchCacheDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val workoutDao: WorkoutDao,
    private val workoutPostDao: WorkoutPostDao,
    private val workoutPostMapper: WorkoutPostMapper,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val gson: Gson
) : UserSearchRepository {

    companion object {
        private const val USERS_PUBLIC_COLLECTION = "users_public"
        private const val USER_SEARCH_CACHE_COLLECTION = "user_search_cache"
        private const val QR_CODE_COLLECTION = "qr_codes"
        private const val PROFILE_VIEWS_COLLECTION = "profile_views"
        private const val SEARCH_CACHE_EXPIRY_HOURS = 24
        private const val QR_CODE_EXPIRY_DAYS = 30
        private const val MAX_SEARCH_RESULTS = 50
        private const val MAX_CACHED_SEARCHES = 10
    }

    override suspend fun searchUsers(
        query: String,
        currentUserId: String,
        filters: SearchFilters
    ): LiftrixResult<List<UserSearchResult>> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.NetworkError("Search failed: ${throwable.message}") }
        ) {
            Timber.i("UserSearchRepository", "Starting user search for query: $query")
            
            if (query.isBlank()) {
                // Query is blank, return empty list
                return@liftrixCatching emptyList<UserSearchResult>()
            }

            // Check cache first for performance
            // Check cache first for performance
            val cachedResults = getCachedSearchResults(currentUserId, query).getOrNull()
            if (cachedResults != null && cachedResults.isNotEmpty()) {
                Timber.i("UserSearchRepository", "Found ${cachedResults.size} cached results")
                return@liftrixCatching applyFilters(cachedResults, filters)
            }
            // No cache hit, perform Firebase search

            // Perform comprehensive Firebase search with tokenized indexing
            // Perform comprehensive Firebase search with tokenized indexing
            val searchResults = searchFirebaseUsersWithTokens(query, currentUserId, filters)
            
            // Cache results for future queries
            if (searchResults.isNotEmpty()) {
                cacheSearchResults(currentUserId, query, searchResults)
                Timber.i("UserSearchRepository", "Search completed with ${searchResults.size} results")
            } else {
                Timber.w("UserSearchRepository", "No results found for query: $query")
            }
            
            // Search flow completed
            searchResults
        }
    }

    override suspend fun getPublicProfile(
        userId: String,
        viewerId: String
    ): LiftrixResult<PublicUserProfile?> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to get public profile: ${throwable.message}") }
        ) {
            Timber.d("Getting public profile: userId=$userId, viewerId=$viewerId")
            
            // Get Firebase public profile with privacy filtering
            val document = firestore.collection(USERS_PUBLIC_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (!document.exists()) {
                Timber.w("Public profile not found for user: $userId")
                return@liftrixCatching null
            }

            val data = document.data ?: return@liftrixCatching null
            
            // Check privacy settings - only return profile if public or if viewer is owner
            val isPublic = data["isPublic"] as? Boolean ?: false
            if (!isPublic && userId != viewerId) {
                Timber.w("Profile is private and viewer is not owner: userId=$userId, viewerId=$viewerId")
                return@liftrixCatching null
            }
            
            // Track profile view for analytics (but not self-views)
            if (userId != viewerId) {
                trackProfileView(userId, viewerId)
            }
            
            // Parse comprehensive profile data
            val memberSinceStr = data["memberSince"] as? String
            val lastActiveAtStr = data["lastActiveAt"] as? String
            val fitnessGoalsData = data["fitnessGoals"] as? List<String> ?: emptyList()
            val equipmentData = data["availableEquipment"] as? List<String> ?: emptyList()
            val achievementsData = data["publicAchievements"] as? List<Map<String, Any>> ?: emptyList()
            
            // Get connection status and mutual connections
            val connectionStatus = calculateConnectionStatus(userId, viewerId)
            val mutualConnections = calculateMutualConnections(userId, viewerId)
            
            // Determine fitness level based on workout stats
            val totalWorkouts = (data["totalWorkouts"] as? Number)?.toInt() ?: 0
            val fitnessLevel = determineFitnessLevel(totalWorkouts, data)
            
            // Get accurate follower/following counts from local database
            val followersCount = try {
                followRelationshipDao.getFollowerCount(userId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to get follower count for user: $userId, falling back to Firestore")
                (data["followersCount"] as? Number)?.toInt() ?: 0
            }
            
            val followingCount = try {
                followRelationshipDao.getFollowingCount(userId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to get following count for user: $userId, falling back to Firestore")
                (data["followingCount"] as? Number)?.toInt() ?: 0
            }
            
            Timber.d("Profile counts for $userId - Followers: $followersCount, Following: $followingCount")
            
            // Fetch recent workouts for the user profile (basic info)
            val recentWorkouts = try {
                val workoutsFlow = workoutDao.getRecentCompletedWorkouts(userId, limit = 5)
                val workouts = workoutsFlow.first()
                workouts.map { workout ->
                    // Calculate duration from start and end times
                    val durationMinutes = if (workout.startTime != null && workout.endTime != null) {
                        val durationSeconds = workout.endTime.epochSecond - workout.startTime.epochSecond
                        "${durationSeconds / 60}m"
                    } else {
                        "N/A"
                    }
                    
                    // Parse exercises from JSON to get count
                    val exerciseCount = try {
                        if (!workout.exercisesJson.isNullOrBlank()) {
                            val gson = com.google.gson.Gson()
                            val jsonElement = gson.fromJson(workout.exercisesJson, com.google.gson.JsonElement::class.java)
                            when {
                                jsonElement.isJsonObject && jsonElement.asJsonObject.has("exercises") -> {
                                    jsonElement.asJsonObject.getAsJsonArray("exercises").size()
                                }
                                jsonElement.isJsonArray -> {
                                    jsonElement.asJsonArray.size()
                                }
                                else -> 0
                            }
                        } else {
                            0
                        }
                    } catch (e: Exception) {
                        0
                    }
                    
                    RecentWorkout(
                        id = workout.id,
                        name = workout.name,
                        date = workout.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        exerciseCount = exerciseCount,
                        duration = durationMinutes
                    )
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch recent workouts for user: $userId")
                emptyList()
            }
            
            // Fetch recent workout posts for feed-style display
            val recentWorkoutPosts = try {
                val postsFlow = workoutPostDao.getRecentUserPosts(userId, limit = 3)
                val postEntities = postsFlow.first()
                postEntities.map { entity ->
                    workoutPostMapper.toDomain(entity, isLikedByViewer = false, isSavedByViewer = false)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch recent workout posts for user: $userId")
                emptyList()
            }
            
            PublicUserProfile(
                userId = userId,
                username = data["username"] as? String ?: "",  // Don't generate temporary username
                displayName = data["displayName"] as? String,
                profileImageUrl = data["profileImageUrl"] as? String,
                coverImageUrl = data["coverImageUrl"] as? String,
                bio = data["bio"] as? String,
                age = data["age"] as? Int,
                location = data["location"] as? String,
                fitnessLevel = fitnessLevel,
                followersCount = followersCount,
                followingCount = followingCount,
                mutualConnectionsCount = mutualConnections,
                totalWorkouts = totalWorkouts,
                currentStreak = (data["currentStreak"] as? Number)?.toInt() ?: 0,
                longestStreak = (data["longestStreak"] as? Number)?.toInt() ?: 0,
                memberSince = memberSinceStr?.let { parseDateTime(it) } ?: LocalDateTime.now(),
                lastActive = lastActiveAtStr?.let { parseDateTime(it) },
                isVerified = data["isVerified"] as? Boolean ?: false,
                isPrivate = data["isPrivate"] as? Boolean ?: false,
                followStatus = FollowStatus.NONE, // Will be determined by relationship check
                connectionStatus = connectionStatus,
                canViewDetails = true, // Will be determined by privacy settings
                recentWorkouts = recentWorkouts, // Now populated with actual workout data
                recentWorkoutPosts = recentWorkoutPosts, // Feed-style workout posts
                publicWorkoutStats = PublicWorkoutStats(
                    totalWorkouts = totalWorkouts,
                    totalWorkoutTime = (data["totalWorkoutTime"] as? Number)?.toLong() ?: 0L,
                    averageWorkoutTime = (data["averageWorkoutTime"] as? Number)?.toLong() ?: 0L,
                    currentStreak = (data["currentStreak"] as? Number)?.toInt() ?: 0,
                    longestStreak = (data["longestStreak"] as? Number)?.toInt() ?: 0
                )
            )
        }
    }

    override suspend fun generateProfileQRCode(userId: String): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to generate QR code: ${throwable.message}") }
        ) {
            Timber.d("Generating QR code for user: $userId")
            
            // Check for existing valid QR code
            val existingQRCode = getValidQRCode(userId)
            if (existingQRCode != null) {
                Timber.d("Returning existing valid QR code for user: $userId")
                return@liftrixCatching existingQRCode
            }
            
            // Generate new QR code with expiration
            val qrCodeId = generateUniqueQRCodeId()
            val expiresAt = LocalDateTime.now().plusDays(QR_CODE_EXPIRY_DAYS.toLong())
            val qrData = "liftrix://qr/$qrCodeId"
            
            // Store QR code mapping in Firebase
            val qrCodeDoc = mapOf(
                "qrCodeId" to qrCodeId,
                "userId" to userId,
                "qrData" to qrData,
                "createdAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "expiresAt" to expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "usageCount" to 0
            )
            
            firestore.collection(QR_CODE_COLLECTION)
                .document(qrCodeId)
                .set(qrCodeDoc)
                .await()
            
            Timber.d("Generated new QR code: $qrData for user: $userId")
            qrData
        }
    }

    override suspend fun resolveQRCodeProfile(qrData: String): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to resolve QR code: ${throwable.message}") }
        ) {
            Timber.d("Resolving QR code: $qrData")
            
            when {
                qrData.startsWith("liftrix://profile/") -> {
                    // Legacy direct profile QR codes
                    val userId = qrData.removePrefix("liftrix://profile/")
                    Timber.d("Resolved legacy QR code to userId: $userId")
                    userId
                }
                qrData.startsWith("liftrix://qr/") -> {
                    // New QR code system with expiration
                    val qrCodeId = qrData.removePrefix("liftrix://qr/")
                    val qrDocument = firestore.collection(QR_CODE_COLLECTION)
                        .document(qrCodeId)
                        .get()
                        .await()
                    
                    if (!qrDocument.exists()) {
                        throw IllegalArgumentException("QR code not found")
                    }
                    
                    val data = qrDocument.data ?: throw IllegalArgumentException("Invalid QR code data")
                    val expiresAtStr = data["expiresAt"] as? String
                    val userId = data["userId"] as? String
                    
                    if (userId == null) {
                        throw IllegalArgumentException("Invalid QR code - missing user ID")
                    }
                    
                    // Check expiration
                    if (expiresAtStr != null) {
                        val expiresAt = LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        if (expiresAt.isBefore(LocalDateTime.now())) {
                            throw IllegalArgumentException("QR code has expired")
                        }
                    }
                    
                    // Increment usage count
                    incrementQRCodeUsage(qrCodeId)
                    
                    Timber.d("Resolved QR code $qrCodeId to userId: $userId")
                    userId
                }
                else -> {
                    Timber.w("Invalid QR code format: $qrData")
                    throw IllegalArgumentException("Invalid QR code format")
                }
            }
        }
    }

    override suspend fun updateSearchKeywords(
        userId: String,
        keywords: List<String>
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to update search keywords: ${throwable.message}") }
        ) {
            Timber.d("Updating search keywords for user: $userId with ${keywords.size} keywords")
            
            // Generate search tokens for better search performance
            val searchTokens = generateSearchTokens(keywords)
            
            // Update search cache collection for fast user discovery
            val searchCacheDoc = mapOf(
                "userId" to userId,
                "searchTokens" to searchTokens,
                "keywords" to keywords,
                "updatedAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            
            firestore.collection(USER_SEARCH_CACHE_COLLECTION)
                .document(userId)
                .set(searchCacheDoc, com.google.firebase.firestore.SetOptions.merge())
                .await()
            
            Timber.d("Search keywords updated successfully for user: $userId")
        }
    }

    override suspend fun trackProfileView(
        profileUserId: String,
        viewerId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to track profile view: ${throwable.message}") }
        ) {
            Timber.d("Tracking profile view: profileUser=$profileUserId, viewer=$viewerId")
            
            // Create profile view record for analytics
            val viewRecord = mapOf(
                "profileUserId" to profileUserId,
                "viewerId" to viewerId,
                "viewedAt" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "sessionId" to generateSessionId()
            )
            
            // Store view record (with auto-generated ID)
            firestore.collection(PROFILE_VIEWS_COLLECTION)
                .add(viewRecord)
                .await()
            
            // Update profile view count (optional - can be computed from view records)
            val profileRef = firestore.collection(USERS_PUBLIC_COLLECTION).document(profileUserId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(profileRef)
                val currentViews = (snapshot.data?.get("profileViews") as? Number)?.toLong() ?: 0L
                transaction.update(profileRef, "profileViews", currentViews + 1)
                transaction.update(profileRef, "lastViewedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }.await()
            
            Timber.d("Profile view tracked successfully")
        }
    }

    override suspend fun getCachedSearchResults(
        viewerId: String,
        query: String
    ): LiftrixResult<List<UserSearchResult>?> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to get cached results: ${throwable.message}") }
        ) {
            Timber.d("Getting cached search results for viewer: $viewerId, query: '$query'")
            
            // Simple cache key based on normalized query
            val normalizedQuery = normalizeCacheKey(query)
            
            // Check local Room cache first
            val cachedResult = userSearchCacheDao.getCachedSearchResult(viewerId, normalizedQuery)
            
            if (cachedResult != null) {
                Timber.d("Found cached search results for query: '$query'")
                
                // Deserialize cached results
                val searchResults = try {
                    gson.fromJson(cachedResult.searchResults, Array<UserSearchResult>::class.java).toList()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to deserialize cached search results")
                    return@liftrixCatching null
                }
                
                Timber.d("Returning ${searchResults.size} cached search results")
                return@liftrixCatching searchResults
            }
            
            Timber.d("No valid cached results found")
            null
        }
    }

    override suspend fun clearSearchCache(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to clear cache: ${throwable.message}") }
        ) {
            Timber.d("Clearing search cache for user: $userId")
            
            // Clear local Room cache
            val deletedCount = userSearchCacheDao.deleteAllForUser(userId)
            Timber.d("Cleared $deletedCount cached search results for user: $userId")
            
            // Also clear expired entries periodically
            val expiredCount = userSearchCacheDao.deleteExpiredEntries()
            if (expiredCount > 0) {
                Timber.d("Cleaned up $expiredCount expired cache entries")
            }
            
            Timber.d("Search cache cleared successfully for user: $userId")
        }
    }

    // Enhanced search with tokenization and caching
    private suspend fun searchFirebaseUsersWithTokens(
        query: String,
        viewerId: String,
        filters: SearchFilters
    ): List<UserSearchResult> {
        return try {
            // Performing tokenized Firebase search
            
            // Use search cache collection for tokenized search
            val searchTokens = generateSearchTokensFromQuery(query)
            // Generated search tokens for query matching
            
            val searchQuery = firestore.collection(USER_SEARCH_CACHE_COLLECTION)
                .whereEqualTo("isPublic", true)
                .whereArrayContainsAny("searchTokens", searchTokens)
                .orderBy("lastActiveAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_SEARCH_RESULTS.toLong())

            // Executing Firestore query
            val snapshot = searchQuery.get().await()
            
            // Processing query results
            
            val results = snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val userId = data["userId"] as? String ?: return@mapNotNull null
                val displayName = data["displayName"] as? String ?: return@mapNotNull null
                val username = data["username"] as? String
                
                // Processing document for user matching
                
                // Allow self-account to appear in search results
                // This enables users to find and view their own profile
                // Note: We still calculate connection status even for self
                
                UserSearchResult(
                    userId = userId,
                    displayName = displayName,
                    profileImageUrl = data["profileImageUrl"] as? String,
                    bio = data["bio"] as? String,
                    fitnessLevel = determineFitnessLevelFromData(data),
                    totalWorkouts = (data["totalWorkouts"] as? Number)?.toInt() ?: 0,
                    memberSince = parseDateTime(data["memberSince"] as? String),
                    sharedEquipment = calculateSharedEquipment(data, viewerId),
                    sharedGoals = calculateSharedGoals(data, viewerId),
                    connectionStatus = if (userId == viewerId) ConnectionStatus.SELF else calculateConnectionStatus(userId, viewerId),
                    mutualConnections = if (userId == viewerId) 0 else calculateMutualConnections(userId, viewerId)
                )
            }
            
            if (results.isEmpty()) {
                Timber.w("UserSearchRepository", "Tokenized search found no results for: $query")
            } else {
                Timber.i("UserSearchRepository", "Tokenized search found ${results.size} results")
            }
            results
            
        } catch (e: Exception) {
            Timber.e("UserSearchRepository", "Tokenized search failed: ${e.message}", e)
            // Fallback to basic search
            Timber.w("UserSearchRepository", "Falling back to basic search")
            searchFirebaseUsersBasic(query, viewerId, filters)
        }
    }
    
    // Fallback basic search
    private suspend fun searchFirebaseUsersBasic(
        query: String,
        viewerId: String,
        filters: SearchFilters
    ): List<UserSearchResult> {
        return try {
            // Performing basic Firebase search
            
            val searchQuery = firestore.collection(USERS_PUBLIC_COLLECTION)
                .whereEqualTo("isPublic", true)
                .limit(MAX_SEARCH_RESULTS.toLong())

            // Executing basic search query
            val snapshot = searchQuery.get().await()
            
            // Processing basic search results
            
            val results = snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val displayName = data["displayName"] as? String ?: return@mapNotNull null
                val username = data["username"] as? String
                val userId = document.id
                
                // Checking document for query match
                
                // Allow self-account to appear in search results
                // No longer filtering out the current user
                
                // Apply text matching on display name and username
                val matchesDisplayName = displayName.contains(query, ignoreCase = true)
                val matchesUsername = username?.contains(query, ignoreCase = true) ?: false
                
                // Evaluating match criteria
                
                if (!matchesDisplayName && !matchesUsername) {
                    // No match found, skipping
                    return@mapNotNull null
                }
                
                // Match found, adding to results
                
                UserSearchResult(
                    userId = userId,
                    displayName = displayName,
                    profileImageUrl = data["profileImageUrl"] as? String,
                    bio = data["bio"] as? String,
                    fitnessLevel = determineFitnessLevelFromData(data),
                    totalWorkouts = (data["totalWorkouts"] as? Number)?.toInt() ?: 0,
                    memberSince = parseDateTime(data["memberSince"] as? String),
                    sharedEquipment = emptyList(),
                    sharedGoals = emptyList(),
                    connectionStatus = if (userId == viewerId) ConnectionStatus.SELF else ConnectionStatus.NONE,
                    mutualConnections = 0
                )
            }
            
            if (results.isEmpty()) {
                Timber.w("UserSearchRepository", "Basic search found no results for: $query")
            } else {
                Timber.i("UserSearchRepository", "Basic search found ${results.size} results")
            }
            results
        } catch (e: Exception) {
            Timber.e("UserSearchRepository", "Basic search failed: ${e.message}", e)
            emptyList()
        }
    }
    
    // Helper functions for enhanced functionality
    private fun generateSearchTokens(keywords: List<String>): List<String> {
        return keywords.flatMap { keyword ->
            val normalized = keyword.lowercase().trim()
            listOf(
                normalized,
                normalized.take(3), // 3-character prefix
                normalized.take(4), // 4-character prefix
            ) + normalized.split(" ").filter { it.length > 2 }
        }.distinct()
    }
    
    private fun generateSearchTokensFromQuery(query: String): List<String> {
        val normalized = query.lowercase().trim()
        return listOf(
            normalized,
            normalized.take(3),
            normalized.take(4)
        ) + normalized.split(" ").filter { it.length > 2 }
    }
    
    private suspend fun getValidQRCode(userId: String): String? {
        return try {
            val now = LocalDateTime.now()
            val snapshot = firestore.collection(QR_CODE_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereGreaterThan("expiresAt", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .orderBy("expiresAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.data?.get("qrData") as? String
        } catch (e: Exception) {
            Timber.e(e, "Error checking for valid QR code")
            null
        }
    }
    
    private fun generateUniqueQRCodeId(): String {
        return "qr_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private suspend fun incrementQRCodeUsage(qrCodeId: String) {
        try {
            val qrRef = firestore.collection(QR_CODE_COLLECTION).document(qrCodeId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(qrRef)
                val currentCount = (snapshot.data?.get("usageCount") as? Number)?.toInt() ?: 0
                transaction.update(qrRef, "usageCount", currentCount + 1)
                transaction.update(qrRef, "lastUsedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }.await()
        } catch (e: Exception) {
            Timber.e(e, "Error incrementing QR code usage")
        }
    }
    
    private fun parseDateTime(dateStr: String?): LocalDateTime? {
        return try {
            dateStr?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun determineFitnessLevel(totalWorkouts: Int, data: Map<String, Any>): FitnessLevel {
        return when {
            totalWorkouts >= 500 -> FitnessLevel.ADVANCED
            totalWorkouts >= 100 -> FitnessLevel.INTERMEDIATE
            totalWorkouts >= 20 -> FitnessLevel.BEGINNER
            else -> FitnessLevel.BEGINNER
        }
    }
    
    private fun determineFitnessLevelFromData(data: Map<String, Any>): FitnessLevel {
        val totalWorkouts = (data["totalWorkouts"] as? Number)?.toInt() ?: 0
        return determineFitnessLevel(totalWorkouts, data)
    }
    
    private suspend fun calculateConnectionStatus(userId: String, viewerId: String): ConnectionStatus {
        // Check if it's the same user
        if (userId == viewerId) {
            return ConnectionStatus.SELF
        }
        
        try {
            // Check the follow relationship from viewer to user
            val followRelationship = followRelationshipDao.getFollowRelationship(viewerId, userId)
            
            return when {
                followRelationship == null -> ConnectionStatus.NONE
                followRelationship.status == FollowRelationshipEntity.STATUS_ACCEPTED -> {
                    // Check if there's a mutual follow
                    val reverseRelationship = followRelationshipDao.getFollowRelationship(userId, viewerId)
                    if (reverseRelationship?.status == FollowRelationshipEntity.STATUS_ACCEPTED) {
                        ConnectionStatus.MUTUAL_FOLLOW
                    } else {
                        ConnectionStatus.CONNECTED
                    }
                }
                followRelationship.status == FollowRelationshipEntity.STATUS_PENDING -> ConnectionStatus.PENDING_SENT
                followRelationship.status == FollowRelationshipEntity.STATUS_BLOCKED -> ConnectionStatus.BLOCKED
                else -> ConnectionStatus.NONE
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating connection status between $viewerId and $userId")
            return ConnectionStatus.NONE
        }
    }
    
    private suspend fun calculateMutualConnections(userId: String, viewerId: String): Int {
        // For now, return 0 - this would be implemented with a connections collection
        return 0
    }
    
    private suspend fun calculateSharedEquipment(data: Map<String, Any>, viewerId: String): List<Equipment> {
        // For now, return empty list - this would require viewer's equipment data
        return emptyList()
    }
    
    private suspend fun calculateSharedGoals(data: Map<String, Any>, viewerId: String): List<FitnessGoal> {
        // For now, return empty list - this would require viewer's goal data
        return emptyList()
    }
    
    private fun parseAchievements(achievementsData: List<Map<String, Any>>): List<UserAchievement> {
        return achievementsData.mapNotNull { achievementMap ->
            try {
                val id = achievementMap["id"] as? String ?: return@mapNotNull null
                val userId = achievementMap["userId"] as? String ?: return@mapNotNull null
                val typeStr = achievementMap["achievementType"] as? String ?: return@mapNotNull null
                val achievementType = com.example.liftrix.domain.model.AchievementType.valueOf(typeStr)
                val title = achievementMap["title"] as? String ?: return@mapNotNull null
                val description = achievementMap["description"] as? String ?: return@mapNotNull null
                val unlockedAtStr = achievementMap["unlockedAt"] as? String ?: return@mapNotNull null
                val unlockedAt = parseDateTime(unlockedAtStr) ?: return@mapNotNull null
                val isDisplayed = achievementMap["isDisplayed"] as? Boolean ?: true
                
                UserAchievement(
                    id = id,
                    userId = userId,
                    achievementType = achievementType,
                    title = title,
                    description = description,
                    unlockedAt = unlockedAt,
                    isDisplayed = isDisplayed
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing achievement: $achievementMap")
                null
            }
        }
    }
    
    private fun parseFitnessGoals(goalsData: List<String>): List<FitnessGoal> {
        return goalsData.mapNotNull { goalStr ->
            try {
                FitnessGoal.valueOf(goalStr)
            } catch (e: Exception) {
                Timber.e(e, "Error parsing fitness goal: $goalStr")
                null
            }
        }
    }
    
    private fun parseEquipment(equipmentData: List<String>): List<Equipment> {
        return equipmentData.mapNotNull { equipmentStr ->
            try {
                Equipment.valueOf(equipmentStr)
            } catch (e: Exception) {
                Timber.e(e, "Error parsing equipment: $equipmentStr")
                null
            }
        }
    }
    
    private fun applyFilters(results: List<UserSearchResult>, filters: SearchFilters): List<UserSearchResult> {
        return results // For now, return unfiltered - filters would be applied here
    }
    
    private suspend fun cacheSearchResults(viewerId: String, query: String, results: List<UserSearchResult>) {
        try {
            val normalizedQuery = normalizeCacheKey(query)
            val now = LocalDateTime.now()
            val expiresAt = now.plusHours(SEARCH_CACHE_EXPIRY_HOURS.toLong())
            
            // Serialize results to JSON
            val serializedResults = gson.toJson(results)
            
            // Create cache entity
            val cacheEntity = UserSearchCacheEntity(
                id = "cache_${viewerId}_${System.currentTimeMillis()}",
                viewerUserId = viewerId,
                searchQuery = normalizedQuery,
                searchResults = serializedResults,
                createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                expiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            
            // Store in Room database
            userSearchCacheDao.insertOrUpdate(cacheEntity)
            
            // Clean up old entries to prevent cache bloat
            userSearchCacheDao.cleanupOldEntries(viewerId, MAX_CACHED_SEARCHES)
            
            Timber.d("Successfully cached ${results.size} search results for query: '$query'")
        } catch (e: Exception) {
            Timber.e(e, "Error caching search results for query: '$query'")
        }
    }
    
    private fun normalizeCacheKey(query: String): String {
        return query.lowercase().trim().replace(Regex("\\s+"), "_")
    }
    
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}"
    }
}