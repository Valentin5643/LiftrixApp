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

// ================================
// SOCIAL FEED FUNCTIONS
// ================================

/**
 * Cloud Function triggered when a workout post is created
 * Generates personalized feeds for followers
 */
exports.generateFeedOnPostCreation = onDocumentWritten(
    "workout_posts/{postId}",
    async (event) => {
      const postId = event.params.postId;
      const postData = event.data?.after?.data();

      if (!postData) {
        logger.info(`Post deleted: ${postId}`);
        return null;
      }

      try {
        const userId = postData.user_id;
        const visibility = postData.visibility || "FOLLOWERS";

        // Only process posts that are visible to followers or public
        if (visibility === "PRIVATE") {
          logger.info(`Private post, skipping feed generation: ${postId}`);
          return null;
        }

        // Get all followers of the user who posted
        const followersSnapshot = await db.collection("follow_relationships")
            .where("following_user_id", "==", userId)
            .where("status", "==", "ACCEPTED")
            .get();

        const batch = db.batch();
        const feedEntries = [];

        // Calculate relevance score for the post
        const relevanceScore = calculatePostRelevanceScore(postData);

        // Add post to each follower's feed
        for (const followerDoc of followersSnapshot.docs) {
          const followerData = followerDoc.data();
          const followerId = followerData.follower_user_id;

          // Check if follower can see this post based on visibility and privacy
          if (await canUserViewPost(followerId, postData)) {
            const feedEntryRef = db.collection("feed_cache")
                .doc(`${followerId}_${postId}`);

            feedEntries.push({
              ref: feedEntryRef,
              data: {
                user_id: followerId,
                post_id: postId,
                author_id: userId,
                score: relevanceScore,
                created_at: new Date(),
                post_created_at: postData.created_at,
                visibility: visibility,
                engagement_count: (postData.like_count || 0) +
                                (postData.comment_count || 0),
              },
            });
          }
        }

        // Batch write feed entries (max 500 per batch)
        for (let i = 0; i < feedEntries.length; i += 500) {
          const batchChunk = feedEntries.slice(i, i + 500);
          const chunkBatch = db.batch();

          batchChunk.forEach(({ref, data}) => {
            chunkBatch.set(ref, data);
          });

          await chunkBatch.commit();
        }

        // If post is public, add to discovery feed
        if (visibility === "PUBLIC") {
          await addToDiscoveryFeed(postId, postData, relevanceScore);
        }

        logger.info(
            `Generated feed entries for ${feedEntries.length} followers ` +
            `for post: ${postId}`,
        );

        return null;
      } catch (error) {
        logger.error(`Error generating feed for post: ${postId}`, error);
        throw error;
      }
    });

/**
 * Cloud Function triggered when post engagement changes
 * Updates relevance scores in feed cache
 */
exports.updateFeedOnEngagement = onDocumentWritten(
    "{engagementType}/{engagementId}",
    async (event) => {
      const engagementType = event.params.engagementType;
      const engagementData = event.data?.after?.data();

      // Only process likes and comments
      if (!["post_likes", "post_comments"].includes(engagementType)) {
        return null;
      }

      if (!engagementData) {
        logger.info(`Engagement deleted: ${event.params.engagementId}`);
        return null;
      }

      try {
        const postId = engagementData.post_id;

        // Get updated post data
        const postDoc = await db.collection("workout_posts").doc(postId).get();
        if (!postDoc.exists) {
          logger.warn(`Post not found: ${postId}`);
          return null;
        }

        const postData = postDoc.data();
        const newScore = calculatePostRelevanceScore(postData);

        // Update all feed cache entries for this post
        const feedEntriesSnapshot = await db.collection("feed_cache")
            .where("post_id", "==", postId)
            .get();

        if (feedEntriesSnapshot.empty) {
          logger.info(`No feed entries found for post: ${postId}`);
          return null;
        }

        const batch = db.batch();
        feedEntriesSnapshot.docs.forEach((doc) => {
          batch.update(doc.ref, {
            score: newScore,
            engagement_count: (postData.like_count || 0) +
                            (postData.comment_count || 0),
            updated_at: new Date(),
          });
        });

        await batch.commit();

        logger.info(
            `Updated ${feedEntriesSnapshot.size} feed entries ` +
            `for post: ${postId}, new score: ${newScore}`,
        );

        return null;
      } catch (error) {
        logger.error(
            `Error updating feed on engagement: ${event.params.engagementId}`,
            error,
        );
        throw error;
      }
    });

/**
 * Scheduled function to clean up old feed cache entries
 * Runs daily to maintain feed cache performance
 */
exports.cleanupFeedCache = onSchedule(
    {
      schedule: "0 3 * * *", // Run daily at 3 AM
      timeZone: "UTC",
    },
    async (event) => {
      try {
        const oneWeekAgo = new Date();
        oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);

        // Find old feed entries
        const oldEntriesSnapshot = await db.collection("feed_cache")
            .where("created_at", "<=", oneWeekAgo)
            .limit(1000) // Process in chunks
            .get();

        if (oldEntriesSnapshot.empty) {
          logger.info("No old feed entries to clean up");
          return null;
        }

        const batch = db.batch();
        oldEntriesSnapshot.docs.forEach((doc) => {
          batch.delete(doc.ref);
        });

        await batch.commit();

        logger.info(
            `Cleaned up ${oldEntriesSnapshot.size} old feed cache entries`,
        );

        return null;
      } catch (error) {
        logger.error("Error cleaning up feed cache", error);
        throw error;
      }
    });

/**
 * HTTP function to regenerate feed for a specific user
 * Useful for testing and manual operations
 */
exports.regenerateUserFeed = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("Authentication required");
  }

  const targetUserId = request.data.userId || request.auth.uid;

  try {
    // Clear existing feed cache for user
    const existingFeedSnapshot = await db.collection("feed_cache")
        .where("user_id", "==", targetUserId)
        .get();

    const batch = db.batch();
    existingFeedSnapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    if (!existingFeedSnapshot.empty) {
      await batch.commit();
    }

    // Get users that the target user follows
    const followingSnapshot = await db.collection("follow_relationships")
        .where("follower_user_id", "==", targetUserId)
        .where("status", "==", "ACCEPTED")
        .get();

    const followingIds = followingSnapshot.docs
        .map((doc) => doc.data().following_user_id);

    if (followingIds.length === 0) {
      logger.info(`User ${targetUserId} follows no one, empty feed generated`);
      return {success: true, feedSize: 0};
    }

    // Get recent posts from followed users (last 30 days)
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const postsSnapshot = await db.collection("workout_posts")
        .where("user_id", "in", followingIds)
        .where("created_at", ">=", thirtyDaysAgo)
        .orderBy("created_at", "desc")
        .limit(200) // Limit to prevent timeout
        .get();

    // Generate feed entries
    const feedEntries = [];
    for (const postDoc of postsSnapshot.docs) {
      const postData = postDoc.data();
      const postId = postDoc.id;

      // Check privacy and visibility
      if (await canUserViewPost(targetUserId, postData)) {
        const relevanceScore = calculatePostRelevanceScore(postData);

        feedEntries.push({
          user_id: targetUserId,
          post_id: postId,
          author_id: postData.user_id,
          score: relevanceScore,
          created_at: new Date(),
          post_created_at: postData.created_at,
          visibility: postData.visibility || "FOLLOWERS",
          engagement_count: (postData.like_count || 0) +
                          (postData.comment_count || 0),
        });
      }
    }

    // Batch write feed entries
    for (let i = 0; i < feedEntries.length; i += 500) {
      const batchChunk = feedEntries.slice(i, i + 500);
      const chunkBatch = db.batch();

      batchChunk.forEach((entry) => {
        const ref = db.collection("feed_cache")
            .doc(`${entry.user_id}_${entry.post_id}`);
        chunkBatch.set(ref, entry);
      });

      await chunkBatch.commit();
    }

    logger.info(
        `Regenerated feed for user ${targetUserId} ` +
        `with ${feedEntries.length} entries`,
    );

    return {
      success: true,
      feedSize: feedEntries.length,
      userId: targetUserId,
    };
  } catch (error) {
    logger.error(`Error regenerating feed for user: ${targetUserId}`, error);
    throw new Error(`Failed to regenerate feed: ${error.message}`);
  }
});

// ================================
// HELPER FUNCTIONS
// ================================

/**
 * Calculate relevance score for a post
 * @param {object} postData - The post data from Firestore
 * @return {number} Relevance score (0-100)
 */
function calculatePostRelevanceScore(postData) {
  let score = 0;

  // Recency (max 40 points)
  const hoursSincePost = (Date.now() - postData.created_at.toDate().getTime()) / 3600000;
  score += Math.max(0, 40 - hoursSincePost * 0.5);

  // Engagement (max 30 points)
  const likes = postData.like_count || 0;
  const comments = postData.comment_count || 0;
  score += Math.min(30, likes * 0.5 + comments * 2);

  // PRs and achievements (max 20 points)
  const prs = postData.prs_count || 0;
  score += Math.min(20, prs * 10);

  // Media presence (max 10 points)
  const mediaUrls = postData.media_urls || [];
  if (mediaUrls.length > 0) {
    score += 10;
  }

  return Math.round(score);
}

/**
 * Check if a user can view a specific post based on privacy settings
 * @param {string} viewerId - ID of the user viewing the post
 * @param {object} postData - The post data from Firestore
 * @return {Promise<boolean>} Whether the user can view the post
 */
async function canUserViewPost(viewerId, postData) {
  const authorId = postData.user_id;
  const visibility = postData.visibility || "FOLLOWERS";

  // Author can always see their own posts
  if (viewerId === authorId) {
    return true;
  }

  // Public posts are visible to everyone
  if (visibility === "PUBLIC") {
    return true;
  }

  // Private posts are only visible to author
  if (visibility === "PRIVATE") {
    return false;
  }

  // Check if viewer is blocked by author
  const blockSnapshot = await db.collection("blocked_users")
      .where("blocker_user_id", "==", authorId)
      .where("blocked_user_id", "==", viewerId)
      .limit(1)
      .get();

  if (!blockSnapshot.empty) {
    return false;
  }

  // For FOLLOWERS visibility, check if viewer follows author
  if (visibility === "FOLLOWERS") {
    const followSnapshot = await db.collection("follow_relationships")
        .where("follower_user_id", "==", viewerId)
        .where("following_user_id", "==", authorId)
        .where("status", "==", "ACCEPTED")
        .limit(1)
        .get();

    return !followSnapshot.empty;
  }

  return false;
}

/**
 * Add post to discovery feed for non-followers
 * @param {string} postId - The post ID
 * @param {object} postData - The post data from Firestore
 * @param {number} relevanceScore - The calculated relevance score
 */
async function addToDiscoveryFeed(postId, postData, relevanceScore) {
  try {
    // For now, just log that we would add to discovery
    // In a full implementation, you would:
    // 1. Get users who don't follow the author
    // 2. Apply additional filtering (interests, location, etc.)
    // 3. Add to their discovery feed cache

    logger.info(
        `Would add post ${postId} to discovery feed ` +
        `(score: ${relevanceScore})`,
    );
  } catch (error) {
    logger.error(`Error adding post ${postId} to discovery feed`, error);
  }
}
