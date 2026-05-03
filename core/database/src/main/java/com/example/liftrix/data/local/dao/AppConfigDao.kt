package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Data Access Object for application configuration operations
 * 
 * Provides methods to retrieve, insert, and update app configuration values
 * Supports caching strategies and configuration type filtering
 */
@Dao
interface AppConfigDao {
    
    /**
     * Retrieves all configuration entries
     * @return Flow of all app configuration entries
     */
    @Query("SELECT * FROM app_config ORDER BY config_key ASC")
    fun getAllConfigs(): Flow<List<AppConfigEntity>>
    
    /**
     * Retrieves all configuration entries synchronously
     * @return List of all app configuration entries
     */
    @Query("SELECT * FROM app_config ORDER BY config_key ASC")
    suspend fun getAllConfigsSync(): List<AppConfigEntity>
    
    /**
     * Retrieves a specific configuration value by key
     * @param key The configuration key
     * @return Flow of AppConfigEntity or null if not found
     */
    @Query("SELECT * FROM app_config WHERE config_key = :key")
    fun getConfig(key: String): Flow<AppConfigEntity?>
    
    /**
     * Retrieves a specific configuration value synchronously
     * @param key The configuration key
     * @return AppConfigEntity or null if not found
     */
    @Query("SELECT * FROM app_config WHERE config_key = :key")
    suspend fun getConfigSync(key: String): AppConfigEntity?
    
    /**
     * Retrieves configuration value as string
     * @param key The configuration key
     * @return Configuration value as string or null if not found
     */
    @Query("SELECT config_value FROM app_config WHERE config_key = :key")
    suspend fun getConfigValue(key: String): String?
    
    /**
     * Retrieves configurations by type
     * @param type The configuration type (STRING, INT, BOOLEAN, JSON)
     * @return Flow of configurations matching the type
     */
    @Query("SELECT * FROM app_config WHERE config_type = :type ORDER BY config_key ASC")
    fun getConfigsByType(type: String): Flow<List<AppConfigEntity>>
    
    /**
     * Retrieves cached configurations only
     * @return Flow of cached configuration entries
     */
    @Query("SELECT * FROM app_config WHERE is_cached = 1 ORDER BY config_key ASC")
    fun getCachedConfigs(): Flow<List<AppConfigEntity>>
    
    /**
     * Retrieves configurations updated after a specific timestamp
     * @param timestamp The timestamp to filter by
     * @return Flow of recently updated configurations
     */
    @Query("SELECT * FROM app_config WHERE last_updated > :timestamp ORDER BY last_updated DESC")
    fun getConfigsUpdatedAfter(timestamp: Instant): Flow<List<AppConfigEntity>>
    
    /**
     * Retrieves expired configurations based on TTL
     * @param expiryTimestamp Configurations older than this are considered expired
     * @return Flow of expired configurations
     */
    @Query("SELECT * FROM app_config WHERE last_updated < :expiryTimestamp AND is_cached = 1")
    fun getExpiredConfigs(expiryTimestamp: Instant): Flow<List<AppConfigEntity>>
    
    /**
     * Gets configurations matching a key pattern
     * @param pattern Key pattern to match (use % for wildcards)
     * @return Flow of matching configurations
     */
    @Query("SELECT * FROM app_config WHERE config_key LIKE :pattern ORDER BY config_key ASC")
    fun getConfigsLike(pattern: String): Flow<List<AppConfigEntity>>
    
    /**
     * Inserts a new configuration or replaces existing one
     * @param config The configuration entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppConfigEntity)
    
    /**
     * Inserts multiple configurations
     * @param configs List of configuration entities to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<AppConfigEntity>)
    
    /**
     * Updates an existing configuration
     * @param config The configuration entity to update
     */
    @Update
    suspend fun updateConfig(config: AppConfigEntity)
    
    /**
     * Updates a configuration value with current timestamp
     * @param key The configuration key
     * @param value The new configuration value
     * @param updatedAt The timestamp of the update
     */
    @Query("""
        UPDATE app_config 
        SET config_value = :value, last_updated = :updatedAt 
        WHERE config_key = :key
    """)
    suspend fun updateConfigValue(key: String, value: String, updatedAt: Instant = Instant.now())
    
    /**
     * Sets or updates a string configuration
     * @param key The configuration key
     * @param value The string value
     * @param cached Whether this config should be cached
     */
    suspend fun setStringConfig(key: String, value: String, cached: Boolean = true) {
        insertConfig(AppConfigEntity.createString(key, value, cached))
    }
    
    /**
     * Sets or updates an integer configuration
     * @param key The configuration key
     * @param value The integer value
     * @param cached Whether this config should be cached
     */
    suspend fun setIntConfig(key: String, value: Int, cached: Boolean = true) {
        insertConfig(AppConfigEntity.createInt(key, value, cached))
    }
    
    /**
     * Sets or updates a boolean configuration
     * @param key The configuration key
     * @param value The boolean value
     * @param cached Whether this config should be cached
     */
    suspend fun setBooleanConfig(key: String, value: Boolean, cached: Boolean = true) {
        insertConfig(AppConfigEntity.createBoolean(key, value, cached))
    }
    
    /**
     * Sets or updates a JSON configuration
     * @param key The configuration key
     * @param value The JSON string value
     * @param cached Whether this config should be cached
     */
    suspend fun setJsonConfig(key: String, value: String, cached: Boolean = true) {
        insertConfig(AppConfigEntity.createJson(key, value, cached))
    }
    
    /**
     * Deletes a specific configuration
     * @param key The configuration key
     */
    @Query("DELETE FROM app_config WHERE config_key = :key")
    suspend fun deleteConfig(key: String)
    
    /**
     * Deletes configurations matching a pattern
     * @param pattern Key pattern to match (use % for wildcards)
     */
    @Query("DELETE FROM app_config WHERE config_key LIKE :pattern")
    suspend fun deleteConfigsLike(pattern: String)
    
    /**
     * Deletes expired configurations
     * @param expiryTimestamp Configurations older than this will be deleted
     */
    @Query("DELETE FROM app_config WHERE last_updated < :expiryTimestamp AND is_cached = 1")
    suspend fun deleteExpiredConfigs(expiryTimestamp: Instant)
    
    /**
     * Deletes all configurations (for testing or reset)
     */
    @Query("DELETE FROM app_config")
    suspend fun deleteAllConfigs()
    
    /**
     * Gets the total number of configurations
     * @return Total count of configurations
     */
    @Query("SELECT COUNT(*) FROM app_config")
    suspend fun getConfigCount(): Int
    
    /**
     * Checks if a configuration exists
     * @param key The configuration key
     * @return True if configuration exists
     */
    @Query("SELECT COUNT(*) > 0 FROM app_config WHERE config_key = :key")
    suspend fun hasConfig(key: String): Boolean
    
    /**
     * Gets the most recently updated configuration
     * @return The most recently updated configuration or null
     */
    @Query("SELECT * FROM app_config ORDER BY last_updated DESC LIMIT 1")
    suspend fun getMostRecentConfig(): AppConfigEntity?
    
    /**
     * Gets configuration counts by type
     * @return Map of type to count
     */
    @Query("SELECT config_type, COUNT(*) as count FROM app_config GROUP BY config_type")
    suspend fun getConfigCountsByType(): Map<@MapColumn(columnName = "config_type") String, @MapColumn(columnName = "count") Int>
    
    /**
     * Batch updates multiple configuration values
     * @param updates Map of key to value pairs
     * @param timestamp The update timestamp
     */
    suspend fun batchUpdateConfigs(updates: Map<String, String>, timestamp: Instant = Instant.now()) {
        updates.forEach { (key, value) ->
            updateConfigValue(key, value, timestamp)
        }
    }
}