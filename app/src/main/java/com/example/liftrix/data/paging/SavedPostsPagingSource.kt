package com.example.liftrix.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.dto.SavedPostWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.min

/**
 * PagingSource for user's saved posts with workout details.
 * Provides paginated access to posts saved by a specific user.
 * Part of social feed engagement system from SPEC-20250113-social-feed-engagement.
 */
class SavedPostsPagingSource @Inject constructor(
    private val savedPostDao: SavedPostDao,
    private val userId: String
) : PagingSource<Int, SavedPostWithDetails>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SavedPostWithDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val offset = params.key ?: 0
                val limit = params.loadSize

                // Get saved posts with details for the specific user
                val allSavedPosts = savedPostDao.getUserSavedPostsWithDetails(userId).first()
                
                // Simulate pagination by slicing the list
                val startIndex = offset
                val endIndex = min(startIndex + limit, allSavedPosts.size)
                val pageData = if (startIndex < allSavedPosts.size) {
                    allSavedPosts.subList(startIndex, endIndex)
                } else {
                    emptyList()
                }

                LoadResult.Page(
                    data = pageData,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (pageData.isEmpty() || endIndex >= allSavedPosts.size) null else endIndex
                )
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SavedPostWithDetails>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}