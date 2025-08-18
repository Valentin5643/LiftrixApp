package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.liftrix.data.local.converter.DateTimeConverters
import java.time.Instant

/**
 * Room entity representing application configuration in local database
 * Stores dynamic configuration values that can be updated remotely
 * 
 * Uses snake_case column names following existing database conventions
 * and supports different configuration types (STRING, INT, BOOLEAN, JSON)
 */
@Entity(tableName = "app_config")
@TypeConverters(DateTimeConverters::class)
data class AppConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "config_key")
    val configKey: String,
    
    @ColumnInfo(name = "config_value")
    val configValue: String,
    
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,
    
    @ColumnInfo(name = "config_type")
    val configType: String = "STRING", // STRING, INT, BOOLEAN, JSON
    
    @ColumnInfo(name = "is_cached")
    val isCached: Boolean = true
) {
    companion object {
        /**
         * Creates a string configuration entry
         * 
         * @param key Configuration key
         * @param value String value
         * @param cached Whether this config should be cached locally
         * @return AppConfigEntity with STRING type
         */
        fun createString(
            key: String,
            value: String,
            cached: Boolean = true
        ): AppConfigEntity = AppConfigEntity(
            configKey = key,
            configValue = value,
            lastUpdated = Instant.now(),
            configType = ConfigType.STRING,
            isCached = cached
        )
        
        /**
         * Creates an integer configuration entry
         * 
         * @param key Configuration key
         * @param value Integer value
         * @param cached Whether this config should be cached locally
         * @return AppConfigEntity with INT type
         */
        fun createInt(
            key: String,
            value: Int,
            cached: Boolean = true
        ): AppConfigEntity = AppConfigEntity(
            configKey = key,
            configValue = value.toString(),
            lastUpdated = Instant.now(),
            configType = ConfigType.INT,
            isCached = cached
        )
        
        /**
         * Creates a boolean configuration entry
         * 
         * @param key Configuration key
         * @param value Boolean value
         * @param cached Whether this config should be cached locally
         * @return AppConfigEntity with BOOLEAN type
         */
        fun createBoolean(
            key: String,
            value: Boolean,
            cached: Boolean = true
        ): AppConfigEntity = AppConfigEntity(
            configKey = key,
            configValue = value.toString(),
            lastUpdated = Instant.now(),
            configType = ConfigType.BOOLEAN,
            isCached = cached
        )
        
        /**
         * Creates a JSON configuration entry
         * 
         * @param key Configuration key
         * @param value JSON string value
         * @param cached Whether this config should be cached locally
         * @return AppConfigEntity with JSON type
         */
        fun createJson(
            key: String,
            value: String,
            cached: Boolean = true
        ): AppConfigEntity = AppConfigEntity(
            configKey = key,
            configValue = value,
            lastUpdated = Instant.now(),
            configType = ConfigType.JSON,
            isCached = cached
        )
        
        /**
         * Configuration type constants
         */
        object ConfigType {
            const val STRING = "STRING"
            const val INT = "INT"
            const val BOOLEAN = "BOOLEAN"
            const val JSON = "JSON"
        }
        
        /**
         * Common configuration keys used throughout the app
         */
        object Keys {
            const val HELP_CONTENT_VERSION = "help_content_version"
            const val PRIVACY_POLICY_URL = "privacy_policy_url"
            const val TERMS_OF_SERVICE_URL = "terms_of_service_url"
            const val SUPPORT_EMAIL = "support_email"
            const val MIN_APP_VERSION = "min_app_version"
            const val FEATURE_FLAGS = "feature_flags"
            const val HELP_CATEGORIES = "help_categories"
            const val POPULAR_ARTICLES = "popular_articles"
            const val SUPPORT_CATEGORIES = "support_categories"
        }
    }
    
    /**
     * Gets the configuration value as a string
     * @return String value
     */
    fun getStringValue(): String = configValue
    
    /**
     * Gets the configuration value as an integer
     * @return Integer value, or 0 if parsing fails
     */
    fun getIntValue(): Int = configValue.toIntOrNull() ?: 0
    
    /**
     * Gets the configuration value as a boolean
     * @return Boolean value, or false if parsing fails
     */
    fun getBooleanValue(): Boolean = configValue.toBooleanStrictOrNull() ?: false
    
    /**
     * Updates the configuration value with a new timestamp
     * @param newValue The new configuration value
     * @return Updated entity with new value and timestamp
     */
    fun updateValue(newValue: String): AppConfigEntity = copy(
        configValue = newValue,
        lastUpdated = Instant.now()
    )
    
    /**
     * Checks if the configuration has expired based on a TTL
     * @param ttlSeconds Time-to-live in seconds
     * @return True if the config is older than the TTL
     */
    fun isExpired(ttlSeconds: Long): Boolean {
        val expiryTime = lastUpdated.plusSeconds(ttlSeconds)
        return Instant.now().isAfter(expiryTime)
    }
}