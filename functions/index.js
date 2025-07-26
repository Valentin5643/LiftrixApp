/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

// const {onRequest} = require("firebase-functions/v2/https");
// const logger = require("firebase-functions/logger");

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore} = require("firebase-admin/firestore");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onCall} = require("firebase-functions/v2/https");
const {logger} = require("firebase-functions");

// Initialize Firebase Admin SDK
initializeApp();

const auth = getAuth();
const db = getFirestore();

/**
 * Cloud Function triggered when a subscription document is created or updated
 * Assigns custom claims based on subscription tier and status
 */
exports.updateUserCustomClaims = onDocumentWritten(
    "subscriptions/{subscriptionId}",
    async (event) => {
      const subscriptionId = event.params.subscriptionId;

      try {
        // Get the subscription data
        const subscriptionData = event.data?.after?.data();

        if (!subscriptionData) {
          logger.warn(
              `No subscription data found for subscription: ${subscriptionId}`,
          );
          return null;
        }

        const userId = subscriptionData.user_id;
        if (!userId) {
          logger.error(`No user_id found in subscription: ${subscriptionId}`);
          return null;
        }

        // Skip if claims were already updated for this version
        if (subscriptionData.claims_updated) {
          logger.info(
              `Claims already updated for subscription: ${subscriptionId}`,
          );
          return null;
        }

        // Calculate custom claims based on subscription
        const customClaims = await calculateCustomClaims(
            userId, subscriptionData,
        );

        // Set custom claims for the user
        await auth.setCustomUserClaims(userId, customClaims);

        // Update the subscription document to mark claims as updated
        await db.collection("subscriptions").doc(subscriptionId).update({
          claims_updated: true,
          claims_updated_at: new Date(),
          updated_at: new Date(),
        });

        // Update user profile with subscription status
        await updateUserProfile(userId, subscriptionData);

        logger.info(
            `Custom claims updated for user: ${userId}, ` +
            `subscription: ${subscriptionId}`,
            {
              userId,
              subscriptionId,
              tier: subscriptionData.tier,
              status: subscriptionData.status,
              claims: customClaims,
            });

        return null;
      } catch (error) {
        logger.error(
            `Error updating custom claims for subscription: ${subscriptionId}`,
            error,
        );
        throw error;
      }
    });

/**
 * Calculate custom claims based on subscription data
 * @param {string} userId - The user ID
 * @param {object} subscriptionData - The subscription data from Firestore
 * @return {object} Custom claims object
 */
async function calculateCustomClaims(userId, subscriptionData) {
  const tier = subscriptionData.tier || "free";
  const status = subscriptionData.status || "active";
  const expiresAt = subscriptionData.expires_at;
  const features = subscriptionData.features || [];

  // Check if subscription is currently active
  const isActive = status === "active" || status === "trial";
  const isExpired = expiresAt && expiresAt.toDate() < new Date();
  const isPremiumActive = tier !== "free" && isActive && !isExpired;

  // Base claims
  const claims = {
    premium: isPremiumActive,
    tier: tier,
    status: status,
    subscription_updated_at: new Date().toISOString(),
  };

  // Add feature-specific claims
  if (isPremiumActive) {
    // Get feature set based on tier
    const tierFeatures = getTierFeatures(tier);
    const allFeatures = new Set([...tierFeatures, ...features]);

    // Add feature flags
    claims.features = Array.from(allFeatures);

    // Add specific feature flags for easy access in security rules
    allFeatures.forEach((feature) => {
      claims[`feature_${feature}`] = true;
    });

    // Add tier-specific flags
    if (tier === "premium") {
      claims.premium_tier = true;
    } else if (tier === "pro") {
      claims.pro_tier = true;
      claims.premium_tier = true; // Pro includes premium features
    }
  }

  // Add expiration info for time-based checks
  if (expiresAt) {
    claims.expires_at = expiresAt.toDate().toISOString();
  }

  return claims;
}

/**
 * Get default features for each tier
 * @param {string} tier - The subscription tier
 * @return {Array<string>} Array of feature names
 */
function getTierFeatures(tier) {
  switch (tier) {
    case "premium":
      return [
        "ai_summaries",
        "advanced_analytics",
        "premium_templates",
        "priority_support",
      ];
    case "pro":
      return [
        "ai_summaries",
        "advanced_analytics",
        "premium_templates",
        "priority_support",
        "unlimited_workouts",
        "custom_templates",
        "export_data",
        "team_features",
      ];
    default:
      return [];
  }
}

/**
 * Update user profile with subscription information
 * @param {string} userId - The user ID
 * @param {object} subscriptionData - The subscription data from Firestore
 */
async function updateUserProfile(userId, subscriptionData) {
  try {
    const tier = subscriptionData.tier || "free";
    const status = subscriptionData.status || "active";
    const expiresAt = subscriptionData.expires_at;

    const isActive = status === "active" || status === "trial";
    const isExpired = expiresAt && expiresAt.toDate() < new Date();
    const premiumEnabled = tier !== "free" && isActive && !isExpired;

    const updateData = {
      subscription_tier: tier,
      subscription_status: status,
      premium_features_enabled: premiumEnabled,
      profile_version: db.FieldValue.increment(1),
      updated_at: new Date(),
    };

    if (expiresAt) {
      updateData.subscription_expires_at = expiresAt;
    }

    await db.collection("users").doc(userId).update(updateData);

    logger.info(`User profile updated for user: ${userId}`, {
      userId,
      tier,
      status,
      premiumEnabled,
    });
  } catch (error) {
    logger.error(`Error updating user profile for user: ${userId}`, error);
    // Don't throw here - we don't want to fail the main function
  }
}

/**
 * Cloud Function to handle subscription expiration cleanup
 * Can be triggered by Cloud Scheduler daily
 */
exports.cleanupExpiredSubscriptions = onSchedule(
    {
      schedule: "0 2 * * *", // Run daily at 2 AM
      timeZone: "UTC",
    },
    async (event) => {
      try {
        const now = new Date();

        // Find expired subscriptions that haven't been cleaned up
        const expiredSubscriptions = await db.collection("subscriptions")
            .where("expires_at", "<=", now)
            .where("status", "in", ["active", "trial"])
            .get();

        const batch = db.batch();
        let updateCount = 0;

        for (const doc of expiredSubscriptions.docs) {
          // Update subscription to expired status
          batch.update(doc.ref, {
            status: "expired",
            claims_updated: false, // Force claims update
            updated_at: now,
          });

          updateCount++;

          // Firestore batch limit is 500 operations
          if (updateCount >= 500) {
            await batch.commit();
            updateCount = 0;
          }
        }

        if (updateCount > 0) {
          await batch.commit();
        }

        logger.info(
            `Cleaned up ${expiredSubscriptions.size} expired subscriptions`,
        );
        return null;
      } catch (error) {
        logger.error("Error cleaning up expired subscriptions", error);
        throw error;
      }
    });

/**
 * HTTP function to manually trigger custom claims update for a user
 * Useful for testing and manual operations
 */
exports.updateUserClaimsManually = onCall(async (request) => {
  // Verify the request is from an authenticated admin user
  if (!request.auth || !request.auth.token.admin) {
    throw new Error("Only admin users can manually update claims");
  }

  const userId = request.data.userId;
  if (!userId) {
    throw new Error("userId is required");
  }

  try {
    // Get the user's active subscription
    const subscriptionQuery = await db.collection("subscriptions")
        .where("user_id", "==", userId)
        .where("status", "in", ["active", "trial"])
        .orderBy("created_at", "desc")
        .limit(1)
        .get();

    if (subscriptionQuery.empty) {
      throw new Error("No active subscription found for user");
    }

    const subscriptionDoc = subscriptionQuery.docs[0];
    const subscriptionData = subscriptionDoc.data();

    // Calculate and set custom claims
    const customClaims = await calculateCustomClaims(
        userId, subscriptionData,
    );
    await auth.setCustomUserClaims(userId, customClaims);

    // Mark subscription as claims updated
    await subscriptionDoc.ref.update({
      claims_updated: true,
      claims_updated_at: new Date(),
    });

    // Update user profile
    await updateUserProfile(userId, subscriptionData);

    logger.info(`Manual claims update completed for user: ${userId}`);

    return {
      success: true,
      userId: userId,
      claims: customClaims,
    };
  } catch (error) {
    logger.error(
        `Error in manual claims update for user: ${userId}`, error,
    );
    throw new Error(`Failed to update user claims: ${error.message}`);
  }
});
