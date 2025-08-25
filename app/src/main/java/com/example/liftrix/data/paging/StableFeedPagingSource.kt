package com.example.liftrix.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * A PagingSource wrapper that prevents empty intermediate states during sync operations.
 * This wrapper intelligently filters out false empty states that Room emits during transactions.
 * 
 * The wrapper implements a smart retry mechanism:
 * 1. If the first load returns empty but we know posts should exist, retry after a short delay
 * 2. This handles the case where Room emits an intermediate empty state during sync
 * 3. After retry, accept whatever state Room provides (could genuinely be empty)
 */
class StableFeedPagingSource(
    private val delegate: PagingSource<Int, WorkoutPostEntity>,
    private val userId: String,
    private val hasLocalPosts: suspend () -> Boolean
) : PagingSource<Int, WorkoutPostEntity>() {
    
    override val keyReuseSupported: Boolean = delegate.keyReuseSupported
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, WorkoutPostEntity> {
        val result = delegate.load(params)
        
        // 🔥 FIX: If we get an empty page but we know posts exist, retry once
        // This handles Room's intermediate empty emission during transactions
        if (result is LoadResult.Page && 
            result.data.isEmpty() && 
            params.key == null && // Only for initial load
            hasLocalPosts()) {
            
            delay(100) // Brief delay to let transaction complete
            
            // Retry the load
            val retryResult = delegate.load(params)
            return retryResult
        }
        
        return result
    }
    
    override fun getRefreshKey(state: PagingState<Int, WorkoutPostEntity>): Int? {
        return delegate.getRefreshKey(state)
    }
}