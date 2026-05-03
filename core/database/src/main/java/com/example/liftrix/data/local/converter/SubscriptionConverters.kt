package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import com.example.liftrix.data.local.entity.SubscriptionTier

/**
 * Room type converters for subscription-related types.
 * Handles serialization of subscription enums to/from database storage.
 */
class SubscriptionConverters {

    @TypeConverter
    fun fromSubscriptionTier(tier: SubscriptionTier): String {
        return tier.name
    }

    @TypeConverter
    fun toSubscriptionTier(tierString: String): SubscriptionTier {
        return SubscriptionTier.valueOf(tierString)
    }
}