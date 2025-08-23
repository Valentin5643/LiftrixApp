const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}

/**
 * Cloud Function to set admin claims for a user
 * Can be called via HTTP request or from your app
 */
exports.setAdminClaim = functions.https.onCall(async (data, context) => {
  // Check if the request is made by an existing admin
  if (context.auth && context.auth.token.admin !== true) {
    return { 
      error: 'Request not authorized. User must be an admin to grant admin access.' 
    };
  }
  
  const { targetUserId } = data;
  
  if (!targetUserId) {
    return { error: 'Target user ID is required' };
  }
  
  try {
    // Set admin custom claim on the target user
    await admin.auth().setCustomUserClaims(targetUserId, { 
      admin: true 
    });
    
    return { 
      success: true,
      message: `Admin claim set for user ${targetUserId}` 
    };
  } catch (error) {
    console.error('Error setting admin claim:', error);
    return { 
      error: error.message 
    };
  }
});

/**
 * Alternative: HTTP endpoint version (less secure, use with caution)
 */
exports.setAdminClaimHttp = functions.https.onRequest(async (req, res) => {
  // In production, add proper authentication here
  const { userId, secret } = req.body;
  
  // Simple secret check (replace with proper auth in production)
  if (secret !== process.env.ADMIN_SECRET) {
    return res.status(403).json({ error: 'Unauthorized' });
  }
  
  if (!userId) {
    return res.status(400).json({ error: 'userId is required' });
  }
  
  try {
    await admin.auth().setCustomUserClaims(userId, { 
      admin: true 
    });
    
    res.json({ 
      success: true,
      message: `Admin claim set for user ${userId}` 
    });
  } catch (error) {
    console.error('Error setting admin claim:', error);
    res.status(500).json({ error: error.message });
  }
});