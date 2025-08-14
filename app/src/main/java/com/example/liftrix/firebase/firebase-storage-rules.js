// Firebase Storage Security Rules for Liftrix Content Sharing and Media Management
// This file contains the security rules for Firebase Storage that need to be deployed
// Enhanced with media sharing capabilities from SPEC-20250113-content-sharing-media
// 
// To deploy these rules:
// 1. Copy the content below
// 2. Go to Firebase Console -> Storage -> Rules
// 3. Paste the rules and publish
//
// Last updated: 2025-01-13
// Compatible with: Liftrix Content Sharing System v1.0

rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    
    // Helper functions for validation
    function isAuthenticated() {
      return request.auth != null;
    }
    
    function isOwner(userId) {
      return request.auth.uid == userId;
    }
    
    function isValidImageFile() {
      return request.resource.contentType.matches('image/.*') &&
             request.resource.size < 10 * 1024 * 1024; // 10MB limit
    }
    
    function isValidImageFormat() {
      return request.resource.contentType.matches('image/(jpeg|jpg|png|webp|heic)');
    }
    
    function isValidVideoFile() {
      return request.resource.contentType.matches('video/.*') &&
             request.resource.size < 50 * 1024 * 1024; // 50MB limit for videos
    }
    
    function isValidVideoFormat() {
      return request.resource.contentType.matches('video/(mp4|mpeg|3gpp|quicktime|x-msvideo)');
    }
    
    function isValidMediaFile() {
      return (isValidImageFile() && isValidImageFormat()) || 
             (isValidVideoFile() && isValidVideoFormat());
    }
    
    function isPostPublic(postId) {
      return firestore.get(/databases/(default)/documents/workout_posts/$(postId)).data.visibility == 'PUBLIC';
    }
    
    function isPostVisible(userId, postId) {
      return isOwner(userId) || isPostPublic(postId);
    }
    
    // Profile images - users can upload/update their own, anyone can read public profiles
    match /profile_images/{userId}/{fileName} {
      allow read: if isAuthenticated() && 
                     (isOwner(userId) || 
                      isPublicProfile(userId));
                      
      allow write: if isAuthenticated() && 
                      isOwner(userId) && 
                      isValidImageFile() && 
                      isValidImageFormat();
      
      allow delete: if isAuthenticated() && 
                       isOwner(userId);
    }
    
    // Profile image thumbnails - generated server-side, readable for public profiles
    match /profile_images/{userId}/thumbnails/{thumbnailName} {
      allow read: if isAuthenticated() && 
                     (isOwner(userId) || 
                      isPublicProfile(userId));
      
      // Thumbnails are generated server-side only
      allow write, delete: if false;
    }
    
    // Temporary upload folder for image processing
    match /temp_uploads/{userId}/{fileName} {
      allow read, write: if isAuthenticated() && 
                           isOwner(userId) && 
                           isValidImageFile() && 
                           isValidImageFormat();
      
      allow delete: if isAuthenticated() && 
                       isOwner(userId);
    }
    
    // QR code images - generated server-side, readable by authenticated users
    match /qr_codes/{userId}/{qrFileName} {
      allow read: if isAuthenticated();
      
      // QR codes are generated server-side only
      allow write, delete: if false;
    }
    
    // Media content for posts and sharing
    match /media/{userId}/{mediaId}/{fileName} {
      allow read: if isAuthenticated() && 
                     (isOwner(userId) || 
                      resource.metadata.isPublic == 'true');
                      
      allow write: if isAuthenticated() && 
                      isOwner(userId) && 
                      isValidMediaFile();
      
      allow delete: if isAuthenticated() && 
                       isOwner(userId);
    }
    
    // Media thumbnails - generated server-side
    match /media/{userId}/{mediaId}/thumbnails/{thumbnailName} {
      allow read: if isAuthenticated() && 
                     (isOwner(userId) || 
                      resource.metadata.isPublic == 'true');
      
      // Thumbnails are generated server-side only
      allow write, delete: if false;
    }
    
    // Shared routine previews - generated server-side
    match /previews/routines/{shareToken}/{fileName} {
      allow read: if isAuthenticated();
      
      // Previews are generated server-side only
      allow write, delete: if false;
    }
    
    // Share image generation for external platforms
    match /share_images/{userId}/{shareId}/{fileName} {
      allow read: if isAuthenticated() && isOwner(userId);
      
      // Share images are generated server-side only
      allow write, delete: if false;
    }
    
    // Progress photos with privacy controls
    match /progress_photos/{userId}/{photoId}/{fileName} {
      allow read: if isAuthenticated() && 
                     (isOwner(userId) || 
                      (resource.metadata.isPrivate != 'true' && isPublicProfile(userId)));
                      
      allow write: if isAuthenticated() && 
                      isOwner(userId) && 
                      isValidImageFile() && 
                      isValidImageFormat();
      
      allow delete: if isAuthenticated() && 
                       isOwner(userId);
    }
    
    // CDN cached content - read-only mirror of original content
    match /cdn_cache/{allPaths=**} {
      allow read: if isAuthenticated();
      
      // CDN cache is managed server-side only
      allow write, delete: if false;
    }
    
    // Workout images (legacy support)
    match /workout_images/{userId}/{workoutId}/{fileName} {
      allow read, write: if isAuthenticated() && 
                           isOwner(userId) && 
                           isValidImageFile() && 
                           isValidImageFormat();
      
      allow delete: if isAuthenticated() && 
                       isOwner(userId);
    }
    
    // Default deny all other paths
    match /{allPaths=**} {
      allow read, write: if false;
    }
  }
  
  // Helper function to check if a user's profile is public
  // Note: This requires a Firestore lookup, which has performance implications
  function isPublicProfile(userId) {
    return firestore.get(/databases/(default)/documents/users_public/$(userId)).data.isPublic == true;
  }
}

/* 
DEPLOYMENT INSTRUCTIONS:

1. Firebase Console Setup:
   - Go to https://console.firebase.google.com
   - Select your Liftrix project
   - Navigate to Storage -> Rules

2. Copy and paste the above rules into the Firebase Console

3. Configure CORS for web access (if needed):
   Create a cors.json file with:
   [
     {
       "origin": ["https://your-app-domain.com"],
       "method": ["GET", "POST", "PUT", "DELETE"],
       "maxAgeSeconds": 3600
     }
   ]
   
   Then run: gsutil cors set cors.json gs://your-bucket-name

4. Test the rules using Firebase Console simulator:
   - Test profile image uploads for authenticated users
   - Test read access for public vs private profiles
   - Verify file size and format restrictions

5. Enhanced Storage Structure:
   /profile_images/{userId}/
     - profile.jpg (main profile image)
     - profile_cropped.jpg (cropped version)
     /thumbnails/
       - profile_thumb_150x150.jpg
       - profile_thumb_50x50.jpg
   
   /media/{userId}/{mediaId}/
     - original.jpg/mp4 (original media file)
     - compressed.jpg/mp4 (optimized version)
     /thumbnails/
       - thumb_300x300.jpg (thumbnail)
       - blurhash.txt (placeholder hash)
   
   /previews/routines/{shareToken}/
     - preview.jpg (routine share preview)
     - qr_code.png (QR code for sharing)
   
   /share_images/{userId}/{shareId}/
     - instagram_story.jpg (1080x1920)
     - instagram_post.jpg (1080x1080)
     - whatsapp_share.jpg (600x600)
     - twitter_card.jpg (1200x630)
   
   /progress_photos/{userId}/{photoId}/
     - original.jpg (original progress photo)
     - optimized.jpg (web-optimized version)
   
   /cdn_cache/{path}/
     - cached versions of frequently accessed content
   
   /temp_uploads/{userId}/
     - {timestamp}_{filename} (temporary files during processing)
   
   /qr_codes/{userId}/
     - profile_qr.png (generated QR code for profile sharing)
   
   /workout_images/{userId}/{workoutId}/
     - {filename} (legacy workout photos)

6. Performance Optimization:
   - Enable Firebase Storage caching headers
   - Configure CDN for image delivery
   - Set up automatic image optimization/compression

7. Enhanced Security Considerations:
   - Profile images are readable by anyone if profile is public
   - Media files have privacy controls based on post visibility
   - Progress photos respect user privacy settings
   - File size limits: 10MB for images, 50MB for videos
   - Supported formats: JPEG, JPG, PNG, WebP, HEIC for images; MP4, MPEG, 3GPP for videos
   - Users can only upload to their own folders
   - Server-side generated content (thumbnails, previews, share images) is read-only
   - CDN cache is managed server-side for performance

8. Monitoring:
   - Set up Firebase Storage usage alerts
   - Monitor for unusual upload patterns
   - Track storage costs and optimize accordingly
*/