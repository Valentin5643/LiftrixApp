package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.SettingsEntity
import com.example.liftrix.domain.model.UserSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper class for converting between SettingsEntity and UserSettings domain model.
 * 
 * This mapper handles the conversion between the data layer's SettingsEntity
 * and the domain layer's UserSettings model, ensuring proper data transformation
 * and validation.
 */
@Singleton
class SettingsMapper @Inject constructor() {
    
    /**
     * Converts a SettingsEntity to UserSettings domain model.
     * 
     * @param entity The SettingsEntity to convert
     * @return UserSettings domain model
     */
    fun toDomain(entity: SettingsEntity): UserSettings {
        return UserSettings(
            userId = entity.userId,
            darkMode = entity.darkMode,
            notificationsEnabled = entity.notificationsEnabled,
            weightUnit = entity.weightUnit,
            updatedAt = entity.updatedAt
        )
    }
    
    /**
     * Converts a UserSettings domain model to SettingsEntity.
     * 
     * @param domain The UserSettings domain model to convert
     * @return SettingsEntity for database storage
     */
    fun toEntity(domain: UserSettings): SettingsEntity {
        return SettingsEntity(
            userId = domain.userId,
            darkMode = domain.darkMode,
            notificationsEnabled = domain.notificationsEnabled,
            weightUnit = domain.weightUnit,
            updatedAt = domain.updatedAt
        )
    }
    
    /**
     * Converts a list of SettingsEntity to a list of UserSettings.
     * 
     * @param entities The list of SettingsEntity to convert
     * @return List of UserSettings domain models
     */
    fun toDomain(entities: List<SettingsEntity>): List<UserSettings> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * Converts a list of UserSettings to a list of SettingsEntity.
     * 
     * @param domains The list of UserSettings to convert
     * @return List of SettingsEntity for database storage
     */
    fun toEntity(domains: List<UserSettings>): List<SettingsEntity> {
        return domains.map { toEntity(it) }
    }
}