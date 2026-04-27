package com.example.liftrix.data.repository.sharing

import com.example.liftrix.data.local.dao.TemplateShareEventDao
import com.example.liftrix.data.mapper.toDomain
import com.example.liftrix.data.mapper.toEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.sharing.TemplateShareDeliveryMode
import com.example.liftrix.domain.model.sharing.TemplateShareEvent
import com.example.liftrix.domain.model.sharing.TemplateShareStatus
import com.example.liftrix.domain.repository.sharing.TemplateShareRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateShareRepositoryImpl @Inject constructor(
    private val dao: TemplateShareEventDao,
    private val firestore: FirebaseFirestore
) : TemplateShareRepository {

    override suspend fun createShare(event: TemplateShareEvent): LiftrixResult<TemplateShareEvent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DatabaseError(
                errorMessage = "Failed to create template share",
                operation = "CREATE_TEMPLATE_SHARE",
                table = "template_share_events",
                analyticsContext = mapOf("sender_id" to event.senderId)
            )
        }
    ) {
        dao.upsert(event.toEntity())
        uploadEvent(event)
        event
    }

    override suspend fun getPendingSharesFromBuddy(
        senderId: String,
        receiverId: String
    ): LiftrixResult<List<TemplateShareEvent>> = liftrixCatching(
        errorMapper = {
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to resolve pending template shares",
                operation = "GET_PENDING_TEMPLATE_SHARES"
            )
        }
    ) {
        val remoteEvents = fetchPendingRemoteShares(senderId, receiverId)
        if (remoteEvents.isNotEmpty()) {
            dao.upsertAll(remoteEvents.map { it.toEntity(isSynced = true, isDirty = false) })
        }

        dao.getPendingSharesFromBuddy(
            senderId = senderId,
            receiverId = receiverId,
            now = System.currentTimeMillis()
        ).map { it.toDomain() }
    }

    override suspend fun getPendingShareForReceiver(
        shareId: String,
        receiverId: String
    ): LiftrixResult<TemplateShareEvent?> = liftrixCatching(
        errorMapper = {
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to retrieve shared template",
                operation = "GET_TEMPLATE_SHARE"
            )
        }
    ) {
        val remoteEvent = fetchRemoteShare(shareId)
        if (remoteEvent != null) {
            dao.upsert(remoteEvent.toEntity(isSynced = true, isDirty = false))
        }

        dao.getPendingShareForReceiver(
            shareId = shareId,
            receiverId = receiverId,
            now = System.currentTimeMillis()
        )?.toDomain()
    }

    override suspend fun markAccepted(
        shareId: String,
        receiverId: String,
        acceptedAt: Long
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = {
            LiftrixError.DatabaseError(
                errorMessage = "Failed to accept template share",
                operation = "ACCEPT_TEMPLATE_SHARE",
                table = "template_share_events"
            )
        }
    ) {
        val updatedRows = dao.markAccepted(shareId, receiverId, acceptedAt)
        if (updatedRows == 0) {
            throw IllegalStateException("Template share was not pending or not visible to this receiver")
        }

        firestore.collection(COLLECTION)
            .document(shareId)
            .update(
                mapOf(
                    "status" to TemplateShareStatus.ACCEPTED.name,
                    "receiverId" to receiverId,
                    "acceptedAt" to acceptedAt,
                    "lastModified" to acceptedAt
                )
            )
            .await()
    }

    private suspend fun uploadEvent(event: TemplateShareEvent) {
        firestore.collection(COLLECTION)
            .document(event.id)
            .set(event.toFirestoreMap())
            .await()
    }

    private suspend fun fetchRemoteShare(shareId: String): TemplateShareEvent? {
        return runCatching {
            firestore.collection(COLLECTION)
                .document(shareId)
                .get()
                .await()
                .data
                ?.toTemplateShareEvent()
        }.onFailure { Timber.w(it, "Failed to fetch remote template share $shareId") }
            .getOrNull()
    }

    private suspend fun fetchPendingRemoteShares(senderId: String, receiverId: String): List<TemplateShareEvent> {
        val now = System.currentTimeMillis()
        val direct = runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("receiverId", receiverId)
                .whereEqualTo("status", TemplateShareStatus.PENDING.name)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toTemplateShareEvent() }
        }.getOrElse {
            Timber.w(it, "Failed to fetch direct pending template shares")
            emptyList()
        }

        val qr = runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("senderId", senderId)
                .whereEqualTo("deliveryMode", TemplateShareDeliveryMode.QR.name)
                .whereEqualTo("status", TemplateShareStatus.PENDING.name)
                .get()
                .await()
                .documents
                .mapNotNull { it.data?.toTemplateShareEvent() }
        }.getOrElse {
            Timber.w(it, "Failed to fetch QR pending template shares")
            emptyList()
        }

        return (direct + qr)
            .distinctBy { it.id }
            .filter { it.isPendingAt(now) && (it.receiverId == null || it.receiverId == receiverId) }
    }

    private fun TemplateShareEvent.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "senderId" to senderId,
            "receiverId" to receiverId,
            "templateId" to templateId,
            "deliveryMode" to deliveryMode.name,
            "status" to status.name,
            "createdAt" to createdAt,
            "expiresAt" to expiresAt,
            "acceptedAt" to acceptedAt,
            "lastModified" to System.currentTimeMillis()
        )
    }

    private fun Map<String, Any>.toTemplateShareEvent(): TemplateShareEvent? {
        return try {
            TemplateShareEvent(
                id = this["id"] as String,
                senderId = this["senderId"] as String,
                receiverId = this["receiverId"] as? String,
                templateId = this["templateId"] as String,
                deliveryMode = TemplateShareDeliveryMode.valueOf(this["deliveryMode"] as String),
                status = TemplateShareStatus.valueOf(this["status"] as String),
                createdAt = (this["createdAt"] as Number).toLong(),
                expiresAt = (this["expiresAt"] as Number).toLong(),
                acceptedAt = (this["acceptedAt"] as? Number)?.toLong()
            )
        } catch (exception: Exception) {
            Timber.w(exception, "Invalid template share Firestore payload")
            null
        }
    }

    private companion object {
        const val COLLECTION = "template_share_events"
    }
}

