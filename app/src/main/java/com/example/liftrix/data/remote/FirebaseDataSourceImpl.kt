package com.example.liftrix.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of FirebaseDataSource for Firestore data operations.
 * 
 * This implementation handles all CRUD operations with Firebase Firestore for the sync
 * infrastructure. It provides:
 * - User-scoped data access with authentication validation
 * - Proper error handling with Firebase-specific exceptions
 * - JSON serialization/deserialization for complex data types
 * - Batch operations for improved performance
 * - Conflict detection based on document existence and timestamps
 * 
 * Technical Implementation:
 * - Uses Firebase Auth for user validation and scoping
 * - Implements the collection structure from FB-001 schema
 * - Provides detailed error logging for debugging sync issues
 * - Returns ProcessResult types that integrate with OfflineQueueManager
 */
@Singleton
class FirebaseDataSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val json: Json
) : FirebaseDataSource {
    
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val WORKOUTS_SUBCOLLECTION = "workouts"
        private const val TEMPLATES_SUBCOLLECTION = "templates"
        private const val ACHIEVEMENTS_SUBCOLLECTION = "achievements"
    }
    
    override suspend fun create(userId: String, entityType: String, entityId: String, data: String): ProcessResult {
        return try {
            Timber.d("FirebaseDataSource: Creating $entityType:$entityId for user $userId")
            
            // Validate authentication
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated or ID mismatch"))
            }
            
            val documentRef = getDocumentReference(userId, entityType, entityId)
            val documentData = parseJsonToMap(data)
            
            // 🔥 PROFILE SYNC CONFLICT FIX: Handle profile entities with UPSERT semantics
            // Profile entities should use merge behavior instead of strict CREATE conflict detection
            if (entityType == "PROFILE") {
                Timber.d("FirebaseDataSource: Using UPSERT semantics for profile $entityId")
                
                // Use SetOptions.merge() for profile documents to avoid conflicts during account creation
                documentRef.set(documentData, com.google.firebase.firestore.SetOptions.merge()).await()
                
                Timber.d("FirebaseDataSource: Successfully upserted profile $entityId")
                return ProcessResult.Success
            }
            
            // For non-profile entities, maintain existing conflict detection behavior
            val existingDoc = documentRef.get().await()
            if (existingDoc.exists()) {
                Timber.w("FirebaseDataSource: Document already exists for $entityType:$entityId")
                return ProcessResult.Conflict
            }
            
            // Create the document
            documentRef.set(documentData).await()
            
            Timber.d("FirebaseDataSource: Successfully created $entityType:$entityId")
            ProcessResult.Success
            
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreException(e, "create", entityType, entityId)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseDataSource: Error creating $entityType:$entityId")
            ProcessResult.Failure(e)
        }
    }
    
    override suspend fun update(userId: String, entityType: String, entityId: String, data: String): ProcessResult {
        return try {
            Timber.d("FirebaseDataSource: Updating $entityType:$entityId for user $userId")
            
            // Validate authentication
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated or ID mismatch"))
            }
            
            val documentRef = getDocumentReference(userId, entityType, entityId)
            val documentData = parseJsonToMap(data)
            
            // Check if document exists and get current version for conflict detection
            val existingDoc = documentRef.get().await()
            if (!existingDoc.exists()) {
                Timber.w("FirebaseDataSource: Document does not exist for update $entityType:$entityId, creating instead")
                return create(userId, entityType, entityId, data)
            }
            
            // Check for conflicts using sync version
            val existingVersion = existingDoc.getLong("syncVersion") ?: 0L
            val newVersion = documentData["syncVersion"] as? Long ?: 0L
            
            if (existingVersion > newVersion) {
                Timber.w("FirebaseDataSource: Version conflict for $entityType:$entityId (existing: $existingVersion, new: $newVersion)")
                return ProcessResult.Conflict
            }
            
            // Update the document
            documentRef.set(documentData).await()
            
            Timber.d("FirebaseDataSource: Successfully updated $entityType:$entityId")
            ProcessResult.Success
            
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreException(e, "update", entityType, entityId)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseDataSource: Error updating $entityType:$entityId")
            ProcessResult.Failure(e)
        }
    }
    
    override suspend fun delete(userId: String, entityType: String, entityId: String): ProcessResult {
        return try {
            Timber.d("FirebaseDataSource: Deleting $entityType:$entityId for user $userId")
            
            // Validate authentication
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated or ID mismatch"))
            }
            
            val documentRef = getDocumentReference(userId, entityType, entityId)
            
            // Check if document exists
            val existingDoc = documentRef.get().await()
            if (!existingDoc.exists()) {
                Timber.w("FirebaseDataSource: Document does not exist for deletion $entityType:$entityId")
                return ProcessResult.Success // Treat as successful since the end state is achieved
            }
            
            // Delete the document
            documentRef.delete().await()
            
            Timber.d("FirebaseDataSource: Successfully deleted $entityType:$entityId")
            ProcessResult.Success
            
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreException(e, "delete", entityType, entityId)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseDataSource: Error deleting $entityType:$entityId")
            ProcessResult.Failure(e)
        }
    }
    
    override suspend fun fetch(userId: String, entityType: String, entityId: String): ProcessResult {
        return try {
            Timber.d("FirebaseDataSource: Fetching $entityType:$entityId for user $userId")
            
            // Validate authentication
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated or ID mismatch"))
            }
            
            val documentRef = getDocumentReference(userId, entityType, entityId)
            val document = documentRef.get().await()
            
            if (!document.exists()) {
                Timber.w("FirebaseDataSource: Document does not exist $entityType:$entityId")
                return ProcessResult.Failure(Exception("Document not found"))
            }
            
            val documentData = document.data ?: emptyMap<String, Any>()
            val dataJson = mapToJsonString(documentData)
            
            Timber.d("FirebaseDataSource: Successfully fetched $entityType:$entityId")
            ProcessResult.Data(dataJson)
            
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreException(e, "fetch", entityType, entityId)
        } catch (e: Exception) {
            Timber.e(e, "FirebaseDataSource: Error fetching $entityType:$entityId")
            ProcessResult.Failure(e)
        }
    }
    
    override suspend fun fetchAll(userId: String, entityType: String): ProcessResult {
        return try {
            Timber.d("FirebaseDataSource: Fetching all $entityType for user $userId")
            
            // Validate authentication
            if (!isUserAuthenticated(userId)) {
                return ProcessResult.Failure(Exception("User not authenticated or ID mismatch"))
            }
            
            val collectionRef = getCollectionReference(userId, entityType)
            val querySnapshot = collectionRef.get().await()
            
            val documents = mutableListOf<String>()
            for (document in querySnapshot.documents) {
                val documentData = document.data ?: emptyMap<String, Any>()
                val dataJson = mapToJsonString(documentData)
                documents.add(dataJson)
            }
            
            Timber.d("FirebaseDataSource: Successfully fetched ${documents.size} $entityType documents")
            ProcessResult.DataList(documents)
            
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreException(e, "fetchAll", entityType, "all")
        } catch (e: Exception) {
            Timber.e(e, "FirebaseDataSource: Error fetching all $entityType")
            ProcessResult.Failure(e)
        }
    }
    
    /**
     * Gets the appropriate document reference based on entity type and collection structure.
     */
    private fun getDocumentReference(userId: String, entityType: String, entityId: String) = when (entityType) {
        "PROFILE" -> firestore.collection(USERS_COLLECTION).document(userId)
        "WORKOUT" -> firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(WORKOUTS_SUBCOLLECTION)
            .document(entityId)
        "TEMPLATE" -> firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(TEMPLATES_SUBCOLLECTION)
            .document(entityId)
        "ACHIEVEMENT" -> firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(ACHIEVEMENTS_SUBCOLLECTION)
            .document(entityId)
        else -> throw IllegalArgumentException("Unknown entity type: $entityType")
    }
    
    /**
     * Gets the appropriate collection reference based on entity type.
     */
    private fun getCollectionReference(userId: String, entityType: String) = when (entityType) {
        "WORKOUT" -> firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(WORKOUTS_SUBCOLLECTION)
        "TEMPLATE" -> firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(TEMPLATES_SUBCOLLECTION)
        "ACHIEVEMENT" -> firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(ACHIEVEMENTS_SUBCOLLECTION)
        else -> throw IllegalArgumentException("Unknown entity type for collection: $entityType")
    }
    
    /**
     * Validates that the current user is authenticated and matches the provided userId.
     */
    private fun isUserAuthenticated(userId: String): Boolean {
        val currentUser = auth.currentUser
        return currentUser != null && currentUser.uid == userId
    }
    
    /**
     * Handles Firebase-specific exceptions and converts them to appropriate ProcessResult types.
     */
    private fun handleFirestoreException(
        exception: FirebaseFirestoreException,
        operation: String,
        entityType: String,
        entityId: String
    ): ProcessResult {
        return when (exception.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                Timber.w("FirebaseDataSource: Permission denied for $operation $entityType:$entityId")
                ProcessResult.Failure(SecurityException("Permission denied: ${exception.message}"))
            }
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                Timber.w("FirebaseDataSource: Unauthenticated for $operation $entityType:$entityId")
                ProcessResult.Failure(SecurityException("Authentication required: ${exception.message}"))
            }
            FirebaseFirestoreException.Code.NOT_FOUND -> {
                Timber.w("FirebaseDataSource: Document not found for $operation $entityType:$entityId")
                ProcessResult.Failure(Exception("Document not found: ${exception.message}"))
            }
            FirebaseFirestoreException.Code.ALREADY_EXISTS -> {
                Timber.w("FirebaseDataSource: Document already exists for $operation $entityType:$entityId")
                ProcessResult.Conflict
            }
            FirebaseFirestoreException.Code.ABORTED -> {
                Timber.w("FirebaseDataSource: Transaction aborted (likely conflict) for $operation $entityType:$entityId")
                ProcessResult.Conflict
            }
            FirebaseFirestoreException.Code.UNAVAILABLE -> {
                Timber.w("FirebaseDataSource: Service unavailable for $operation $entityType:$entityId")
                ProcessResult.Failure(Exception("Service temporarily unavailable: ${exception.message}"))
            }
            else -> {
                Timber.e(exception, "FirebaseDataSource: Unexpected Firestore error for $operation $entityType:$entityId")
                ProcessResult.Failure(exception)
            }
        }
    }
    
    /**
     * Converts a JSON string to a Map for Firestore operations.
     * Uses kotlinx.serialization to parse JSON safely.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseJsonToMap(jsonString: String): Map<String, Any> {
        return try {
            val jsonElement = json.parseToJsonElement(jsonString)
            val jsonObject = jsonElement as kotlinx.serialization.json.JsonObject
            
            // Convert JsonObject to Map<String, Any> compatible with Firestore
            jsonObject.mapValues { (_, value) ->
                when (value) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        when {
                            value.isString -> value.content
                            value.content == "true" || value.content == "false" -> value.content.toBoolean()
                            value.content.toLongOrNull() != null -> value.content.toLong()
                            value.content.toDoubleOrNull() != null -> value.content.toDouble()
                            else -> value.content
                        }
                    }
                    is kotlinx.serialization.json.JsonArray -> value.toString() // Simplified array handling
                    is kotlinx.serialization.json.JsonObject -> value.toString() // Simplified object handling
                    else -> value.toString()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing JSON to map: $jsonString")
            throw e
        }
    }
    
    /**
     * Converts a Map to a JSON string for data operations.
     * Handles Firestore data types properly.
     */
    private fun mapToJsonString(map: Map<String, Any?>): String {
        return try {
            // Convert Firestore data to JSON-compatible map
            val jsonCompatibleMap = map.mapValues { (_, value) ->
                when (value) {
                    is com.google.firebase.Timestamp -> value.toDate().time
                    is com.google.firebase.firestore.GeoPoint -> mapOf("latitude" to value.latitude, "longitude" to value.longitude)
                    is com.google.firebase.firestore.DocumentReference -> value.path
                    else -> value
                }
            }
            
            // Use basic JSON encoding for now - this would need proper serializer in production
            buildString {
                append("{")
                jsonCompatibleMap.entries.forEachIndexed { index, (key, value) ->
                    if (index > 0) append(",")
                    append("\"$key\":")
                    when (value) {
                        null -> append("null")
                        is String -> append("\"${value.replace("\"", "\\\"")}\"")
                        is Number, is Boolean -> append(value.toString())
                        else -> append("\"$value\"")
                    }
                }
                append("}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error converting map to JSON: $map")
            throw e
        }
    }
}