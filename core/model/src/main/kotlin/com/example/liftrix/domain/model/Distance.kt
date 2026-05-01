@file:JvmName("DistanceKt")

package com.example.liftrix.domain.model

/**
 * Value class representing distance in meters with validation
 */
@JvmInline
value class Distance(val meters: Float) {
    init {
        require(meters > 0) { "Distance must be positive: $meters" }
        require(meters <= MAX_DISTANCE_METERS) { "Distance cannot exceed ${MAX_DISTANCE_METERS}m (100km): $meters" }
    }
    
    companion object {
        const val MAX_DISTANCE_METERS = 100000f // 100km
        
        fun fromMeters(meters: Float): Distance = Distance(meters)
        fun fromKilometers(km: Float): Distance = Distance(km * 1000f)
        fun fromMiles(miles: Float): Distance = Distance(miles * 1609.34f)
    }
    
    /**
     * Converts distance to kilometers
     */
    fun toKilometers(): Float = meters / 1000f
    
    /**
     * Converts distance to miles
     */
    fun toMiles(): Float = meters / 1609.34f
    
    /**
     * Formats distance with appropriate unit
     */
    fun format(): String = when {
        meters < 1000 -> "%.0f m".format(meters)
        meters < 10000 -> "%.2f km".format(toKilometers())
        else -> "%.1f km".format(toKilometers())
    }
    
    operator fun plus(other: Distance): Distance = Distance(meters + other.meters)
    operator fun minus(other: Distance): Distance = Distance(meters - other.meters)
    operator fun times(multiplier: Float): Distance = Distance(meters * multiplier)
    operator fun div(divisor: Float): Distance = Distance(meters / divisor)
    
    operator fun compareTo(other: Distance): Int = meters.compareTo(other.meters)
    
    override fun toString(): String = format()
}