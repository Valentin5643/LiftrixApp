package com.example.liftrix.data.remote.legacy

import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyCustomExerciseFirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val COLLECTION_CUSTOM_EXERCISES = "custom_exercises"
    }

    suspend fun syncExercise(entity: CustomExerciseEntity) {
        val firestoreData = mapOf(
            "id" to entity.id,
            "user_id" to entity.userId,
            "name" to entity.name,
            "description" to entity.description,
            "exerciseType" to entity.exerciseType.name,
            "primaryMuscleGroup" to entity.primaryMuscleGroup.name,
            "equipment" to entity.equipment.name,
            "secondaryMuscleGroups" to (entity.secondaryMuscleGroups?.map { it.name } ?: emptyList()),
            "difficulty" to entity.difficulty,
            "instructions" to (entity.instructions ?: emptyList()),
            "mainImageUrl" to entity.mainImageUrl,
            "additionalImageUrls" to (entity.additionalImageUrls ?: emptyList()),
            "videoUrl" to entity.videoUrl,
            "tags" to (entity.tags ?: emptyList()),
            "categories" to (entity.categories?.map { it.name } ?: emptyList()),
            "notes" to entity.notes,
            "createdAt" to entity.createdAt,
            "updatedAt" to entity.updatedAt,
            "syncVersion" to entity.syncVersion,
            "lastModified" to entity.lastModified
        )

        firestore.collection(COLLECTION_CUSTOM_EXERCISES)
            .document(entity.id)
            .set(firestoreData)
            .await()
    }

    suspend fun deleteExercise(exerciseId: String) {
        try {
            firestore.collection(COLLECTION_CUSTOM_EXERCISES)
                .document(exerciseId)
                .delete()
                .await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete custom exercise $exerciseId from Firestore")
            throw e
        }
    }
}
