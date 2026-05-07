package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.SupportTicketEntity
import com.example.liftrix.domain.model.support.DeviceInfo
import com.example.liftrix.domain.model.support.SupportCategory
import com.example.liftrix.domain.model.support.SupportStatus
import com.example.liftrix.domain.model.support.SupportTicket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Mapper for converting between SupportTicket domain model and SupportTicketEntity
 */
object SupportTicketMapper {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Converts SupportTicketEntity to SupportTicket domain model
     */
    fun SupportTicketEntity.toDomainModel(): SupportTicket = SupportTicket(
        id = ticketId,
        userId = userId,
        category = SupportCategory.valueOf(category),
        subject = subject,
        description = description,
        deviceInfo = deviceInfo?.let { 
            try {
                json.decodeFromString<DeviceInfo>(it)
            } catch (e: Exception) {
                null
            }
        },
        appVersion = appVersion,
        status = SupportStatus.valueOf(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced
    )
    
    /**
     * Converts SupportTicket domain model to SupportTicketEntity
     */
    fun SupportTicket.toEntity(): SupportTicketEntity = SupportTicketEntity(
        ticketId = id,
        userId = userId,
        category = category.name,
        subject = subject,
        description = description,
        deviceInfo = deviceInfo?.let { 
            try {
                json.encodeToString(it)
            } catch (e: Exception) {
                null
            }
        },
        appVersion = appVersion,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced,
        syncVersion = 1
    )
    
    /**
     * Converts list of SupportTicketEntity to list of SupportTicket domain models
     */
    fun List<SupportTicketEntity>.toDomainModels(): List<SupportTicket> = 
        map { it.toDomainModel() }
    
    /**
     * Converts list of SupportTicket domain models to list of SupportTicketEntity
     */
    fun List<SupportTicket>.toEntities(): List<SupportTicketEntity> = 
        map { it.toEntity() }
}