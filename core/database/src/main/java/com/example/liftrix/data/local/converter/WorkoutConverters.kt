package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import com.example.liftrix.domain.model.WorkoutStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room type converters for workout-specific types
 */
class WorkoutConverters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromWorkoutStatus(status: WorkoutStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toWorkoutStatus(statusString: String): WorkoutStatus {
        return WorkoutStatus.valueOf(statusString)
    }
    
    @TypeConverter
    fun fromWorkoutStringList(value: List<String>): String {
        return gson.toJson(value)
    }
    
    @TypeConverter
    fun toWorkoutStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
} 