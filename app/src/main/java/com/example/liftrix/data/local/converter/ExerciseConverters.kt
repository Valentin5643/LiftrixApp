package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room type converters for exercise-specific types
 */
class ExerciseConverters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromExerciseCategory(category: ExerciseCategory): String {
        return category.name
    }
    
    @TypeConverter
    fun toExerciseCategory(categoryString: String): ExerciseCategory {
        return ExerciseCategory.valueOf(categoryString)
    }
    
    @TypeConverter
    fun fromEquipment(equipment: Equipment): String {
        return equipment.name
    }
    
    @TypeConverter
    fun toEquipment(equipmentString: String): Equipment {
        return Equipment.valueOf(equipmentString)
    }
    
    @TypeConverter
    fun fromExerciseCategoryList(categories: List<ExerciseCategory>?): String? {
        return if (categories == null) null else gson.toJson(categories.map { it.name })
    }
    
    @TypeConverter
    fun toExerciseCategoryList(categoriesJson: String?): List<ExerciseCategory>? {
        if (categoriesJson == null) return null
        val type = object : TypeToken<List<String>>() {}.type
        val categoryNames: List<String> = gson.fromJson(categoriesJson, type)
        return categoryNames.map { ExerciseCategory.valueOf(it) }
    }
    
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return if (value == null) null else gson.toJson(value)
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
    
} 