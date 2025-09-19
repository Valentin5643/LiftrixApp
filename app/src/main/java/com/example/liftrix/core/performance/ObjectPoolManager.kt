package com.example.liftrix.core.performance

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Object Pool Manager for reducing memory allocations during JSON parsing operations.
 *
 * Implements object pooling pattern to reduce GC pressure by reusing expensive objects:
 * - Gson instances with pre-configured settings
 * - TypeToken instances for common parsing operations
 * - StringBuilder instances for JSON manipulation
 * - Temporary collections for data processing
 *
 * Target: 60-80% reduction in memory allocations for workout JSON operations
 */
@Singleton
class ObjectPoolManager @Inject constructor() {

    companion object {
        const val MAX_POOL_SIZE = 10
        const val MAX_STRING_BUILDER_SIZE = 50_000 // ~50KB initial capacity
        const val MAX_COLLECTION_SIZE = 1000 // Max exercises/sets in collections
    }

    // Gson instance pool for different parsing configurations
    private val strictGsonPool = ConcurrentLinkedQueue<Gson>()
    private val lenientGsonPool = ConcurrentLinkedQueue<Gson>()

    // TypeToken pool for common parsing operations
    private val exerciseListTypeTokenPool = ConcurrentLinkedQueue<TypeToken<List<com.example.liftrix.domain.model.Exercise>>>()
    private val mapTypeTokenPool = ConcurrentLinkedQueue<TypeToken<Map<String, Any>>>()

    // StringBuilder pool for JSON manipulation
    private val stringBuilderPool = ConcurrentLinkedQueue<StringBuilder>()

    // Collection pools for temporary data processing
    private val arrayListPool = ConcurrentLinkedQueue<ArrayList<Any>>()
    private val hashMapPool = ConcurrentLinkedQueue<HashMap<String, Any>>()

    init {
        // Pre-populate pools with commonly used objects
        initializePools()
    }

    /**
     * Get a strict Gson instance (fails on malformed JSON)
     */
    fun getStrictGson(): Gson {
        return strictGsonPool.poll() ?: createStrictGson()
    }

    /**
     * Return a strict Gson instance to the pool
     */
    fun returnStrictGson(gson: Gson) {
        if (strictGsonPool.size < MAX_POOL_SIZE) {
            strictGsonPool.offer(gson)
        }
    }

    /**
     * Get a lenient Gson instance (handles malformed JSON gracefully)
     */
    fun getLenientGson(): Gson {
        return lenientGsonPool.poll() ?: createLenientGson()
    }

    /**
     * Return a lenient Gson instance to the pool
     */
    fun returnLenientGson(gson: Gson) {
        if (lenientGsonPool.size < MAX_POOL_SIZE) {
            lenientGsonPool.offer(gson)
        }
    }

    /**
     * Get a TypeToken for Exercise list parsing
     */
    fun getExerciseListTypeToken(): TypeToken<List<com.example.liftrix.domain.model.Exercise>> {
        return exerciseListTypeTokenPool.poll() ?: object : TypeToken<List<com.example.liftrix.domain.model.Exercise>>() {}
    }

    /**
     * Return an Exercise list TypeToken to the pool
     */
    fun returnExerciseListTypeToken(typeToken: TypeToken<List<com.example.liftrix.domain.model.Exercise>>) {
        if (exerciseListTypeTokenPool.size < MAX_POOL_SIZE) {
            exerciseListTypeTokenPool.offer(typeToken)
        }
    }

    /**
     * Get a TypeToken for Map parsing
     */
    fun getMapTypeToken(): TypeToken<Map<String, Any>> {
        return mapTypeTokenPool.poll() ?: object : TypeToken<Map<String, Any>>() {}
    }

    /**
     * Return a Map TypeToken to the pool
     */
    fun returnMapTypeToken(typeToken: TypeToken<Map<String, Any>>) {
        if (mapTypeTokenPool.size < MAX_POOL_SIZE) {
            mapTypeTokenPool.offer(typeToken)
        }
    }

    /**
     * Get a StringBuilder for JSON manipulation
     */
    fun getStringBuilder(): StringBuilder {
        val sb = stringBuilderPool.poll()
        return if (sb != null) {
            sb.clear()
            sb
        } else {
            StringBuilder(MAX_STRING_BUILDER_SIZE)
        }
    }

    /**
     * Return a StringBuilder to the pool
     */
    fun returnStringBuilder(stringBuilder: StringBuilder) {
        if (stringBuilderPool.size < MAX_POOL_SIZE && stringBuilder.capacity() <= MAX_STRING_BUILDER_SIZE * 2) {
            stringBuilderPool.offer(stringBuilder)
        }
    }

    /**
     * Get an ArrayList for temporary data processing
     */
    fun getArrayList(): ArrayList<Any> {
        val list = arrayListPool.poll()
        return if (list != null) {
            list.clear()
            list
        } else {
            ArrayList(MAX_COLLECTION_SIZE)
        }
    }

    /**
     * Return an ArrayList to the pool
     */
    fun returnArrayList(arrayList: ArrayList<Any>) {
        if (arrayListPool.size < MAX_POOL_SIZE && arrayList.size <= MAX_COLLECTION_SIZE) {
            arrayListPool.offer(arrayList)
        }
    }

    /**
     * Get a HashMap for temporary data processing
     */
    fun getHashMap(): HashMap<String, Any> {
        val map = hashMapPool.poll()
        return if (map != null) {
            map.clear()
            map
        } else {
            HashMap(MAX_COLLECTION_SIZE)
        }
    }

    /**
     * Return a HashMap to the pool
     */
    fun returnHashMap(hashMap: HashMap<String, Any>) {
        if (hashMapPool.size < MAX_POOL_SIZE && hashMap.size <= MAX_COLLECTION_SIZE) {
            hashMapPool.offer(hashMap)
        }
    }

    /**
     * Execute a block of code with automatic resource management
     */
    inline fun <T> withStrictGson(block: (Gson) -> T): T {
        val gson = getStrictGson()
        return try {
            block(gson)
        } finally {
            returnStrictGson(gson)
        }
    }

    /**
     * Execute a block of code with automatic resource management
     */
    inline fun <T> withLenientGson(block: (Gson) -> T): T {
        val gson = getLenientGson()
        return try {
            block(gson)
        } finally {
            returnLenientGson(gson)
        }
    }

    /**
     * Execute a block of code with automatic StringBuilder management
     */
    inline fun <T> withStringBuilder(block: (StringBuilder) -> T): T {
        val sb = getStringBuilder()
        return try {
            block(sb)
        } finally {
            returnStringBuilder(sb)
        }
    }

    /**
     * Get pool statistics for monitoring
     */
    fun getPoolStats(): PoolStats {
        return PoolStats(
            strictGsonPoolSize = strictGsonPool.size,
            lenientGsonPoolSize = lenientGsonPool.size,
            exerciseListTypeTokenPoolSize = exerciseListTypeTokenPool.size,
            mapTypeTokenPoolSize = mapTypeTokenPool.size,
            stringBuilderPoolSize = stringBuilderPool.size,
            arrayListPoolSize = arrayListPool.size,
            hashMapPoolSize = hashMapPool.size
        )
    }

    private fun initializePools() {
        // Pre-populate Gson pools
        repeat(3) {
            strictGsonPool.offer(createStrictGson())
            lenientGsonPool.offer(createLenientGson())
        }

        // Pre-populate TypeToken pools
        repeat(2) {
            exerciseListTypeTokenPool.offer(object : TypeToken<List<com.example.liftrix.domain.model.Exercise>>() {})
            mapTypeTokenPool.offer(object : TypeToken<Map<String, Any>>() {})
        }

        // Pre-populate StringBuilder pool
        repeat(2) {
            stringBuilderPool.offer(StringBuilder(MAX_STRING_BUILDER_SIZE))
        }

        // Pre-populate collection pools
        repeat(2) {
            arrayListPool.offer(ArrayList(MAX_COLLECTION_SIZE))
            hashMapPool.offer(HashMap(MAX_COLLECTION_SIZE))
        }

        Timber.d("ObjectPoolManager: Initialized pools with pre-allocated objects")
    }

    private fun createStrictGson(): Gson {
        return GsonBuilder()
            .create()
    }

    private fun createLenientGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    /**
     * Pool statistics data class
     */
    data class PoolStats(
        val strictGsonPoolSize: Int,
        val lenientGsonPoolSize: Int,
        val exerciseListTypeTokenPoolSize: Int,
        val mapTypeTokenPoolSize: Int,
        val stringBuilderPoolSize: Int,
        val arrayListPoolSize: Int,
        val hashMapPoolSize: Int
    ) {
        val totalObjectsPooled: Int
            get() = strictGsonPoolSize + lenientGsonPoolSize + exerciseListTypeTokenPoolSize +
                    mapTypeTokenPoolSize + stringBuilderPoolSize + arrayListPoolSize + hashMapPoolSize
    }
}