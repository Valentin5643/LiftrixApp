package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.UserSearchCacheDao
import com.example.liftrix.data.local.dao.QRCodeMappingDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.entity.UserSearchCacheEntity
import com.example.liftrix.data.local.entity.QRCodeMappingEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.FitnessLevel
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.PublicWorkoutStats
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UserSearchRepository with Firebase integration and local caching
 * 
 * Provides advanced user search capabilities with privacy filtering, caching, and
 * QR code profile sharing. Designed for performance and offline-first functionality.
 */
@Singleton
class UserSearchRepositoryImpl @Inject constructor(
    private val userSearchCacheDao: UserSearchCacheDao,
    private val qrCodeMappingDao: QRCodeMappingDao,
    private val userProfileDao: UserProfileDao,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val gson: Gson
) : UserSearchRepository {

    companion object {
        private const val USERS_PUBLIC_COLLECTION = "users_public"
        private const val QR_CODES_COLLECTION = "qr_codes"
        private const val CACHE_TTL_HOURS = 1L
        private const val MAX_SEARCH_RESULTS = 20
        private const val QR_CODE_EXPIRY_DAYS = 30L
    }

    override suspend fun searchUsers(
        query: String,
        currentUserId: String,
        filters: SearchFilters
    ): LiftrixResult<List<UserSearchResult>> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to search users") }
        ) {
            Timber.d("Searching users: query='$query', filters=$filters")
            
            // Check cache first if query is not empty
            if (query.isNotBlank()) {
                val cachedResults = getCachedSearchResultsInternal(currentUserId, query)
                if (cachedResults != null) {
                    Timber.d("Returning cached search results for query: $query")
                    return@liftrixCatching applyCachedFilters(cachedResults, filters)
                }
            }

            // Perform Firebase search
            val searchResults = searchFirebaseUsers(query, currentUserId, filters)
            
            // Cache the results if query is not empty
            if (query.isNotBlank() && searchResults.isNotEmpty()) {
                cacheSearchResults(currentUserId, query, searchResults)
            }
            
            Timber.d("User search completed: ${searchResults.size} results")
            searchResults
        }
    }

    override suspend fun getPublicProfile(
        userId: String,
        viewerId: String
    ): LiftrixResult<PublicUserProfile?> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to get public profile") }
        ) {
            Timber.d("Getting public profile: userId=$userId, viewerId=$viewerId")
            
            // Get Firebase public profile
            val document = firestore.collection(USERS_PUBLIC_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (!document.exists()) {
                Timber.w("Public profile not found for user: $userId")
                return@liftrixCatching null
            }

            val data = document.data ?: return@liftrixCatching null
            
            // Check if profile is public
            if (data["isPublic"] as? Boolean != true) {
                Timber.w("Profile is not public for user: $userId")
                return@liftrixCatching null
            }

            // Get connection status
            val connectionStatus = getConnectionStatus(userId, viewerId)
            
            // Create public profile with privacy filtering
            PublicUserProfile(
                userId = userId,
                displayName = data["displayName"] as? String ?: "Unknown User",
                profileImageUrl = data["profileImageUrl"] as? String,
                bio = data["bio"] as? String,
                memberSince = parseTimestamp(data["memberSince"] as? String) ?: LocalDateTime.now(),
                fitnessLevel = null, // TODO: Parse fitness level from data
                isOnline = data["isOnline"] as? Boolean ?: false,
                lastActiveAt = parseTimestamp(data["lastActiveAt"] as? String),
                connectionStatus = connectionStatus,
                publicAchievements = null, // TODO: Implement achievements loading
                publicWorkoutStats = createPublicWorkoutStats(data),
                publicFitnessGoals = null, // TODO: Implement goals loading
                availableEquipment = null // TODO: Implement equipment loading
            )
        }
    }

    override suspend fun generateProfileQRCode(userId: String): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to generate QR code") }
        ) {
            Timber.d("Generating QR code for user: $userId")
            
            val qrCodeId = UUID.randomUUID().toString()
            val createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val expiresAt = LocalDateTime.now().plusDays(QR_CODE_EXPIRY_DAYS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            
            // Store QR code mapping locally
            val qrMapping = QRCodeMappingEntity(
                qrCodeId = qrCodeId,
                userId = userId,
                createdAt = createdAt,
                expiresAt = expiresAt,
                isActive = true,
                usageCount = 0
            )
            
            qrCodeMappingDao.insertOrUpdate(qrMapping)
            
            // Store QR code mapping in Firebase
            val firebaseData = mapOf(
                "userId" to userId,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "expiresAt" to com.google.firebase.Timestamp.now().also { 
                    it.seconds += QR_CODE_EXPIRY_DAYS * 24 * 60 * 60 
                },
                "isActive" to true
            )
            
            firestore.collection(QR_CODES_COLLECTION)
                .document(qrCodeId)
                .set(firebaseData)
                .await()
            
            Timber.d("QR code generated successfully: $qrCodeId")
            qrCodeId
        }
    }

    override suspend fun resolveQRCodeProfile(qrData: String): LiftrixResult<String> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to resolve QR code") }
        ) {
            Timber.d("Resolving QR code: $qrData")
            
            // Check local mapping first
            val localMapping = qrCodeMappingDao.getMapping(qrData)
            if (localMapping != null) {
                // Increment usage count
                qrCodeMappingDao.incrementUsageCount(qrData)
                Timber.d("QR code resolved locally: ${localMapping.userId}")
                return@liftrixCatching localMapping.userId
            }
            
            // Check Firebase mapping
            val document = firestore.collection(QR_CODES_COLLECTION)
                .document(qrData)
                .get()
                .await()
            
            if (!document.exists()) {
                throw LiftrixError.NotFoundError("QR code not found or expired")
            }
            
            val data = document.data ?: throw LiftrixError.NotFoundError("Invalid QR code data")
            val userId = data["userId"] as? String 
                ?: throw LiftrixError.ValidationError("Invalid QR code format")
            val isActive = data["isActive"] as? Boolean ?: false
            
            if (!isActive) {
                throw LiftrixError.ValidationError("QR code is no longer active")
            }
            
            // Store locally for future use
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val localMapping = QRCodeMappingEntity(
                qrCodeId = qrData,
                userId = userId,
                createdAt = now,
                expiresAt = null,
                isActive = true,
                usageCount = 1
            )
            qrCodeMappingDao.insertOrUpdate(localMapping)
            
            Timber.d("QR code resolved from Firebase: $userId")
            userId
        }
    }

    override suspend fun updateSearchKeywords(
        userId: String,
        keywords: List<String>
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable -> LiftrixError.DatabaseError("Failed to update search keywords") }
        ) {
            Timber.d("Updating search keywords for user: $userId (${keywords.size} keywords)")
            
            // Update local profile
            val keywordsJson = gson.toJson(keywords)
            val rowsUpdated = userProfileDao.updateSearchKeywords(userId, keywordsJson)
            
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError("User profile not found for keyword update")
            }
            
            // Update Firebase public profile
            val updateData = mapOf(
                "searchKeywords" to keywords,
                "lastUpdated" to com.google.firebase.Timestamp.now()
            )
            
            firestore.collection(USERS_PUBLIC_COLLECTION)
                .document(userId)
                .update(updateData)
                .await()
            
            Timber.d("Search keywords updated successfully for user: $userId")
        }
    }

    override suspend fun trackProfileView(
        profileUserId: String,
        viewerId: String
    ): LiftrixResult<Unit> {
        return liftrixCatching {
            Timber.d("Tracking profile view: $profileUserId viewed by $viewerId")
            
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            
            // Update local profile view tracking
            userProfileDao.updateProfileView(profileUserId, now)
            
            // Track in Firebase for analytics
            val viewData = mapOf(
                "profileUserId" to profileUserId,
                "viewerId" to viewerId,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            
            firestore.collection("profile_views")
                .add(viewData)
                .await()
            
            Timber.d("Profile view tracked successfully")
        }
    }

    override suspend fun clearSearchCache(userId: String): LiftrixResult<Unit> {
        return liftrixCatching {
            Timber.d("Clearing search cache for user: $userId")
            
            val deletedCount = userSearchCacheDao.deleteAllForUser(userId)
            
            Timber.d("Search cache cleared: $deletedCount entries deleted")
        }
    }

    override suspend fun getCachedSearchResults(
        viewerId: String,
        query: String
    ): LiftrixResult<List<UserSearchResult>?> {
        return liftrixCatching {
            getCachedSearchResultsInternal(viewerId, query)
        }
    }

    /**
     * Internal method to get cached search results
     */
    private suspend fun getCachedSearchResultsInternal(
        viewerId: String,
        query: String
    ): List<UserSearchResult>? {
        val cachedEntity = userSearchCacheDao.getCachedSearchResult(viewerId, query)
        return if (cachedEntity != null) {
            try {
                val type = object : TypeToken<List<UserSearchResult>>() {}.type
                gson.fromJson(cachedEntity.searchResults, type)
            } catch (e: Exception) {
                Timber.w(e, "Failed to deserialize cached search results")
                null
            }
        } else {
            null
        }
    }

    /**
     * Performs Firebase search with privacy filtering
     */
    private suspend fun searchFirebaseUsers(
        query: String,
        currentUserId: String,
        filters: SearchFilters
    ): List<UserSearchResult> {
        try {
            val baseQuery = firestore.collection(USERS_PUBLIC_COLLECTION)
                .whereEqualTo("isPublic", true)
                .limit(MAX_SEARCH_RESULTS.toLong())

            // Apply text search if query is provided
            val searchQuery = if (query.isNotBlank()) {
                baseQuery
                    .whereGreaterThanOrEqualTo("displayNameLower", query.lowercase())
                    .whereLessThanOrEqualTo("displayNameLower", query.lowercase() + "\uf8ff")
            } else {
                baseQuery.orderBy("lastActiveAt", Query.Direction.DESCENDING)
            }

            val documents = searchQuery.get().await()
            val results = mutableListOf<UserSearchResult>()

            for (document in documents.documents) {
                try {
                    if (document.id == currentUserId) continue // Exclude current user

                    val data = document.data ?: continue
                    val userResult = createUserSearchResult(document.id, data, currentUserId)
                    
                    // Apply filters
                    if (matchesFilters(userResult, filters)) {
                        results.add(userResult)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse user search result: ${document.id}")
                }
            }

            return results.take(MAX_SEARCH_RESULTS)
        } catch (e: Exception) {
            Timber.e(e, "Firebase user search failed")
            return emptyList()
        }
    }

    /**
     * Creates UserSearchResult from Firebase document data
     */
    private suspend fun createUserSearchResult(
        userId: String,
        data: Map<String, Any>,
        viewerId: String
    ): UserSearchResult {
        return UserSearchResult(
            userId = userId,
            displayName = data["displayName"] as? String ?: "Unknown User",
            profileImageUrl = data["profileImageUrl"] as? String,
            bio = data["bio"] as? String,
            fitnessLevel = parseFitnessLevel(data["fitnessLevel"] as? String),
            totalWorkouts = (data["totalWorkouts"] as? Long)?.toInt() ?: 0,
            memberSince = parseTimestamp(data["memberSince"] as? String) ?: LocalDateTime.now(),
            sharedEquipment = emptyList(), // TODO: Parse equipment from data
            sharedGoals = emptyList(), // TODO: Parse goals from data
            connectionStatus = getConnectionStatus(userId, viewerId),
            mutualConnections = 0 // TODO: Calculate mutual connections
        )
    }

    /**
     * Caches search results for performance
     */
    private suspend fun cacheSearchResults(
        viewerId: String,
        query: String,
        results: List<UserSearchResult>
    ) {
        try {
            val cacheId = UUID.randomUUID().toString()
            val now = LocalDateTime.now()
            val expiresAt = now.plusHours(CACHE_TTL_HOURS)
            
            val cacheEntity = UserSearchCacheEntity(
                id = cacheId,
                viewerUserId = viewerId,
                searchQuery = query,
                searchResults = gson.toJson(results),
                createdAt = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                expiresAt = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            
            userSearchCacheDao.insertOrUpdate(cacheEntity)
            Timber.d("Search results cached: $query (${results.size} results)")
        } catch (e: Exception) {
            Timber.w(e, "Failed to cache search results")
            // Don't fail the main operation
        }
    }

    /**
     * Applies filters to cached search results
     */
    private fun applyCachedFilters(
        results: List<UserSearchResult>,
        filters: SearchFilters
    ): List<UserSearchResult> {
        return results.filter { user ->
            matchesFilters(user, filters)
        }
    }

    /**
     * Checks if a user matches the given filters
     */
    private fun matchesFilters(user: UserSearchResult, filters: SearchFilters): Boolean {
        // Apply fitness level filter
        if (filters.fitnessLevel != null && user.fitnessLevel != filters.fitnessLevel) {
            return false
        }

        // Apply equipment filter
        if (filters.equipment.isNotEmpty() && 
            !user.sharedEquipment.any { equipment -> filters.equipment.contains(equipment) }) {
            return false
        }

        // Apply goals filter
        if (filters.goals.isNotEmpty() && 
            !user.sharedGoals.any { goal -> filters.goals.contains(goal) }) {
            return false
        }

        // Apply workout count filters
        if (filters.minWorkouts != null && user.totalWorkouts < filters.minWorkouts) {
            return false
        }
        if (filters.maxWorkouts != null && user.totalWorkouts > filters.maxWorkouts) {
            return false
        }

        return true
    }

    /**
     * Gets connection status between two users
     */
    private suspend fun getConnectionStatus(userId: String, viewerId: String): ConnectionStatus {
        // TODO: Implement connection status checking with friends/social repository
        return ConnectionStatus.NONE
    }

    /**
     * Creates public workout stats from Firebase data
     */
    private fun createPublicWorkoutStats(data: Map<String, Any>): PublicWorkoutStats? {
        return try {
            PublicWorkoutStats(
                totalWorkouts = (data["totalWorkouts"] as? Long)?.toInt() ?: 0,
                totalWorkoutTime = (data["totalWorkoutTime"] as? Long) ?: 0L,
                averageWorkoutTime = (data["averageWorkoutTime"] as? Long) ?: 0L,
                currentStreak = (data["currentStreak"] as? Long)?.toInt() ?: 0,
                longestStreak = (data["longestStreak"] as? Long)?.toInt() ?: 0,
                favoriteExercises = emptyList() // TODO: Parse favorite exercises
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to create public workout stats")
            null
        }
    }

    /**
     * Parses fitness level from string
     */
    private fun parseFitnessLevel(level: String?): FitnessLevel? {
        return try {
            level?.let { FitnessLevel.valueOf(it.uppercase()) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses timestamp string to LocalDateTime
     */
    private fun parseTimestamp(timestamp: String?): LocalDateTime? {
        return try {
            timestamp?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        } catch (e: Exception) {
            null
        }
    }
}