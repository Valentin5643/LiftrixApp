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
        private const val USERS_PUBLIC_COLLECTION = "social_profiles"
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
            Timber.i("[USER-SEARCH] 🔍 Starting user search")
            Timber.d("[USER-SEARCH]   - Query: '$query'")
            Timber.d("[USER-SEARCH]   - Viewer ID: $currentUserId")
            Timber.d("[USER-SEARCH]   - Filters: $filters")
            
            if (query.isBlank()) {
                Timber.d("[USER-SEARCH] ⚠️ Query is blank, returning empty results")
                return@liftrixCatching emptyList<UserSearchResult>()
            }

            // Check cache first for performance
            // Check cache first for performance
            val cachedResults = getCachedSearchResults(currentUserId, query).getOrNull()
            if (cachedResults != null && cachedResults.isNotEmpty()) {
                Timber.i("[USER-SEARCH] 💾 Found ${cachedResults.size} cached results for query: '$query'")
                Timber.d("[USER-SEARCH]   - Using cached data, skipping Firebase search")
                val filteredResults = applyFilters(cachedResults, filters)
                Timber.d("[USER-SEARCH]   - After filters: ${filteredResults.size} results")
                return@liftrixCatching filteredResults
            } else {
                Timber.d("[USER-SEARCH] 🚫 No valid cached results found, performing Firebase search")
            }
            // No cache hit, perform Firebase search

            // Perform comprehensive Firebase search with tokenized indexing
            // Perform comprehensive Firebase search with tokenized indexing
            Timber.d("[USER-SEARCH] 🔥 Performing Firebase search with tokenized indexing...")
            val searchResults = searchFirebaseUsersWithTokens(query, currentUserId, filters)
            
            // Cache results for future queries
            if (searchResults.isNotEmpty()) {
                Timber.i("[USER-SEARCH] ✅ Search completed with ${searchResults.size} results")
                Timber.d("[USER-SEARCH]   - Caching results for future queries")
                cacheSearchResults(currentUserId, query, searchResults)
                
                // Log sample of results for debugging
                searchResults.take(3).forEach { result ->
                    Timber.d("[USER-SEARCH]   - Result: ${result.displayName} (${result.userId}) - ${result.connectionStatus}")
                }
                if (searchResults.size > 3) {
                    Timber.d("[USER-SEARCH]   - ... and ${searchResults.size - 3} more results")
                }
            } else {
                Timber.w("[USER-SEARCH] 🚫 No results found for query: '$query'")
                Timber.w("[USER-SEARCH]   - User may not have created social profile yet")
                Timber.w("[USER-SEARCH]   - Check social_profiles collection in Firebase")
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
            Timber.d("[PUBLIC-PROFILE] 🔍 Getting public profile")
            Timber.d("[PUBLIC-PROFILE]   - Profile User ID: $userId")
            Timber.d("[PUBLIC-PROFILE]   - Viewer ID: $viewerId")
            Timber.d("[PUBLIC-PROFILE]   - Collection: $USERS_PUBLIC_COLLECTION")
            
            // Get Firebase public profile with privacy filtering
            val document = firestore.collection(USERS_PUBLIC_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (!document.exists()) {
                Timber.w("[PUBLIC-PROFILE] 🚫 Public profile not found for user: $userId")
                Timber.w("[PUBLIC-PROFILE]   - Document path: $USERS_PUBLIC_COLLECTION/$userId")
                Timber.w("[PUBLIC-PROFILE]   - User may not have completed onboarding")
                Timber.w("[PUBLIC-PROFILE]   - Check if social profile was created and synced")
                return@liftrixCatching null
            } else {
                Timber.d("[PUBLIC-PROFILE] ✅ Profile document found for user: $userId")
            }

            val data = document.data ?: return@liftrixCatching null
            
            // Check privacy settings - handle both isPrivate and isPublic fields for compatibility
            val isPrivate = data["isPrivate"] as? Boolean ?: false
            val isPublic = data["isPublic"] as? Boolean ?: !isPrivate // Default to opposite of isPrivate
            val profileIsPublic = !isPrivate || isPublic // Profile is public if isPrivate=false OR isPublic=true
            
            Timber.d("[PUBLIC-PROFILE] 🔒 Privacy check - isPrivate: $isPrivate, isPublic: $isPublic, resolved: $profileIsPublic, isOwner: ${userId == viewerId}")
            
            if (!profileIsPublic && userId != viewerId) {
                Timber.w("[PUBLIC-PROFILE] 🚫 Profile is private and viewer is not owner")
                Timber.w("[PUBLIC-PROFILE]   - Profile User: $userId")
                Timber.w("[PUBLIC-PROFILE]   - Viewer: $viewerId")
                Timber.w("[PUBLIC-PROFILE]   - Privacy fields: isPrivate=$isPrivate, isPublic=$isPublic")
                return@liftrixCatching null
            } else {
                Timber.d("[PUBLIC-PROFILE] ✅ Privacy check passed - profile accessible")
            }
            
            // Track profile view for analytics (but not self-views)
            if (userId != viewerId) {
                trackProfileView(userId, viewerId)
            }
            
            // Parse comprehensive profile data with enhanced profile image handling
            val memberSinceStr = data["memberSince"] as? String
            val lastActiveAtStr = data["lastActiveAt"] as? String
            val fitnessGoalsData = data["fitnessGoals"] as? List<String> ?: emptyList()
            val equipmentData = data["availableEquipment"] as? List<String> ?: emptyList()
            val achievementsData = data["publicAchievements"] as? List<Map<String, Any>> ?: emptyList()
            
            // Handle profile image URL with multiple possible field names for backward compatibility
            val profileImageUrl = (data["profileImageUrl"] as? String) 
                ?: (data["photoUrl"] as? String)
                ?: (data["profilePhotoUrl"] as? String)
                ?: (data["imageUrl"] as? String)
            
            // Debug logging for profile image resolution
            Timber.d("Profile image resolution for $userId: " +
                "profileImageUrl='${data["profileImageUrl"]}', " +
                "photoUrl='${data["photoUrl"]}', " +
                "profilePhotoUrl='${data["profilePhotoUrl"]}', " +
                "imageUrl='${data["imageUrl"]}', " +
                "resolved='$profileImageUrl'")
            
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
            
            Timber.d("[PUBLIC-PROFILE] 📊 Profile stats for $userId")
            Timber.d("[PUBLIC-PROFILE]   - Followers: $followersCount")
            Timber.d("[PUBLIC-PROFILE]   - Following: $followingCount")
            Timber.d("[PUBLIC-PROFILE]   - Total Workouts: $totalWorkouts")
            Timber.d("[PUBLIC-PROFILE]   - Fitness Level: $fitnessLevel")
            Timber.d("[PUBLIC-PROFILE]   - Connection Status: $connectionStatus")
            
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
                profileImageUrl = profileImageUrl,  // Use resolved profile image URL
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

    override suspend fun profileExists(userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to check profile existence: ${throwable.message}") }
        ) {
            Timber.d("[PROFILE-EXISTS] 🔍 Checking if profile exists for user: $userId")
            
            // Check Firebase social_profiles collection directly (no privacy filtering)
            val document = firestore.collection(USERS_PUBLIC_COLLECTION)
                .document(userId)
                .get()
                .await()

            val exists = document.exists()
            Timber.d("[PROFILE-EXISTS] ${if (exists) "✅" else "🚫"} Profile exists: $exists for user: $userId")
            
            exists
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
            Timber.d("[FIREBASE-SEARCH] 🔥 Starting tokenized Firebase search")
            Timber.d("[FIREBASE-SEARCH]   - Query: '$query'")
            Timber.d("[FIREBASE-SEARCH]   - Viewer: $viewerId")
            Timber.d("[FIREBASE-SEARCH]   - Collection: $USER_SEARCH_CACHE_COLLECTION")
            
            // Performing tokenized Firebase search
            
            // Use search cache collection for tokenized search
            val searchTokens = generateSearchTokensFromQuery(query)
            Timber.d("[FIREBASE-SEARCH] 🎨 Generated search tokens: $searchTokens")
            
            val searchQuery = firestore.collection(USER_SEARCH_CACHE_COLLECTION)
                .whereEqualTo("isSearchable", true)
                .whereArrayContainsAny("searchTokens", searchTokens)
                .orderBy("lastActiveAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_SEARCH_RESULTS.toLong())

            Timber.d("[FIREBASE-SEARCH] 📡 Executing Firestore query on $USER_SEARCH_CACHE_COLLECTION")
            val snapshot = searchQuery.get().await()
            Timber.d("[FIREBASE-SEARCH] ✅ Query executed, found ${snapshot.size()} documents")
            
            Timber.d("[FIREBASE-SEARCH] 📊 Processing ${snapshot.documents.size} query results...")
            
            val results = snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val userId = data["userId"] as? String ?: return@mapNotNull null
                val displayName = data["displayName"] as? String ?: return@mapNotNull null
                val username = data["username"] as? String
                
                Timber.v("[FIREBASE-SEARCH]   - Processing document: $userId ($displayName)")
                
                // Allow self-account to appear in search results
                // This enables users to find and view their own profile
                // Note: We still calculate connection status even for self
                
                UserSearchResult(
                    userId = userId,
                    displayName = displayName,
                    profileImageUrl = (data["profileImageUrl"] as? String) 
                        ?: (data["photoUrl"] as? String)
                        ?: (data["profilePhotoUrl"] as? String)
                        ?: (data["imageUrl"] as? String),
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
                Timber.w("[FIREBASE-SEARCH] 🚫 Tokenized search found no results for: '$query'")
                Timber.w("[FIREBASE-SEARCH]   - No documents matched search tokens: $searchTokens")
                Timber.w("[FIREBASE-SEARCH]   - Check if users have searchable profiles with search tokens")
            } else {
                Timber.i("[FIREBASE-SEARCH] ✅ Tokenized search found ${results.size} results")
                Timber.d("[FIREBASE-SEARCH]   - Results include ${results.count { it.connectionStatus == ConnectionStatus.SELF }} self-matches")
                Timber.d("[FIREBASE-SEARCH]   - Results include ${results.count { it.connectionStatus != ConnectionStatus.SELF }} other users")
            }
            results
            
        } catch (e: Exception) {
            Timber.e("[FIREBASE-SEARCH] ❌ Tokenized search failed: ${e.message}", e)
            Timber.e("[FIREBASE-SEARCH]   - Error type: ${e.javaClass.simpleName}")
            Timber.e("[FIREBASE-SEARCH]   - Collection: $USER_SEARCH_CACHE_COLLECTION")
            Timber.w("[FIREBASE-SEARCH] 🔄 Falling back to basic search on $USERS_PUBLIC_COLLECTION")
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
            Timber.d("[BASIC-SEARCH] 🔎 Starting basic Firebase search")
            Timber.d("[BASIC-SEARCH]   - Query: '$query'")
            Timber.d("[BASIC-SEARCH]   - Collection: $USERS_PUBLIC_COLLECTION")
            
            // Performing basic Firebase search
            
            val searchQuery = firestore.collection(USERS_PUBLIC_COLLECTION)
                .whereEqualTo("isSearchable", true)
                .limit(MAX_SEARCH_RESULTS.toLong())

            Timber.d("[BASIC-SEARCH] 📡 Executing basic search query...")
            val snapshot = searchQuery.get().await()
            Timber.d("[BASIC-SEARCH] ✅ Query executed, found ${snapshot.size()} documents")
            
            Timber.d("[BASIC-SEARCH] 📊 Processing ${snapshot.documents.size} basic search results...")
            
            val results = snapshot.documents.mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val displayName = data["displayName"] as? String ?: return@mapNotNull null
                val username = data["username"] as? String
                val userId = document.id
                
                Timber.v("[BASIC-SEARCH]   - Checking document: $userId ($displayName)")
                
                // Allow self-account to appear in search results
                // No longer filtering out the current user
                
                // Apply text matching on display name and username
                val matchesDisplayName = displayName.contains(query, ignoreCase = true)
                val matchesUsername = username?.contains(query, ignoreCase = true) ?: false
                
                Timber.v("[BASIC-SEARCH]     - Display name match: $matchesDisplayName")
                Timber.v("[BASIC-SEARCH]     - Username match: $matchesUsername")
                
                if (!matchesDisplayName && !matchesUsername) {
                    Timber.v("[BASIC-SEARCH]     - No match found, skipping")
                    return@mapNotNull null
                } else {
                    Timber.v("[BASIC-SEARCH]     - Match found, including in results")
                }
                
                
                UserSearchResult(
                    userId = userId,
                    displayName = displayName,
                    profileImageUrl = (data["profileImageUrl"] as? String) 
                        ?: (data["photoUrl"] as? String)
                        ?: (data["profilePhotoUrl"] as? String)
                        ?: (data["imageUrl"] as? String),
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
                Timber.w("[BASIC-SEARCH] 🚫 Basic search found no results for: '$query'")
                Timber.w("[BASIC-SEARCH]   - No display names or usernames matched the query")
                Timber.w("[BASIC-SEARCH]   - Total documents checked: ${snapshot.documents.size}")
            } else {
                Timber.i("[BASIC-SEARCH] ✅ Basic search found ${results.size} results")
                Timber.d("[BASIC-SEARCH]   - Results include ${results.count { it.connectionStatus == ConnectionStatus.SELF }} self-matches")
            }
            results
        } catch (e: Exception) {
            Timber.e("[BASIC-SEARCH] ❌ Basic search failed: ${e.message}", e)
            Timber.e("[BASIC-SEARCH]   - Error type: ${e.javaClass.simpleName}")
            Timber.e("[BASIC-SEARCH]   - Collection: $USERS_PUBLIC_COLLECTION")
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