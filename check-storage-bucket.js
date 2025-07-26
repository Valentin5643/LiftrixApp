// Firebase Storage Bucket Detection Script
// Run with: node check-storage-bucket.js

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'liftrix-390cf',  // Explicitly set project ID
  // Don't specify storage bucket initially - let's detect it
});

async function checkStorageBucket() {
  console.log('Checking Firebase Storage bucket configuration...\n');

  try {
    // Method 1: Try to get the default bucket
    console.log('🔍 Attempting to detect default storage bucket...');
    
    const projectId = admin.app().options.projectId;
    console.log(`Project ID: ${projectId}`);

    // Common bucket naming patterns for Firebase projects
    const possibleBuckets = [
      `${projectId}.appspot.com`,
      `${projectId}.firebasestorage.app`,
      `gs://${projectId}.appspot.com`,
      `gs://${projectId}.firebasestorage.app`
    ];

    console.log('\n🔍 Testing possible bucket names:');
    
    for (const bucketName of possibleBuckets) {
      try {
        console.log(`   Testing: ${bucketName}`);
        
        // Clean bucket name (remove gs:// prefix if present)
        const cleanBucketName = bucketName.replace('gs://', '');
        
        const bucket = admin.storage().bucket(cleanBucketName);
        
        // Try to check if bucket exists by getting metadata
        const [metadata] = await bucket.getMetadata();
        
        console.log(`✅ Found working bucket: ${cleanBucketName}`);
        console.log(`   Storage Class: ${metadata.storageClass}`);
        console.log(`   Location: ${metadata.location}`);
        console.log(`   Created: ${metadata.timeCreated}`);
        
        // Test bucket access by listing files
        try {
          const [files] = await bucket.getFiles({ maxResults: 1 });
          console.log(`   Access: ✅ Can list files (${files.length} files found)`);
        } catch (listError) {
          console.log(`   Access: ⚠️ Can't list files: ${listError.message}`);
        }

        return cleanBucketName;
        
      } catch (error) {
        if (error.code === 404) {
          console.log(`   ❌ Not found: ${bucketName}`);
        } else if (error.code === 403) {
          console.log(`   ⚠️ Access denied: ${bucketName} (bucket exists but no permission)`);
        } else {
          console.log(`   ❌ Error: ${error.message}`);
        }
      }
    }

    console.log('\n❌ No accessible storage bucket found!');
    console.log('\n💡 Storage might not be enabled. Here\'s how to enable it:');
    console.log('\n1. Manual Setup (Recommended):');
    console.log('   • Go to https://console.firebase.google.com');
    console.log('   • Select your Liftrix project');
    console.log('   • Click "Storage" in the left sidebar');
    console.log('   • Click "Get started"');
    console.log('   • Choose "Start in test mode" (we\'ll apply security rules later)');
    console.log('   • Select a location (us-central1 recommended)');
    console.log('   • Click "Done"');
    
    console.log('\n2. CLI Setup (Alternative):');
    console.log('   firebase init storage');
    
    return null;

  } catch (error) {
    console.error('❌ Error checking storage bucket:', error.message);
    
    if (error.code === 'storage/unknown') {
      console.log('\n💡 This suggests Firebase Storage is not enabled for your project.');
    }
    
    throw error;
  }
}

// Run the check
checkStorageBucket()
  .then((bucketName) => {
    if (bucketName) {
      console.log(`\n✨ Success! Use this bucket name in your scripts: ${bucketName}`);
      console.log('\nUpdate setup-storage-structure.js with:');
      console.log(`storageBucket: '${bucketName}'`);
    } else {
      console.log('\n⏳ Enable Firebase Storage first, then run this script again.');
    }
    process.exit(0);
  })
  .catch((error) => {
    console.error('\n💥 Storage bucket check failed!');
    console.error('Error details:', error.message);
    process.exit(1);
  });