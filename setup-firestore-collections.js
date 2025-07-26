// Firebase Firestore Collection Setup Script
// Run with: node setup-firestore-collections.js

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
// Make sure you have GOOGLE_APPLICATION_CREDENTIALS environment variable set
// or use the serviceAccountKey.json file
const serviceAccount = require('./serviceAccountKey.json'); // Download from Firebase Console

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'liftrix-390cf',
  databaseURL: 'https://liftrix-390cf-default-rtdb.firebaseio.com'
});

const db = admin.firestore();

async function setupCollections() {
  console.log('Setting up Firestore collections for Liftrix profile system...');

  try {
    // 1. Create sample users_public document
    const publicProfileRef = db.collection('users_public').doc('sample-user-123');
    await publicProfileRef.set({
      userId: 'sample-user-123',
      displayName: 'Sample User',
      bio: 'Welcome to Liftrix! This is a sample profile to demonstrate the profile system.',
      isPublic: true,
      profileImageUrl: null,
      age: 25,
      memberSince: admin.firestore.Timestamp.now(),
      lastActiveAt: admin.firestore.Timestamp.now(),
      updatedAt: admin.firestore.Timestamp.now(),
      // Profile completion tracking
      profileCompletionScore: 75,
      equipmentTypes: ['dumbbells', 'barbells'],
      fitnessGoals: ['muscle_gain', 'strength'],
      preferredWorkoutDuration: 60
    });
    console.log('✅ Created users_public sample document');

    // 2. Create sample user_search_cache document
    const searchCacheRef = db.collection('user_search_cache').doc('sample-user-123');
    await searchCacheRef.set({
      userId: 'sample-user-123',
      displayName: 'Sample User',
      searchTokens: ['sample', 'user', 'liftrix'],
      isPublic: true,
      profileImageUrl: null,
      lastActiveAt: admin.firestore.Timestamp.now(),
      totalWorkouts: 0,
      memberSince: admin.firestore.Timestamp.now(),
      profileCompletionScore: 75
    });
    console.log('✅ Created user_search_cache sample document');

    // 3. Create sample qr_codes document
    const qrCodeRef = db.collection('qr_codes').doc('qr-sample-123');
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 30); // Expire in 30 days
    
    await qrCodeRef.set({
      userId: 'sample-user-123',
      qrCodeUrl: 'https://chart.googleapis.com/chart?chs=200x200&cht=qr&chl=https://liftrix.app/profile/sample-user-123',
      profileUrl: 'https://liftrix.app/profile/sample-user-123',
      createdAt: admin.firestore.Timestamp.now(),
      expiresAt: admin.firestore.Timestamp.fromDate(expiresAt)
    });
    console.log('✅ Created qr_codes sample document');

    // 4. Create sample user_achievements document
    const achievementsRef = db.collection('user_achievements').doc('sample-user-123');
    await achievementsRef.set({
      userId: 'sample-user-123',
      achievements: [
        {
          id: 'first_workout',
          achievementType: 'WORKOUT_MILESTONE',
          title: 'First Workout Complete',
          description: 'Completed your first workout session',
          isDisplayed: true,
          unlockedAt: admin.firestore.Timestamp.now(),
          iconName: 'trophy'
        },
        {
          id: 'profile_setup',
          achievementType: 'PROFILE_MILESTONE',
          title: 'Profile Creator',
          description: 'Set up your complete profile',
          isDisplayed: true,
          unlockedAt: admin.firestore.Timestamp.now(),
          iconName: 'user'
        }
      ],
      totalAchievements: 2,
      displayedAchievements: 2,
      lastUpdated: admin.firestore.Timestamp.now()
    });
    console.log('✅ Created user_achievements sample document');

    // 5. Create sample profile_images metadata document
    const profileImageRef = db.collection('profile_images').doc('sample-user-123');
    await profileImageRef.set({
      userId: 'sample-user-123',
      originalUrl: null,
      thumbnailUrl: null,
      croppedUrl: null,
      uploadedAt: null,
      updatedAt: admin.firestore.Timestamp.now(),
      fileSize: 0,
      dimensions: {
        width: 0,
        height: 0
      }
    });
    console.log('✅ Created profile_images sample document');

    // 6. Create sample connections document (for future social features)
    const connectionsRef = db.collection('connections').doc('connection-sample-123');
    await connectionsRef.set({
      fromUserId: 'sample-user-123',
      toUserId: 'sample-user-456',
      status: 'pending',
      createdAt: admin.firestore.Timestamp.now(),
      updatedAt: admin.firestore.Timestamp.now()
    });
    console.log('✅ Created connections sample document');

    // 7. Create sample users (private) document
    const privateUserRef = db.collection('users').doc('sample-user-123');
    await privateUserRef.set({
      userId: 'sample-user-123',
      email: 'sample@liftrix.app',
      subscription_tier: 'free',
      subscription_status: 'active',
      premium_features_enabled: false,
      profile_version: 1,
      created_at: admin.firestore.Timestamp.now(),
      updated_at: admin.firestore.Timestamp.now(),
      // Privacy settings
      privacy: {
        profileVisibility: 'public',
        showAchievements: true,
        showWorkoutHistory: false,
        showConnectionCount: true
      }
    });
    console.log('✅ Created users (private) sample document');

    console.log('\n🎉 All Firestore collections set up successfully!');
    console.log('\nNext steps:');
    console.log('1. Verify collections in Firebase Console');
    console.log('2. Test security rules with these sample documents');
    console.log('3. Run index performance tests');
    
  } catch (error) {
    console.error('❌ Error setting up collections:', error);
    throw error;
  }
}

// Run the setup
setupCollections()
  .then(() => {
    console.log('Setup completed successfully');
    process.exit(0);
  })
  .catch((error) => {
    console.error('Setup failed:', error);
    process.exit(1);
  });