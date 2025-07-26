# Firebase Deployment Guide - Liftrix Profile System

This guide provides step-by-step instructions for deploying the Firebase configuration required for the Liftrix profile system.

## Prerequisites

- Firebase project set up for Liftrix
- Firebase CLI installed (`npm install -g firebase-tools`)
- Administrative access to the Firebase project
- Completed authentication setup

## Deployment Steps

### 1. Firestore Security Rules

**Manual Deployment (Recommended for first-time setup):**

1. Open [Firebase Console](https://console.firebase.google.com)
2. Select your Liftrix project
3. Navigate to **Firestore Database** → **Rules**
4. Copy the contents of `firestore-security-rules.js`
5. Paste into the Firebase Console rules editor
6. Click **Publish** to deploy the rules
7. Test using the Firebase Console simulator

**Automated Deployment:**

```bash
# Copy the rules content to firestore.rules
cp firestore-security-rules.js firestore.rules

# Deploy rules
firebase deploy --only firestore:rules
```

### 2. Firebase Storage Security Rules

**Manual Deployment:**

1. In Firebase Console, navigate to **Storage** → **Rules**
2. Copy the contents of `firebase-storage-rules.js`
3. Paste into the Storage rules editor
4. Click **Publish** to deploy the rules

**Automated Deployment:**

```bash
# Copy the rules content to storage.rules
cp firebase-storage-rules.js storage.rules

# Deploy rules
firebase deploy --only storage
```

### 3. Firestore Indexes

**Manual Deployment:**

1. In Firebase Console, navigate to **Firestore Database** → **Indexes**
2. For each composite index in `firestore-indexes.json`, create manually:
   - Click **Create Index**
   - Select collection group
   - Add fields with specified order (Ascending/Descending)
   - Wait for index creation to complete

**Automated Deployment (Recommended):**

```bash
# Initialize Firestore (if not already done)
firebase init firestore

# Replace the generated firestore.indexes.json with our configuration
cp firestore-indexes.json firestore.indexes.json

# Deploy indexes
firebase deploy --only firestore:indexes
```

### 4. Required Firestore Collections Setup

Create the following collections with sample documents to establish the structure:

```javascript
// Collection: users_public
{
  "userId": "sample-user-id",
  "displayName": "Sample User",
  "bio": "Sample bio text",
  "isPublic": true,
  "profileImageUrl": null,
  "memberSince": "2025-07-25T00:00:00Z",
  "lastActiveAt": "2025-07-25T12:00:00Z",
  "updatedAt": "2025-07-25T12:00:00Z"
}

// Collection: user_search_cache
{
  "userId": "sample-user-id", 
  "displayName": "Sample User",
  "searchTokens": ["sample", "user"],
  "isPublic": true,
  "profileImageUrl": null,
  "lastActiveAt": "2025-07-25T12:00:00Z",
  "totalWorkouts": 0
}

// Collection: qr_codes
{
  "userId": "sample-user-id",
  "qrCodeUrl": "https://chart.googleapis.com/chart?...",
  "profileUrl": "https://liftrix.app/profile/sample-user-id",
  "createdAt": "2025-07-25T12:00:00Z",
  "expiresAt": "2025-08-25T12:00:00Z"
}
```

### 5. Firebase Storage Bucket Configuration

**Configure CORS (if web app access needed):**

Create `cors.json`:
```json
[
  {
    "origin": ["https://your-app-domain.com", "https://localhost:3000"],
    "method": ["GET", "POST", "PUT", "DELETE"],
    "maxAgeSeconds": 3600
  }
]
```

Deploy CORS configuration:
```bash
gsutil cors set cors.json gs://your-firebase-project.appspot.com
```

**Set up Storage folders:**
- Create folder structure in Firebase Console Storage:
  - `/profile_images/`
  - `/temp_uploads/`
  - `/qr_codes/`
  - `/workout_images/`

### 6. Authentication Configuration

Ensure Firebase Authentication is configured with these providers:
- Email/Password
- Google Sign-In
- (Optional) Facebook, Apple, etc.

**Required settings:**
- Enable email verification
- Set up password reset templates
- Configure authorized domains for production

### 7. Firebase Functions (Optional - for server-side operations)

If using Firebase Functions for server-side operations:

```bash
# Initialize functions
firebase init functions

# Deploy functions
firebase deploy --only functions
```

**Recommended functions:**
- `generateThumbnails` - Automatic image resizing
- `cleanupExpiredQRCodes` - Scheduled cleanup
- `calculateAchievements` - Server-side achievement calculation
- `updateSearchCache` - Maintain search index

### 8. Performance and Monitoring Setup

**Enable Performance Monitoring:**
1. Firebase Console → Performance
2. Enable Performance Monitoring
3. Add Performance SDK to app

**Set up Crashlytics:**
1. Firebase Console → Crashlytics
2. Enable Crashlytics
3. Add Crashlytics SDK to app

**Configure Analytics:**
1. Firebase Console → Analytics
2. Set up conversion events for profile completion
3. Track user engagement metrics

### 9. Production Considerations

**Security:**
- Review and audit security rules before production
- Set up monitoring for unusual access patterns
- Enable Firebase App Check for additional security

**Performance:**
- Monitor query performance and costs
- Set up budget alerts for Firebase usage
- Optimize indexes based on usage patterns

**Backup:**
- Set up automated Firestore backups
- Configure Storage backup policies
- Document disaster recovery procedures

## Testing the Deployment

### 1. Security Rules Testing

Use Firebase Console Rules simulator:

```javascript
// Test public profile access
// Authenticated user: auth.uid = "test-user-123"
// Path: /databases/(default)/documents/users_public/other-user-456
// Operation: get
// Expected: Allow if resource.data.isPublic == true

// Test profile write access
// Authenticated user: auth.uid = "test-user-123" 
// Path: /databases/(default)/documents/users_public/test-user-123
// Operation: write
// Expected: Allow with valid profile data
```

### 2. Storage Rules Testing

```javascript
// Test profile image upload
// Authenticated user: auth.uid = "test-user-123"
// Path: /profile_images/test-user-123/profile.jpg
// Operation: write
// Expected: Allow with valid image file

// Test unauthorized access
// Authenticated user: auth.uid = "test-user-123"
// Path: /profile_images/other-user-456/profile.jpg  
// Operation: write
// Expected: Deny
```

### 3. Index Performance Testing

Run test queries to verify index performance:

```javascript
// Test user search query
db.collection('user_search_cache')
  .where('isPublic', '==', true)
  .where('searchTokens', 'array-contains', 'test')
  .orderBy('lastActiveAt', 'desc')
  .limit(10)

// Test achievement query
db.collection('user_achievements')
  .where('userId', '==', 'test-user-123')
  .where('achievementType', '==', 'WORKOUT_MILESTONE')
  .orderBy('unlockedAt', 'desc')
```

## Troubleshooting

**Common Issues:**

1. **Security rules deny access:**
   - Verify user authentication
   - Check rule conditions match data structure
   - Test with Firebase Console simulator

2. **Missing indexes error:**
   - Check Firestore console for index creation status
   - Allow time for index building to complete
   - Verify composite index field order matches queries

3. **Storage upload failures:**
   - Verify file size limits (10MB max)
   - Check file format restrictions (JPEG, PNG, WebP)
   - Ensure CORS configuration for web uploads

4. **Performance issues:**
   - Monitor query complexity and costs
   - Review index usage in Firebase Console
   - Optimize query patterns to use indexes efficiently

## Maintenance

**Regular Tasks (Monthly):**
- Review Firebase usage and costs
- Clean up expired QR codes
- Monitor security rule effectiveness
- Update indexes based on query patterns

**Quarterly Reviews:**
- Audit user data and privacy compliance
- Review and update security rules
- Performance optimization based on metrics
- Backup and disaster recovery testing

## Support

For issues with this deployment:
1. Check Firebase Console for error details
2. Review logs in Firebase Functions (if applicable)  
3. Test individual components using Firebase emulators
4. Consult Firebase documentation for specific services

## Version History

- v1.0 (2025-07-25): Initial profile system deployment
- Security rules for profile privacy controls
- Storage rules for profile image management
- Composite indexes for optimal query performance
- Complete social discovery system support