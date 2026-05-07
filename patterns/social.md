# Social, Feed, Gym Buddy, and PR Patterns

Last moved from root `AGENTS.md`: 2026-05-05. Source audit baseline: 2026-05-01.

Use this when touching feed, posts, follows, public profiles, privacy, engagement, comments, media sharing, gym buddies, PR notifications, or social notification settings.

## Core Rules

- Every social surface must include viewer context and privacy filtering.
- User-owned/social data queries must remain scoped by `userId`, `user_id`, `viewerId`, or the relevant relationship key.
- Use optimistic UI for engagement operations and revert on repository failure.
- Avoid direct Firebase access from UI. Social feed reads local/Paging data and syncs in the background.
- Batch engagement/notification updates where the repository/service pattern supports it.

## Active Feed Path

```text
FeedScreen / Home feed
  -> collectAsLazyPagingItems()
  -> FeedViewModel
  -> Flow<PagingData<WorkoutPost>>
  -> FeedRepositoryImpl
  -> Room DAO paging plus cache/sync
```

Source-audited notes:

- `FeedRepositoryImpl` uses Paging 3 and Room DAO paging for active feed queries.
- `FeedRemoteMediator` exists/imports remain, but active feed queries explicitly avoid using it.
- `FeedGeneratorUseCase` still exists for relevance scoring/privacy filtering paths, but the active Home feed path does not call it directly.

Required Compose imports for feed screens:

```kotlin
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState
```

## Privacy

Always check visibility before display or profile access:

```kotlin
val canView = privacyService.canViewPost(viewerId, post)
if (!canView) return@filter false
```

Privacy levels preserved from the prior root guide:

- `PUBLIC`: visible to everyone and may appear in discovery feed.
- `FOLLOWERS`: visible to approved followers.
- `PRIVATE`: visible only to the author.

For profile access, pass viewer context:

```kotlin
userSearchRepository.getPublicProfile(profileUserId, viewerId)
```

Do not expose profile data with missing privacy context unless source proves the method is safe for that surface.

## Engagement

Use optimistic state and revert on failure:

```kotlin
_likedPosts.value = _likedPosts.value + postId
val result = engagementRepository.toggleLike(postId, userId)
result.onFailure {
    _likedPosts.value = _likedPosts.value - postId
}
```

Debug hot zones:

- Privacy violations: `PrivacyEnforcementService.canViewPost()`.
- Optimistic update failures: engagement repository operations and revert logic.
- Feed cache staleness: `FeedCacheService` invalidation after follows/unfollows or new content.
- Realtime comments: `CommentSyncService` listener lifecycle.
- Sequential DB operations: use batch operations for repeated engagement mutations.

## Media Sharing

Media pipeline ownership:

```text
MediaUploadServiceImpl
  -> Firebase Storage
  -> image compression and thumbnails
  -> URL/CDN persistence
  -> background upload progress where applicable
```

Keep upload/storage behavior behind services. Do not add UI-owned Firebase Storage writes.

## Gym Buddy

Rules preserved from the prior root guide:

- Gym buddy connections are mutual.
- Maximum buddy count is 5.
- QR pairing tokens expire after 5 minutes.
- Validate both buddy limit and QR expiration before creating a connection.

Representative flow:

```text
GymBuddyScreen
  -> GymBuddyViewModel
  -> QRCodeService
  -> GymBuddyRepository
  -> PRDetectionService / notifications
```

## PR Detection and Notifications

Preserved PR facts:

- PR types: `ONE_RM`, `VOLUME`, `REPS`, `MAX_WEIGHT`.
- Epley formula is used for one-rep-max estimation.
- Significance levels: `EXCEPTIONAL` 10%+, `MAJOR` 5%+, `MODERATE` 2%+, `MINOR` below 2%.
- Enforce one PR notification per buddy per day.

Notification routing should respect privacy, quiet hours, category preferences, mutes, batching, and active-workout silencing where implemented.

## Common Issues

- Missing viewer context can leak private social data.
- Firestore listeners must be removed when their owning lifecycle ends.
- Feed cache invalidation is required after relationship changes.
- Do not add links to `SocialOnboarding` or `PrivacySettings` without verifying active `UnifiedNavigationContainer` registration.
- Do not newly surface deprecated/hidden social routes without checking `docs/architecture.md` and `docs/module-map.md`.

## Existing Docs Reused

- `docs/social.md` is an older product/implementation plan and remains useful for intent/history.
- This file is the shorter source-audited agent ruleset for active code changes.
