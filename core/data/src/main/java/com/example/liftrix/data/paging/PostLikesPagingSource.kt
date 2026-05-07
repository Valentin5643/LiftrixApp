package com.example.liftrix.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dto.PostLikeWithProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * PagingSource for post likes with user profiles.
 * Provides paginated access to users who liked a specific post.
 * Part of social feed engagement system from SPEC-20250113-social-feed-engagement.
 */
class PostLikesPagingSource @Inject constructor(
    private val postLikeDao: PostLikeDao,
    private val postId: String
) : PagingSource<Int, PostLikeWithProfile>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PostLikeWithProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val offset = params.key ?: 0
                val limit = params.loadSize

                // Get likes with user profiles for the specific post
                val likes = postLikeDao.getPostLikersWithProfiles(postId, limit, offset)

                LoadResult.Page(
                    data = likes,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (likes.isEmpty()) null else offset + likes.size
                )
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, PostLikeWithProfile>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}