// Firebase Storage Setup Script
// Run with: node setup-storage-structure.js

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Initialize Firebase Admin SDK (reuse from collections script)
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: 'liftrix-390cf',
  storageBucket: 'liftrix-390cf.firebasestorage.app'
});

const bucket = admin.storage().bucket();

async function setupStorageStructure() {
  console.log('Setting up Firebase Storage structure for Liftrix...');

  try {
    // Create folder structure by uploading placeholder files
    const folders = [
      'profile_images/.gitkeep',
      'profile_images/thumbnails/.gitkeep', 
      'temp_uploads/.gitkeep',
      'qr_codes/.gitkeep',
      'workout_images/.gitkeep'
    ];

    for (const folderPath of folders) {
      const file = bucket.file(folderPath);
      await file.save('# Folder structure placeholder\n# This file maintains the folder structure in Firebase Storage\n');
      console.log(`✅ Created folder: ${path.dirname(folderPath)}`);
    }

    // Create CORS configuration file
    const corsConfig = [
      {
        "origin": ["https://liftrix.app", "https://liftrix-390cf.web.app", "http://localhost:3000"],
        "method": ["GET", "POST", "PUT", "DELETE", "HEAD"],
        "maxAgeSeconds": 3600,
        "responseHeader": ["Content-Type", "Authorization"]
      }
    ];

    fs.writeFileSync('./cors.json', JSON.stringify(corsConfig, null, 2));
    console.log('✅ Created cors.json configuration file');

    // Upload a sample profile image placeholder
    const placeholderImagePath = 'profile_images/sample-user-123/placeholder.png';
    const placeholderContent = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
    
    const placeholderFile = bucket.file(placeholderImagePath);
    await placeholderFile.save(placeholderContent, {
      metadata: {
        contentType: 'image/png'
      }
    });
    console.log('✅ Created sample profile image placeholder');

    console.log('\n🎉 Firebase Storage structure set up successfully!');
    console.log('\nFolder structure created:');
    console.log('├── profile_images/');
    console.log('│   ├── thumbnails/');
    console.log('│   └── sample-user-123/');
    console.log('├── temp_uploads/');
    console.log('├── qr_codes/');
    console.log('└── workout_images/');

    console.log('\nNext steps:');
    console.log('1. Apply CORS configuration: gsutil cors set cors.json gs://liftrix-390cf.firebasestorage.app');
    console.log('2. Test file upload permissions');
    console.log('3. Verify storage rules in Firebase Console');

  } catch (error) {
    console.error('❌ Error setting up storage structure:', error);
    throw error;
  }
}

// Run the setup
setupStorageStructure()
  .then(() => {
    console.log('Storage setup completed successfully');
    process.exit(0);
  })
  .catch((error) => {
    console.error('Storage setup failed:', error);
    process.exit(1);
  });