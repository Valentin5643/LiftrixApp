package com.example.liftrix.data.local.dao

import com.example.liftrix.data.local.entity.PostLikeEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialMutationSyncDaoTest {
    @Test
    fun tombstoneRetainsDeleteAndDirtyMetadata() {
        val now = System.currentTimeMillis()
        val tombstone = PostLikeEntity("like", "post", "user", now).copy(
            isDeleted = true, isDirty = true, isSynced = false, lastModified = now + 1
        )
        assertTrue(tombstone.isDeleted)
        assertTrue(tombstone.isDirty)
        assertFalse(tombstone.isSynced)
    }
}
