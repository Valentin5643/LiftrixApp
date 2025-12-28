# Database Composite Index Audit - P0-PERF-001

**Date**: 2024-12-24
**Database Version**: v1 → v2
**Total Entities**: 69
**Target**: Add composite indexes to 20-30 critical entities

## Audit Summary

### Entities Already Well-Indexed ✅
1. **WorkoutEntity** - 10 composite indexes including user_id + date/timestamp patterns
2. **WorkoutPostEntity** - 8 composite indexes for feed queries, visibility, engagement
3. **FollowRelationshipEntity** - 6 composite indexes for follower/following timelines
4. **PersonalRecordEntity** - 4 composite indexes including user_id + achieved_at

### Entities Needing Critical Indexes 🔧

#### **Category 1: Social Engagement (High Priority)**
1. **PostLikeEntity** - MISSING: `user_id + created_at` for user activity timeline
2. **PostCommentEntity** - MISSING: `user_id + created_at` for user comment history
3. **SavedPostEntity** - Need to audit (likely missing user_id + created_at)
4. **ProfileViewEntity** - Need to audit (likely missing viewer_id + timestamp)

#### **Category 2: Notifications & Messaging**
5. **NotificationHistoryEntity** - Need: `user_id + created_at + read_status`
6. **NotificationQueueEntity** - Need: `user_id + status + scheduled_at`
7. **ChatHistoryEntity** - Need: `user_id + created_at` for message timeline
8. **SupportTicketMessageEntity** - Need: `ticket_id + created_at`

#### **Category 3: Workout & Exercise History**
9. **ExerciseHistoryEntity** - Need: `user_id + performed_at` for analytics
10. **ExerciseSetEntity** - Need: `workout_id + exercise_id + set_order`
11. **WorkoutTemplateEntity** - Need: `user_id + updated_at`
12. **CustomExerciseEntity** - Need: `user_id + created_at`

#### **Category 4: Progress & Analytics**
13. **ProgressPhotoEntity** - Need: `user_id + taken_at`
14. **WorkoutAnomalyEntity** - Need: `user_id + detected_at + severity`
15. **ExerciseUsageHistoryEntity** - Need: `user_id + exercise_id + last_used`

#### **Category 5: Sync & Queue Management**
16. **SyncQueueEntity** - Need: `user_id + sync_status + priority + created_at`
17. **DeadLetterQueueEntity** - Need: `user_id + failed_at`

#### **Category 6: Social Infrastructure**
18. **GymBuddyEntity** - Need: `user_id + status + created_at`
19. **GymBuddyActivityEntity** - Need: `user_id + activity_type + created_at`
20. **BlockedUserEntity** - Need: `user_id + blocked_at`
21. **FollowRequestEntity** - Need: `to_user_id + status + created_at`, `from_user_id + created_at`

#### **Category 7: Cache & Performance**
22. **FeedCacheEntity** - Need: `user_id + relevance_score + cached_at`
23. **UserSearchCacheEntity** - Need: `query_hash + created_at`
24. **AnalyticsCacheEntity** - Need: `user_id + widget_id + time_range + cached_at`

#### **Category 8: Settings & Preferences**
25. **WidgetPreferenceEntity** - Need: `user_id + widget_id`
26. **NotificationPreferenceEntity** - Need: `user_id + category`
27. **PrivacySettingsEntity** - Need: `user_id` (single-column sufficient)
28. **SettingsAuditEntity** - Need: `user_id + changed_at`

## Implementation Priority

### Phase 1: Critical Social & Feed Queries (6 entities)
- PostLikeEntity
- PostCommentEntity
- SavedPostEntity
- NotificationHistoryEntity
- ChatHistoryEntity
- FeedCacheEntity

### Phase 2: Workout & Analytics (6 entities)
- ExerciseHistoryEntity
- ExerciseSetEntity
- WorkoutTemplateEntity
- ProgressPhotoEntity
- ExerciseUsageHistoryEntity
- AnalyticsCacheEntity

### Phase 3: Sync & Infrastructure (6 entities)
- SyncQueueEntity
- NotificationQueueEntity
- GymBuddyEntity
- FollowRequestEntity
- BlockedUserEntity
- DeadLetterQueueEntity

### Phase 4: Settings & Optimization (6 entities)
- WidgetPreferenceEntity
- SettingsAuditEntity
- UserSearchCacheEntity
- CustomExerciseEntity
- WorkoutAnomalyEntity
- GymBuddyActivityEntity

## Expected Performance Improvements

### Before Optimization:
- Feed queries: 200-500ms (full table scan on user activity)
- Comment history: 100-300ms (post_id index only)
- Notification loading: 150-400ms (user_id sequential scan)
- Analytics queries: 300-800ms (workout history without date range optimization)

### After Optimization (Projected):
- Feed queries: 20-50ms (user_id + created_at covering index)
- Comment history: 10-30ms (direct index lookup)
- Notification loading: 15-40ms (user_id + created_at + status composite)
- Analytics queries: 30-80ms (user_id + date covering indexes)

**Expected Overall Improvement**: 70-90% reduction in query time for user-scoped temporal queries

## Database Migration Strategy

### Version Bump: v1 → v2

```kotlin
// LiftrixDatabase.kt
@Database(
    entities = [/* all 69 entities */],
    version = 2, // BUMPED FROM 1
    exportSchema = true
)
```

### Migration Implementation:
1. Create Migration_1_2.kt with all index additions
2. No data migration needed (structure-only changes)
3. Indexes created asynchronously during app startup
4. Fallback to destructive migration in debug builds only

## Verification Plan

1. **Compilation Test**: Verify all @Index annotations compile correctly
2. **Migration Test**: Test database upgrade from v1 to v2
3. **Query Plan Analysis**: Use EXPLAIN QUERY PLAN to verify index usage
4. **Performance Test**: Measure query times before/after on sample data
5. **Schema Export**: Verify exported schema includes all new indexes

---

**Status**: Ready for implementation
**Estimated Time**: 4 hours implementation + 1 hour testing = 5 hours total
