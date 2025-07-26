// Firestore Security Rules for Liftrix Profile System
// This file contains the security rules that need to be deployed to Firebase Console
// 
// To deploy these rules:
// 1. Copy the content below
// 2. Go to Firebase Console -> Firestore Database -> Rules
// 3. Paste the rules and publish
//
// Last updated: 2025-07-25
// Compatible with: Liftrix Profile System v1.0

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Helper functions for common validation
    function isAuthenticated() {
      return request.auth != null;
    }
    
    function isOwner(userId) {
      return request.auth.uid == userId;
    }
    
    function isValidUserProfile() {
      return request.resource.data.keys().hasAll(['userId', 'displayName', 'isPublic', 'updatedAt']) &&
             request.resource.data.userId == request.auth.uid &&
             request.resource.data.displayName is string &&
             request.resource.data.displayName.size() > 0 &&
             request.resource.data.displayName.size() <= 50 &&
             request.resource.data.isPublic is bool;
    }
    
    function isValidBio() {
      return !('bio' in request.resource.data) || 
             (request.resource.data.bio is string && 
              request.resource.data.bio.size() <= 500);
    }
    
    function isValidAge() {
      return !('age' in request.resource.data) || 
             (request.resource.data.age is int && 
              request.resource.data.age >= 13 && 
              request.resource.data.age <= 100);
    }
    
    // Users collection - private user data
    match /users/{userId} {
      allow read, write: if isAuthenticated() && isOwner(userId);
    }
    
    // Users public profiles - publicly accessible profile data
    match /users_public/{userId} {
      allow read: if isAuthenticated() &&
                     (isOwner(userId) || 
                      resource.data.isPublic == true);
                      
      allow write: if isAuthenticated() && 
                      isOwner(userId) && 
                      isValidUserProfile() && 
                      isValidBio() && 
                      isValidAge();
      
      allow create: if isAuthenticated() && 
                       isOwner(userId) && 
                       isValidUserProfile() && 
                       isValidBio() && 
                       isValidAge();
    }
    
    // User achievements - read-only for profile owners
    match /user_achievements/{userId} {
      allow read: if isAuthenticated() && isOwner(userId);
      allow write: if false; // Achievements are managed server-side only
    }
    
    // QR code mappings for profile sharing
    match /qr_codes/{qrCodeId} {
      allow read: if isAuthenticated();
      allow write: if isAuthenticated() && 
                      isOwner(request.resource.data.userId) &&
                      request.resource.data.keys().hasAll(['userId', 'createdAt', 'expiresAt']) &&
                      request.resource.data.userId == request.auth.uid;
    }
    
    // User search cache - publicly readable for authenticated users
    match /user_search_cache/{userId} {
      allow read: if isAuthenticated() && 
                     resource.data.isPublic == true;
      allow write: if isAuthenticated() && 
                      isOwner(userId) &&
                      request.resource.data.userId == request.auth.uid;
    }
    
    // Connection management (for future friend system)
    match /connections/{connectionId} {
      allow read: if isAuthenticated() && 
                     (isOwner(resource.data.fromUserId) || 
                      isOwner(resource.data.toUserId));
      allow write: if isAuthenticated() && 
                      isOwner(request.resource.data.fromUserId) &&
                      request.resource.data.fromUserId == request.auth.uid;
    }
    
    // Profile image metadata
    match /profile_images/{userId} {
      allow read: if isAuthenticated() && 
                     (isOwner(userId) || 
                      get(/databases/$(database)/documents/users_public/$(userId)).data.isPublic == true);
      allow write: if isAuthenticated() && 
                      isOwner(userId) &&
                      request.resource.data.userId == request.auth.uid;
    }
    
    // Workout data - private to user
    match /workouts/{userId}/user_workouts/{workoutId} {
      allow read, write: if isAuthenticated() && isOwner(userId);
    }
    
    // Exercise data - publicly readable reference data
    match /exercises/{exerciseId} {
      allow read: if isAuthenticated();
      allow write: if false; // Exercise data is read-only
    }
    
    // Default deny all other paths
    match /{document=**} {
      allow read, write: if false;
    }
  }
}

/* 
DEPLOYMENT INSTRUCTIONS:

1. Firebase Console Setup:
   - Go to https://console.firebase.google.com
   - Select your Liftrix project
   - Navigate to Firestore Database -> Rules

2. Copy and paste the above rules into the Firebase Console

3. Test the rules using the Firebase Console simulator with:
   - auth.uid = "test-user-123"
   - Test reading/writing users_public documents
   - Test privacy controls (isPublic field)

4. Required Firestore Indexes:
   Create these composite indexes in Firebase Console:

   Collection: users_public
   Fields: isPublic (Ascending), displayName (Ascending)
   
   Collection: user_search_cache  
   Fields: isPublic (Ascending), displayName (Ascending), lastActiveAt (Descending)
   
   Collection: qr_codes
   Fields: userId (Ascending), expiresAt (Ascending)

5. Verify deployment by testing:
   - Profile creation and updates
   - Privacy setting changes
   - User search functionality
   - QR code generation and lookup
*/