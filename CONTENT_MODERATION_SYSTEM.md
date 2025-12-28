# Liftrix Content Moderation System Documentation

**Status**: ✅ **Play Store Compliant**
**Last Updated**: 2024-12-26
**Compliance**: Google Play UGC Policy Compliant

---

## Executive Summary

Liftrix implements a **comprehensive multi-layered content moderation system** that exceeds Google Play Store requirements for user-generated content (UGC) applications. The system combines user reporting, admin moderation tools, privacy controls, and automated enforcement to maintain a safe community.

---

## System Architecture

```
User-Generated Content (UGC)
    ↓
Privacy Enforcement Layer (PrivacyEnforcementService)
    ↓
User Reporting (ReportContentBottomSheet)
    ↓
Admin Review Queue
    ↓
Moderation Actions (AdminBanManagementScreen)
    ↓
Enforcement (Bans, Content Removal, Warnings)
```

---

## 1. Content Reporting System ✅

### Implementation
**File**: `app/src/main/java/com/example/liftrix/ui/components/ReportContentBottomSheet.kt`

### Features
- **Accessible from all UGC**: Posts, comments, profiles
- **Predefined report reasons**:
  - Spam or misleading content
  - Inappropriate content (community guideline violations)
  - Harassment or bullying
  - False/harmful health information
  - Copyright violations
  - Other (with optional description field)

### Report Flow
1. User taps ⋮ menu on content
2. Selects "Report Content"
3. Chooses reason from predefined list
4. Optionally adds details
5. Report submitted to moderation queue
6. Reporter identity kept confidential

**Compliance**: ✅ Meets Google Play requirement for in-app content reporting

---

## 2. Admin Moderation Tools ✅

### Implementation
**File**: `app/src/main/java/com/example/liftrix/ui/admin/AdminBanManagementScreen.kt`

### Features
- **User search and lookup**: Find users by ID or username
- **Ban management**:
  - View current ban status
  - Issue temporary or permanent bans
  - Add ban reasons and notes
  - Severity classification (low, medium, high, critical)
- **Ban history**: Complete audit trail of all moderation actions
- **Confirmation dialogs**: Prevent accidental bans
- **Admin permissions verification**: Firebase Admin SDK integration

### Moderation Actions
- **Warning**: First offense, content removed
- **Temporary Suspension**: 7-30 days, all privileges revoked
- **Permanent Ban**: Account deactivation, content removal, device blocking

**Compliance**: ✅ Meets Google Play requirement for moderation tools

---

## 3. Privacy & Blocking System ✅

### Implementation
**File**: `app/src/main/java/com/example/liftrix/domain/service/PrivacyEnforcementService.kt`

### Privacy Controls
- **Profile Visibility**: Public, Followers Only, Private
- **Workout Sharing**: Granular control per workout or default setting
- **Discovery Settings**: Hide from user suggestions
- **Social Features Toggle**: Disable all social interactions

### User Blocking
- **Bidirectional privacy**: Blocker and blocked cannot interact
- **Content filtering**: Blocked users invisible in feeds
- **Private action**: Blocked users not notified
- **Access control**: Profile, posts, comments all restricted

### Relationship Cache (Performance Optimization)
- 5-minute TTL cache for follower/block relationships
- Reduces O(N) database queries to O(1) for batch operations
- Automatic cache invalidation on relationship changes

**Compliance**: ✅ Exceeds Google Play expectations for user safety controls

---

## 4. Content Visibility Enforcement ✅

### Implementation
**Service**: `PrivacyEnforcementService.kt`
**Methods**:
- `canViewProfile(profileUserId, viewerId)` - Profile access control
- `canViewWorkout(workoutOwnerId, viewerId)` - Workout visibility
- `canViewPost(viewerId, post)` - Post-level privacy
- `canSendFollowRequest()` - Follow permission checks
- `canAddGymBuddy()` - Gym buddy access control
- `filterDiscoverableUsers()` - Discovery/search filtering

### Privacy Enforcement Rules
1. **Blocked users**: Automatically filtered from all content
2. **Privacy settings respected**: Public/Followers/Private enforced
3. **Fail-safe defaults**: Missing settings default to Private
4. **Social features toggle**: Respects user's social enabled/disabled state
5. **Anonymous blocking**: Anonymous users cannot view any social content

**Compliance**: ✅ Comprehensive privacy enforcement exceeds baseline requirements

---

## 5. Community Guidelines ✅

### Implementation
**File**: `docs/community-guidelines.html`
**Accessibility**: Settings → Community Guidelines (in-app link)

### Coverage
- Core values (respect, honesty, support, safety, inclusivity)
- Encouraged behaviors (sharing achievements, constructive feedback)
- Prohibited content (harassment, harmful advice, spam, inappropriate content)
- Reporting process (step-by-step user guide)
- Moderation actions (warnings, suspensions, bans)
- Privacy controls documentation
- Fitness safety guidelines
- Appeals process

**Compliance**: ✅ Meets Google Play requirement for documented moderation policies

---

## 6. Report Processing Workflow

### Current Implementation

```
1. User reports content via ReportContentBottomSheet
   ↓
2. Report stored in ReportRepository with metadata:
   - Reporter ID (confidential)
   - Content ID and type
   - Report reason
   - Timestamp
   - Additional details
   ↓
3. Admin reviews reports via AdminBanManagementScreen
   ↓
4. Moderation decision:
   - No action (report dismissed)
   - Warning issued (first offense)
   - Content removed
   - Temporary ban (7-30 days)
   - Permanent ban
   ↓
5. User notification (if applicable)
   ↓
6. Audit trail created
```

### Review SLA
- **Standard reports**: Reviewed within 24-48 hours
- **High-priority reports** (harassment, threats): Reviewed within 4 hours
- **Critical reports** (explicit content, illegal activity): Immediate action

---

## 7. Data Models

### Report Entity
```kotlin
data class ContentReport(
    val reportId: String,
    val reporterId: String,      // Confidential
    val contentId: String,
    val contentType: ContentType, // POST, COMMENT, PROFILE
    val reason: ReportReason,
    val additionalDetails: String?,
    val timestamp: Long,
    val status: ReportStatus      // PENDING, REVIEWED, DISMISSED, ACTIONED
)
```

### Ban Entity
```kotlin
data class BanInfo(
    val userId: String,
    val bannedBy: String,         // Admin ID
    val banType: BanType,          // TEMPORARY, PERMANENT
    val severity: BanSeverity,     // LOW, MEDIUM, HIGH, CRITICAL
    val reason: String,
    val startDate: Long,
    val endDate: Long?,            // null for permanent
    val notes: String?
)
```

---

## 8. Play Store Compliance Checklist

### UGC App Requirements

| Requirement | Status | Implementation |
|-------------|--------|----------------|
| Content reporting mechanism | ✅ Complete | ReportContentBottomSheet |
| User blocking/muting tools | ✅ Complete | PrivacyEnforcementService |
| Admin moderation tools | ✅ Complete | AdminBanManagementScreen |
| Community guidelines | ✅ Complete | docs/community-guidelines.html |
| Privacy controls | ✅ Complete | Privacy settings + blocking |
| Report confidentiality | ✅ Complete | Reporter ID never disclosed |
| Appeals process | ✅ Complete | Documented in guidelines |

**Overall Compliance**: ✅ **100% Compliant** with Google Play UGC policies

---

## 9. Future Enhancements (Optional)

### Not Required for Play Store, But Recommended

**Automated Content Scanning** (Post-Launch):
- Profanity filter for instant content flagging
- Spam detection using ML models
- Harmful content detection (eating disorders, dangerous advice)

**Admin Dashboard Improvements**:
- Bulk moderation actions
- Report analytics and trends
- User reputation scoring
- Automated warning escalation

**User Safety Features**:
- Mute notifications from specific users
- Time-limited mutes (24hr, 7 days, 30 days)
- Content warnings for sensitive topics

---

## 10. Testing & Validation

### Manual Testing Checklist
- [x] User can report posts via ⋮ menu
- [x] User can report comments
- [x] User can report profiles
- [x] All report reasons selectable
- [x] Optional details field works
- [x] Reports submitted successfully
- [x] Admin can view report queue
- [x] Admin can issue bans
- [x] Banned users cannot post
- [x] Blocked users cannot view content
- [x] Privacy settings enforced

### Production Monitoring
- **Report volume**: Track reports per day/week
- **Response time**: Average time to review reports
- **Ban rate**: Percentage of users banned
- **Appeal rate**: Appeals per 100 bans

---

## 11. Contact & Support

### For Moderation Issues
- **Email**: support@liftrix.app
- **In-App**: Settings → Help & Feedback
- **Appeals**: Email with subject "Moderation Appeal"

### For Play Store Review Team
This document demonstrates Liftrix's comprehensive UGC moderation system. All required components are implemented and accessible for review:
- Community Guidelines: `docs/community-guidelines.html`
- Report UI: Search codebase for `ReportContentBottomSheet.kt`
- Admin Tools: Search codebase for `AdminBanManagementScreen.kt`
- Privacy Service: Search codebase for `PrivacyEnforcementService.kt`

---

## Conclusion

Liftrix's content moderation system is **production-ready and Play Store compliant**. The implementation includes:
- ✅ User reporting with confidentiality
- ✅ Comprehensive admin moderation tools
- ✅ Granular privacy and blocking controls
- ✅ Documented community guidelines
- ✅ Appeals process
- ✅ Audit trails and accountability

**No additional implementation required for Play Store submission.**

---

**Document Version**: 1.0
**Created**: 2024-12-26
**Next Review**: After 30 days of production use
**Owner**: Liftrix Engineering Team
