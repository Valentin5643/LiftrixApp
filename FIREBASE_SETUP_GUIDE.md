# Complete Firebase Setup Guide - Liftrix

This guide will help you complete ALL remaining Firebase setup steps properly.

## Prerequisites

1. **Download Service Account Key:**
   - Go to [Firebase Console](https://console.firebase.google.com) → Project Settings → Service Accounts
   - Click "Generate new private key"
   - Save as `serviceAccountKey.json` in project root
   - **NEVER commit this file to git**

2. **Install Dependencies:**
   ```bash
   npm install firebase-admin
   ```

## Step 1: Create Required Firestore Collections ✅ HIGH PRIORITY

```bash
# Run the automated collection setup
node setup-firestore-collections.js
```

This creates sample documents for:
- `users_public` - Public profile data
- `user_search_cache` - Search optimization data
- `qr_codes` - QR code mappings
- `user_achievements` - Achievement data
- `profile_images` - Image metadata
- `connections` - Social connections
- `users` - Private user data

## Step 2: Configure Firebase Storage ✅ HIGH PRIORITY

```bash
# Create storage folder structure
node setup-storage-structure.js

# Apply CORS configuration (requires Google Cloud SDK)
gsutil cors set cors.json gs://liftrix-390cf.firebasestorage.app
```

### Manual Storage Setup (if gsutil not available):

1. **In Firebase Console → Storage:**
   - Create folders: `profile_images/`, `temp_uploads/`, `qr_codes/`, `workout_images/`
   - Upload a test image to verify permissions

2. **CORS Configuration:**
   - Use Google Cloud Console → Storage → Browser
   - Select your bucket → Edit bucket details → Add CORS configuration:
   ```json
   [
     {
       "origin": ["https://liftrix.app", "http://localhost:3000"],
       "method": ["GET", "POST", "PUT", "DELETE"],
       "maxAgeSeconds": 3600
     }
   ]
   ```

## Step 3: Verify Firebase Authentication 🔶 MEDIUM PRIORITY

### Check Current Auth Setup:

1. **Firebase Console → Authentication → Sign-in method:**
   - ✅ Email/Password should be enabled
   - ✅ Google should be enabled (recommended)
   - Configure authorized domains for production

2. **Authentication Templates:**
   - Go to Authentication → Templates
   - Customize email verification template
   - Customize password reset template

3. **Security Settings:**
   - Enable email enumeration protection
   - Set up reCAPTCHA for web (optional)

### Test Authentication:
```bash
# Create test script to verify auth works
node test-auth.js
```

## Step 4: Complete Firebase Functions Deployment 🔶 MEDIUM PRIORITY

The Functions deployment failed due to Git bash path issues. Let's fix this:

### Option A: Use PowerShell instead of Git Bash
```powershell
# In PowerShell, not Git Bash
cd C:\Users\Administrator\Liftrix\functions
npm install
npm run lint
cd ..
firebase deploy --only functions
```

### Option B: Fix Git Bash Path
```bash
# Update Windows PATH to correct Git bash location
# OR use Windows Command Prompt instead
```

### Option C: Deploy Functions Manually
1. Zip the `functions` folder
2. Upload via Firebase Console → Functions → Upload ZIP

## Step 5: Enable Performance Monitoring 🔶 MEDIUM PRIORITY

### In Firebase Console:

1. **Performance Monitoring:**
   - Go to Performance tab
   - Click "Get started" 
   - Follow integration guide for Android

2. **Crashlytics:**
   - Go to Crashlytics tab
   - Click "Get started"
   - Add Crashlytics SDK to Android app

3. **Analytics:**
   - Go to Analytics tab
   - Set up conversion events:
     - `profile_completed`
     - `first_workout_logged`
     - `achievement_unlocked`

## Step 6: Test Security Rules and Storage Access 🔶 MEDIUM PRIORITY

### Test Security Rules in Firebase Console:

1. **Firestore Rules Testing:**
   ```javascript
   // Test public profile read access
   // Path: /databases/(default)/documents/users_public/sample-user-123
   // Auth: {"uid": "test-user-456"}
   // Operation: get
   // Expected: Allow (profile is public)

   // Test profile write access
   // Path: /databases/(default)/documents/users_public/test-user-456
   // Auth: {"uid": "test-user-456"}
   // Operation: create
   // Expected: Allow (user owns profile)
   ```

2. **Storage Rules Testing:**
   ```javascript
   // Test profile image upload
   // Path: /profile_images/test-user-456/profile.jpg
   // Auth: {"uid": "test-user-456"}
   // Operation: create
   // Expected: Allow (user owns folder)

   // Test unauthorized upload
   // Path: /profile_images/other-user-123/profile.jpg
   // Auth: {"uid": "test-user-456"}
   // Operation: create
   // Expected: Deny (wrong user)
   ```

### Test Index Performance:

Run these queries in Firebase Console to verify indexes work:

```javascript
// User search query
db.collection('user_search_cache')
  .where('isPublic', '==', true)
  .where('searchTokens', 'array-contains', 'sample')
  .orderBy('lastActiveAt', 'desc')
  .limit(10)

// Achievement query
db.collection('user_achievements')
  .where('userId', '==', 'sample-user-123')
  .orderBy('achievements.unlockedAt', 'desc')
```

## Verification Checklist

After completing all steps, verify:

- [ ] ✅ Firestore collections exist with sample data
- [ ] ✅ Storage folder structure created
- [ ] ✅ CORS configuration applied
- [ ] ⚠️ Authentication providers working
- [ ] ⚠️ Functions deployed successfully
- [ ] ⚠️ Performance monitoring enabled
- [ ] ⚠️ Security rules tested and working
- [ ] ⚠️ Index performance verified

## Troubleshooting

### Common Issues:

1. **"Permission denied" errors:**
   - Check service account key is valid
   - Verify Firebase project ID is correct

2. **Storage upload failures:**
   - Verify CORS configuration
   - Check file size limits (10MB max)

3. **Functions deployment hanging:**
   - Use PowerShell instead of Git Bash
   - Check internet connection and Firebase CLI version

4. **Index creation errors:**
   - Allow time for index building (can take 10+ minutes)
   - Check field names match exactly

## Support

If you encounter issues:
1. Check Firebase Console for detailed error messages
2. Use Firebase CLI debug mode: `firebase --debug deploy`
3. Test individual components using Firebase emulators

## Next Steps After Setup

Once all setup is complete:
1. Update Android app to use production Firebase config
2. Test profile creation, image upload, and search flows
3. Monitor Firebase usage and costs
4. Set up automated backups for production data