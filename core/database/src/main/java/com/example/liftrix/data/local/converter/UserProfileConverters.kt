package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room type converters for user profile specific types.
 * Uses Gson for serializing complex data structures to JSON strings.
 */
class UserProfileConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromEquipmentList(equipment: List<Equipment>?): String? {
        return gson.toJson(equipment)
    }

    @TypeConverter
    fun toEquipmentList(equipmentString: String?): List<Equipment>? {
        if (equipmentString == null) return null
        val listType = object : TypeToken<List<Equipment>>() {}.type
        return gson.fromJson(equipmentString, listType)
    }

    @TypeConverter
    fun fromFitnessGoalList(goals: List<FitnessGoal>?): String? {
        return gson.toJson(goals)
    }

    @TypeConverter
    fun toFitnessGoalList(goalsString: String?): List<FitnessGoal>? {
        if (goalsString == null) return null
        val listType = object : TypeToken<List<FitnessGoal>>() {}.type
        return gson.fromJson(goalsString, listType)
    }

    @TypeConverter
    fun fromGoalsPriorityMap(priorityMap: Map<FitnessGoal, Int>?): String? {
        return gson.toJson(priorityMap)
    }

    @TypeConverter
    fun toGoalsPriorityMap(priorityMapString: String?): Map<FitnessGoal, Int>? {
        if (priorityMapString == null) return null
        val mapType = object : TypeToken<Map<FitnessGoal, Int>>() {}.type
        return gson.fromJson(priorityMapString, mapType)
    }
} 