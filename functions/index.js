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
    logger.info(`Adding post ${postId} to discovery feed (score: ${relevanceScore})`);

    // Only add public posts to discovery feed
    if (postData.visibility !== "PUBLIC") {
      logger.info(`Post ${postId} is not public, skipping discovery feed`);
      return;
    }

    // Get a sample of users who don't follow the author
    // For performance, we'll limit this to 1000 users per post
    const discoveryTargets = await findDiscoveryTargets(postData.user_id, 1000);

    if (discoveryTargets.length === 0) {
      logger.info(`No discovery targets found for post ${postId}`);
      return;
    }

    // Create feed entries for discovery users
    const batch = db.batch();
    let addedCount = 0;

    for (const targetUserId of discoveryTargets) {
      const feedRef = db.collection("feed_cache").doc(`${targetUserId}_discovery_${postId}`);
      
      batch.set(feedRef, {
        user_id: targetUserId,
        post_id: postId,
        author_id: postData.user_id,
        feed_type: "DISCOVERY",
        relevance_score: relevanceScore,
        created_at: admin.firestore.FieldValue.serverTimestamp(),
        post_created_at: postData.created_at,
        // Add content preview for faster loading
        preview_data: {
          author_name: postData.author_display_name || "Unknown",
          workout_name: postData.workout_summary?.name || "",
          exercise_count: postData.workout_summary?.exercise_count || 0,
          media_count: postData.media_urls?.length || 0,
          prs_count: postData.workout_summary?.prs_count || 0
        }
      });

      addedCount++;

      // Process in batches of 500 to avoid Firestore limits
      if (addedCount % 500 === 0) {
        await batch.commit();
        logger.info(`Committed batch of ${addedCount} discovery feed entries`);
      }
    }

    // Commit any remaining entries
    if (addedCount % 500 !== 0) {
      await batch.commit();
    }

    logger.info(`Added post ${postId} to discovery feed for ${addedCount} users`);

  } catch (error) {
    logger.error(`Error adding post ${postId} to discovery feed`, error);
    throw error;
  }
}

/**
 * Finds users who should see this post in their discovery feed
 * Excludes users who already follow the author
 */
async function findDiscoveryTargets(authorUserId, maxTargets = 1000) {
  try {
    // Get users who already follow the author (to exclude them)
    const followersSnapshot = await db.collection("follow_relationships")
        .where("target_user_id", "==", authorUserId)
        .where("status", "==", "FOLLOWING")
        .select("follower_id")
        .get();

    const followerIds = new Set(followersSnapshot.docs.map(doc => doc.data().follower_id));
    followerIds.add(authorUserId); // Also exclude the author themselves

    // Get a sample of active users (users who have posted recently)
    const recentActiveUsersSnapshot = await db.collection("users")
        .where("last_active_at", ">=", getXDaysAgo(30)) // Active in last 30 days
        .where("privacy_settings.discoverable", "==", true) // Opted into discovery
        .limit(maxTargets * 2) // Get more than needed to account for filtering
        .get();

    const discoveryTargets = [];
    
    for (const userDoc of recentActiveUsersSnapshot.docs) {
      const userId = userDoc.id;
      
      // Skip if user follows author or is the author
      if (followerIds.has(userId)) {
        continue;
      }

      // Apply additional filtering based on user preferences
      const userData = userDoc.data();
      if (await shouldAddToUserDiscovery(userId, userData, authorUserId)) {
        discoveryTargets.push(userId);
        
        if (discoveryTargets.length >= maxTargets) {
          break;
        }
      }
    }

    logger.info(`Found ${discoveryTargets.length} discovery targets for author ${authorUserId}`);
    return discoveryTargets;

  } catch (error) {
    logger.error(`Error finding discovery targets for author ${authorUserId}`, error);
    return [];
  }
}

/**
 * Determines if a post should be added to a specific user's discovery feed
 * Based on user interests, engagement patterns, and preferences
 */
async function shouldAddToUserDiscovery(userId, userData, authorUserId) {
  try {
    // Check if user has discovery enabled
    if (!userData.privacy_settings?.discoverable) {
      return false;
    }

    // Check if user has blocked the author
    const blockSnapshot = await db.collection("blocked_users")
        .where("blocker_id", "==", userId)
        .where("blocked_id", "==", authorUserId)
        .limit(1)
        .get();
    
    if (!blockSnapshot.empty) {
      return false;
    }

    // Check discovery feed frequency preference
    const discoverySettings = userData.discovery_settings || {};
    const maxDiscoveryPerDay = discoverySettings.max_posts_per_day || 10;
    
    // Check how many discovery posts user has received today
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    const todayDiscoverySnapshot = await db.collection("feed_cache")
        .where("user_id", "==", userId)
        .where("feed_type", "==", "DISCOVERY")
        .where("created_at", ">=", today)
        .select()
        .get();
    
    if (todayDiscoverySnapshot.size >= maxDiscoveryPerDay) {
      return false;
    }

    // Simple interest matching could be added here
    // For now, we'll use a basic randomization to avoid overwhelming users
    return Math.random() < 0.3; // 30% chance to include

  } catch (error) {
    logger.warn(`Error checking discovery eligibility for user ${userId}`, error);
    return false;
  }
}

/**
 * Helper function to get date X days ago
 */
function getXDaysAgo(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date;
}

// ================================
// NOTIFICATION SYSTEM FUNCTIONS
// ================================

const {getMessaging} = require("firebase-admin/messaging");
const messaging = getMessaging();

/**
 * Cloud Function to send immediate notifications
 * Called when high-priority events occur (PRs, follow requests)
 */
exports.sendImmediateNotification = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("Authentication required");
  }

  const {targetUserId, type, title, body, data} = request.data;
  
  if (!targetUserId || !type || !title || !body) {
    throw new Error("Missing required notification parameters");
  }

  try {
    // Check notification preferences
    const preferencesDoc = await db.collection("notification_preferences")
        .doc(targetUserId).get();
    
    if (!preferencesDoc.exists) {
      logger.warn(`No notification preferences found for user: ${targetUserId}`);
      return {success: false, reason: "No preferences found"};
    }

    const preferences = preferencesDoc.data();
    
    // Check master toggle
    if (!preferences.notifications_enabled) {
      logger.info(`Notifications disabled for user: ${targetUserId}`);
      return {success: false, reason: "Notifications disabled"};
    }

    // Check category-specific preferences
    if (!isNotificationTypeEnabled(type, preferences)) {
      logger.info(`Notification type ${type} disabled for user: ${targetUserId}`);
      return {success: false, reason: "Notification type disabled"};
    }

    // Check if we're in quiet hours
    if (isInQuietHours(preferences)) {
      logger.info(`In quiet hours, queueing notification for user: ${targetUserId}`);
      await queueNotificationForLater(targetUserId, type, title, body, data, preferences);
      return {success: true, reason: "Queued for quiet hours"};
    }

    // Check for mutes
    const fromUserId = data?.fromUserId;
    if (fromUserId && await isUserMuted(targetUserId, fromUserId, type)) {
      logger.info(`User ${fromUserId} muted by ${targetUserId}`);
      return {success: false, reason: "User muted"};
    }

    // Get FCM tokens for user
    const tokensSnapshot = await db.collection("fcm_tokens")
        .where("user_id", "==", targetUserId)
        .where("is_active", "==", true)
        .get();

    if (tokensSnapshot.empty) {
      logger.warn(`No active FCM tokens found for user: ${targetUserId}`);
      return {success: false, reason: "No active tokens"};
    }

    // Send to all active tokens
    const tokens = tokensSnapshot.docs.map((doc) => doc.data().token);
    const message = {
      notification: {
        title: title,
        body: body,
      },
      data: {
        type: type,
        ...data,
      },
      android: {
        priority: "high",
        notification: {
          channelId: getChannelIdForType(type),
          priority: "high",
          defaultSound: preferences.notification_sound !== false,
          defaultVibrateTimings: preferences.notification_vibration !== false,
        },
      },
      tokens: tokens,
    };

    const response = await messaging.sendEachForMulticast(message);

    // Process any failed tokens
    const failedTokens = [];
    response.responses.forEach((resp, idx) => {
      if (!resp.success) {
        failedTokens.push(tokens[idx]);
        logger.warn(`Failed to send to token: ${tokens[idx]}, error: ${resp.error}`);
      }
    });

    // Deactivate failed tokens
    if (failedTokens.length > 0) {
      await deactivateFailedTokens(failedTokens);
    }

    // Store notification in history
    await storeNotificationHistory(targetUserId, type, title, body, data);

    logger.info(
        `Sent notification to ${response.successCount} devices ` +
        `for user: ${targetUserId}, type: ${type}`,
    );

    return {
      success: true,
      sentCount: response.successCount,
      failedCount: response.failureCount,
    };
  } catch (error) {
    logger.error(`Error sending immediate notification: ${error.message}`, error);
    throw new Error(`Failed to send notification: ${error.message}`);
  }
});

/**
 * Cloud Function triggered when workout post creates PR
 * Sends immediate notification to gym buddies
 */
exports.notifyGymBuddyPR = onDocumentWritten(
    "workout_posts/{postId}",
    async (event) => {
      const postData = event.data?.after?.data();
      
      if (!postData || !postData.prs_count || postData.prs_count === 0) {
        return null;
      }

      try {
        const authorId = postData.user_id;
        const postId = event.params.postId;

        // Get gym buddies (mutual followers)
        const gymBuddiesSnapshot = await db.collection("follow_relationships")
            .where("following_user_id", "==", authorId)
            .where("status", "==", "ACCEPTED")
            .get();

        // Get author's profile for display name
        const authorDoc = await db.collection("social_profiles").doc(authorId).get();
        const authorName = authorDoc.exists ? 
            authorDoc.data().display_name : "A gym buddy";

        // Send notification to each gym buddy
        for (const buddyDoc of gymBuddiesSnapshot.docs) {
          const buddyId = buddyDoc.data().follower_user_id;
          
          // Check if they're also friends (mutual follow)
          const mutualFollowSnapshot = await db.collection("follow_relationships")
              .where("follower_user_id", "==", authorId)
              .where("following_user_id", "==", buddyId)
              .where("status", "==", "ACCEPTED")
              .limit(1)
              .get();

          if (mutualFollowSnapshot.empty) {
            continue; // Only notify mutual followers (gym buddies)
          }

          const prDetails = postData.prs_achieved || ["New PR!"];
          const prText = prDetails.length > 1 ? 
              `${prDetails.length} PRs` : prDetails[0];

          await sendImmediateNotificationInternal(
              buddyId,
              "GYM_BUDDY_PR",
              `🎉 ${authorName} hit a PR!`,
              prText,
              {
                postId: postId,
                fromUserId: authorId,
                fromUserName: authorName,
                prDetail: prText,
              },
          );
        }

        logger.info(`Sent gym buddy PR notifications for post: ${postId}`);
        return null;
      } catch (error) {
        logger.error(`Error sending gym buddy PR notifications: ${error.message}`, error);
        throw error;
      }
    });

/**
 * Cloud Function triggered when follow request is created
 * Sends immediate notification to target user
 */
exports.notifyFollowRequest = onDocumentWritten(
    "follow_relationships/{relationshipId}",
    async (event) => {
      const relationshipData = event.data?.after?.data();
      
      if (!relationshipData || relationshipData.status !== "PENDING") {
        return null;
      }

      try {
        const followerId = relationshipData.follower_user_id;
        const followingId = relationshipData.following_user_id;

        // Get follower's profile
        const followerDoc = await db.collection("social_profiles").doc(followerId).get();
        const followerName = followerDoc.exists ? 
            followerDoc.data().display_name : "Someone";

        await sendImmediateNotificationInternal(
            followingId,
            "FOLLOW_REQUEST",
            "New Follow Request",
            `${followerName} wants to follow you`,
            {
              fromUserId: followerId,
              fromUserName: followerName,
              relationshipId: event.params.relationshipId,
            },
        );

        logger.info(`Sent follow request notification from ${followerId} to ${followingId}`);
        return null;
      } catch (error) {
        logger.error(`Error sending follow request notification: ${error.message}`, error);
        throw error;
      }
    });

/**
 * Scheduled function to process batched notifications
 * Runs every hour to send grouped social notifications
 */
exports.processBatchedNotifications = onSchedule(
    {
      schedule: "0 * * * *", // Every hour
      timeZone: "UTC",
    },
    async (event) => {
      try {
        const now = new Date();
        const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000);

        // Get pending notifications ready for batching
        const pendingSnapshot = await db.collection("notification_queue")
            .where("status", "==", "PENDING")
            .where("scheduled_for", "<=", now)
            .where("can_batch", "==", true)
            .limit(1000)
            .get();

        if (pendingSnapshot.empty) {
          logger.info("No pending notifications to batch");
          return null;
        }

        // Group by user and batch key
        const userBatches = {};
        pendingSnapshot.docs.forEach((doc) => {
          const notification = doc.data();
          const userId = notification.user_id;
          const batchKey = notification.batch_key || "default";

          if (!userBatches[userId]) {
            userBatches[userId] = {};
          }
          if (!userBatches[userId][batchKey]) {
            userBatches[userId][batchKey] = [];
          }

          userBatches[userId][batchKey].push({
            id: doc.id,
            ...notification,
          });
        });

        let processedCount = 0;

        // Process each user's batched notifications
        for (const [userId, batches] of Object.entries(userBatches)) {
          for (const [batchKey, notifications] of Object.entries(batches)) {
            await processBatchForUser(userId, notifications);
            processedCount += notifications.length;
          }
        }

        logger.info(`Processed ${processedCount} batched notifications for ${Object.keys(userBatches).length} users`);
        return null;
      } catch (error) {
        logger.error("Error processing batched notifications", error);
        throw error;
      }
    });

/**
 * Scheduled function to send quiet hours notifications
 * Runs every morning to send queued notifications
 */
exports.sendQuietHoursNotifications = onSchedule(
    {
      schedule: "0 8 * * *", // 8 AM UTC daily
      timeZone: "UTC",
    },
    async (event) => {
      try {
        const now = new Date();

        // Get notifications scheduled for quiet hours
        const queuedSnapshot = await db.collection("notification_queue")
            .where("status", "==", "PENDING")
            .where("scheduled_for", "<=", now)
            .limit(1000)
            .get();

        if (queuedSnapshot.empty) {
          logger.info("No quiet hours notifications to send");
          return null;
        }

        let sentCount = 0;

        // Process each notification
        for (const doc of queuedSnapshot.docs) {
          const notification = doc.data();
          
          try {
            await sendQueuedNotification(notification);
            await doc.ref.update({
              status: "SENT",
              sent_at: now,
            });
            sentCount++;
          } catch (error) {
            logger.error(`Failed to send queued notification ${doc.id}:`, error);
            await doc.ref.update({
              status: "FAILED",
              failure_reason: error.message,
            });
          }
        }

        logger.info(`Sent ${sentCount} quiet hours notifications`);
        return null;
      } catch (error) {
        logger.error("Error sending quiet hours notifications", error);
        throw error;
      }
    });

// ================================
// NOTIFICATION HELPER FUNCTIONS
// ================================

/**
 * Internal helper to send immediate notification
 */
async function sendImmediateNotificationInternal(targetUserId, type, title, body, data) {
  // This mirrors the main sendImmediateNotification function but for internal use
  const preferencesDoc = await db.collection("notification_preferences")
      .doc(targetUserId).get();
  
  if (!preferencesDoc.exists || !preferencesDoc.data().notifications_enabled) {
    return false;
  }

  const preferences = preferencesDoc.data();
  
  if (!isNotificationTypeEnabled(type, preferences)) {
    return false;
  }

  if (isInQuietHours(preferences)) {
    await queueNotificationForLater(targetUserId, type, title, body, data, preferences);
    return true;
  }

  const tokensSnapshot = await db.collection("fcm_tokens")
      .where("user_id", "==", targetUserId)
      .where("is_active", "==", true)
      .get();

  if (tokensSnapshot.empty) {
    return false;
  }

  const tokens = tokensSnapshot.docs.map((doc) => doc.data().token);
  const message = {
    notification: {title, body},
    data: {type, ...data},
    android: {
      priority: "high",
      notification: {
        channelId: getChannelIdForType(type),
        priority: "high",
        defaultSound: preferences.notification_sound !== false,
        defaultVibrateTimings: preferences.notification_vibration !== false,
      },
    },
    tokens: tokens,
  };

  const response = await messaging.sendEachForMulticast(message);
  
  if (response.failureCount > 0) {
    const failedTokens = [];
    response.responses.forEach((resp, idx) => {
      if (!resp.success) {
        failedTokens.push(tokens[idx]);
      }
    });
    await deactivateFailedTokens(failedTokens);
  }

  await storeNotificationHistory(targetUserId, type, title, body, data);
  return response.successCount > 0;
}

/**
 * Check if notification type is enabled in user preferences
 */
function isNotificationTypeEnabled(type, preferences) {
  switch (type) {
    case "GYM_BUDDY_PR":
      return preferences.gym_buddy_prs !== false;
    case "FOLLOW_REQUEST":
      return preferences.follow_requests !== false;
    case "POST_LIKE":
      return preferences.post_likes !== false;
    case "POST_COMMENT":
      return preferences.post_comments !== false;
    case "MENTION":
      return preferences.mentions !== false;
    case "ACHIEVEMENT":
      return preferences.achievement_notifications !== false;
    case "WORKOUT_REMINDER":
      return preferences.reminder_notifications !== false;
    default:
      return preferences.social_notifications !== false;
  }
}

/**
 * Check if current time is in user's quiet hours
 */
function isInQuietHours(preferences) {
  if (!preferences.quiet_hours_enabled) {
    return false;
  }

  const now = new Date();
  const currentHour = now.getHours();
  const startHour = preferences.quiet_hours_start || 22;
  const endHour = preferences.quiet_hours_end || 8;

  if (startHour <= endHour) {
    return currentHour >= startHour && currentHour < endHour;
  } else {
    return currentHour >= startHour || currentHour < endHour;
  }
}

/**
 * Queue notification for later delivery (quiet hours)
 */
async function queueNotificationForLater(userId, type, title, body, data, preferences) {
  const tomorrow8AM = new Date();
  tomorrow8AM.setDate(tomorrow8AM.getDate() + 1);
  tomorrow8AM.setHours(preferences.quiet_hours_end || 8, 0, 0, 0);

  await db.collection("notification_queue").add({
    user_id: userId,
    type: type,
    title: title,
    body: body,
    data: JSON.stringify(data),
    priority: "NORMAL",
    channel_id: getChannelIdForType(type),
    batch_key: `${type}_${userId}`,
    can_batch: type !== "GYM_BUDDY_PR", // PRs should be immediate
    scheduled_for: tomorrow8AM,
    expires_at: new Date(tomorrow8AM.getTime() + 24 * 60 * 60 * 1000), // Expire after 24 hours
    status: "PENDING",
    created_at: new Date(),
  });
}

/**
 * Check if user has muted notifications from another user
 */
async function isUserMuted(userId, fromUserId, notificationType) {
  const muteSnapshot = await db.collection("notification_mutes")
      .where("user_id", "==", userId)
      .where("mute_type", "==", "USER")
      .where("muted_user_id", "==", fromUserId)
      .limit(1)
      .get();

  if (muteSnapshot.empty) {
    return false;
  }

  const muteData = muteSnapshot.docs[0].data();
  const mutedUntil = muteData.muted_until;
  
  // If no expiry, it's permanent
  if (!mutedUntil) {
    return true;
  }

  // Check if mute has expired
  return mutedUntil.toDate() > new Date();
}

/**
 * Get notification channel ID for Android
 */
function getChannelIdForType(type) {
  switch (type) {
    case "GYM_BUDDY_PR":
      return "gym_buddy_channel";
    case "FOLLOW_REQUEST":
      return "social_requests_channel";
    case "POST_LIKE":
    case "POST_COMMENT":
    case "MENTION":
      return "social_engagement_channel";
    case "ACHIEVEMENT":
      return "achievement_channel";
    case "WORKOUT_REMINDER":
      return "reminder_channel";
    default:
      return "default_channel";
  }
}

/**
 * Deactivate FCM tokens that failed to send
 */
async function deactivateFailedTokens(failedTokens) {
  const batch = db.batch();
  
  for (const token of failedTokens) {
    const tokenQuery = await db.collection("fcm_tokens")
        .where("token", "==", token)
        .limit(1)
        .get();
    
    if (!tokenQuery.empty) {
      batch.update(tokenQuery.docs[0].ref, {
        is_active: false,
        updated_at: new Date(),
      });
    }
  }
  
  await batch.commit();
}

/**
 * Store notification in user's history
 */
async function storeNotificationHistory(userId, type, title, body, data) {
  await db.collection("notification_history").add({
    user_id: userId,
    type: type,
    title: title,
    body: body,
    data: JSON.stringify(data),
    is_read: false,
    received_at: new Date(),
  });
}

/**
 * Process batched notifications for a user
 */
async function processBatchForUser(userId, notifications) {
  if (notifications.length === 1) {
    // Send single notification
    await sendQueuedNotification(notifications[0]);
  } else if (notifications.length <= 4) {
    // Send as expandable notification
    await sendBatchedNotification(userId, notifications, "inbox");
  } else {
    // Send as summary
    await sendBatchedNotification(userId, notifications, "summary");
  }

  // Mark all as sent
  const batch = db.batch();
  notifications.forEach((notification) => {
    const ref = db.collection("notification_queue").doc(notification.id);
    batch.update(ref, {
      status: "SENT",
      sent_at: new Date(),
    });
  });
  
  await batch.commit();
}

/**
 * Send a queued notification
 */
async function sendQueuedNotification(notification) {
  const data = notification.data ? JSON.parse(notification.data) : {};
  
  return await sendImmediateNotificationInternal(
      notification.user_id,
      notification.type,
      notification.title,
      notification.body,
      data,
  );
}

/**
 * Send batched notification (inbox or summary style)
 */
async function sendBatchedNotification(userId, notifications, style) {
  const tokensSnapshot = await db.collection("fcm_tokens")
      .where("user_id", "==", userId)
      .where("is_active", "==", true)
      .get();

  if (tokensSnapshot.empty) {
    return;
  }

  const tokens = tokensSnapshot.docs.map((doc) => doc.data().token);
  
  let title, body;
  if (style === "summary") {
    title = `${notifications.length} new updates`;
    body = "Tap to view all notifications";
  } else {
    title = `${notifications.length} new updates`;
    body = notifications.map((n) => `${n.title}: ${n.body}`).join("\n");
  }

  const message = {
    notification: {title, body},
    data: {
      type: "BATCHED",
      count: notifications.length.toString(),
      style: style,
    },
    android: {
      notification: {
        channelId: "social_engagement_channel",
      },
    },
    tokens: tokens,
  };

  await messaging.sendEachForMulticast(message);
  
  // Store in history
  await storeNotificationHistory(userId, "BATCHED", title, body, {
    count: notifications.length,
    notifications: notifications.map((n) => ({
      type: n.type,
      title: n.title,
      body: n.body,
    })),
  });
}

// ================================
// USER MANAGEMENT & BANNING FUNCTIONS
// ================================

/**
 * Admin-only Cloud Function to ban/disable users
 * Disables Firebase Auth account and updates user document
 */
exports.banUser = onCall(async (request) => {
  // Verify admin permissions
  if (!request.auth || !request.auth.token.admin) {
    throw new Error("Only admin users can ban other users");
  }

  const { userId, reason, banDuration, severity } = request.data;

  if (!userId || !reason) {
    throw new Error("userId and reason are required");
  }

  try {
    logger.info(`Admin ${request.auth.uid} attempting to ban user: ${userId}`);

    // Get user record to check if user exists
    const userRecord = await auth.getUser(userId);

    if (!userRecord) {
      throw new Error(`User not found: ${userId}`);
    }

    // Disable Firebase Auth account
    await auth.updateUser(userId, {
      disabled: true,
      customClaims: {
        ...userRecord.customClaims,
        banned: true,
        banReason: reason,
        bannedBy: request.auth.uid,
        bannedAt: new Date().toISOString(),
        banDuration: banDuration || null,
        severity: severity || "moderate"
      }
    });

    // Create ban record in Firestore
    const banRecord = {
      userId: userId,
      bannedBy: request.auth.uid,
      reason: reason,
      severity: severity || "moderate",
      banDuration: banDuration || null, // null means permanent
      bannedAt: new Date(),
      status: "active",
      userEmail: userRecord.email || null,
      userDisplayName: userRecord.displayName || null,
      metadata: {
        userCreatedAt: userRecord.metadata.creationTime,
        lastSignIn: userRecord.metadata.lastSignInTime,
        providerData: userRecord.providerData.map(p => p.providerId)
      }
    };

    const banDoc = await db.collection("user_bans").add(banRecord);

    // Update user profile to reflect ban status
    try {
      await db.collection("users").doc(userId).update({
        accountStatus: "banned",
        bannedAt: new Date(),
        bannedBy: request.auth.uid,
        banReason: reason,
        banSeverity: severity || "moderate",
        profileVersion: db.FieldValue.increment(1),
        updated_at: new Date()
      });
    } catch (profileError) {
      logger.warn(`Could not update user profile for banned user ${userId}:`, profileError);
    }

    // Block user in social contexts
    try {
      // Add to global blocked users collection
      await db.collection("globally_blocked_users").doc(userId).set({
        userId: userId,
        bannedBy: request.auth.uid,
        reason: reason,
        bannedAt: new Date(),
        status: "active"
      });

      // Remove from public discoverable profiles
      const socialProfileRef = db.collection("social_profiles").doc(userId);
      await socialProfileRef.update({
        isDiscoverable: false,
        profileVisibility: "PRIVATE",
        accountStatus: "banned",
        updatedAt: new Date()
      });
    } catch (socialError) {
      logger.warn(`Could not update social data for banned user ${userId}:`, socialError);
    }

    // Log the ban action for auditing
    await db.collection("admin_actions").add({
      actionType: "BAN_USER",
      performedBy: request.auth.uid,
      targetUserId: userId,
      details: {
        reason: reason,
        severity: severity || "moderate",
        banDuration: banDuration || "permanent",
        userEmail: userRecord.email
      },
      timestamp: new Date(),
      outcome: "success"
    });

    logger.info(`User ${userId} successfully banned by admin ${request.auth.uid}. Reason: ${reason}`);

    return {
      success: true,
      userId: userId,
      banId: banDoc.id,
      bannedAt: banRecord.bannedAt,
      message: `User ${userId} has been banned successfully`
    };

  } catch (error) {
    logger.error(`Error banning user ${userId}:`, error);
    
    // Log failed ban attempt
    await db.collection("admin_actions").add({
      actionType: "BAN_USER",
      performedBy: request.auth.uid,
      targetUserId: userId,
      details: {
        reason: reason,
        severity: severity || "moderate",
        error: error.message
      },
      timestamp: new Date(),
      outcome: "failed"
    });

    throw new Error(`Failed to ban user: ${error.message}`);
  }
});

/**
 * Admin-only Cloud Function to unban/re-enable users
 */
exports.unbanUser = onCall(async (request) => {
  // Verify admin permissions
  if (!request.auth || !request.auth.token.admin) {
    throw new Error("Only admin users can unban other users");
  }

  const { userId, reason } = request.data;

  if (!userId) {
    throw new Error("userId is required");
  }

  try {
    logger.info(`Admin ${request.auth.uid} attempting to unban user: ${userId}`);

    // Re-enable Firebase Auth account
    await auth.updateUser(userId, {
      disabled: false,
      customClaims: {
        banned: false,
        unbannedBy: request.auth.uid,
        unbannedAt: new Date().toISOString(),
        unbanReason: reason || "Appeal approved"
      }
    });

    // Update ban record to inactive
    const banQuery = await db.collection("user_bans")
      .where("userId", "==", userId)
      .where("status", "==", "active")
      .limit(1)
      .get();

    if (!banQuery.empty) {
      await banQuery.docs[0].ref.update({
        status: "inactive",
        unbannedBy: request.auth.uid,
        unbannedAt: new Date(),
        unbanReason: reason || "Appeal approved"
      });
    }

    // Update user profile
    try {
      await db.collection("users").doc(userId).update({
        accountStatus: "active",
        bannedAt: db.FieldValue.delete(),
        bannedBy: db.FieldValue.delete(),
        banReason: db.FieldValue.delete(),
        banSeverity: db.FieldValue.delete(),
        unbannedAt: new Date(),
        unbannedBy: request.auth.uid,
        profileVersion: db.FieldValue.increment(1),
        updated_at: new Date()
      });
    } catch (profileError) {
      logger.warn(`Could not update user profile for unbanned user ${userId}:`, profileError);
    }

    // Remove from globally blocked users
    try {
      await db.collection("globally_blocked_users").doc(userId).delete();

      // Restore social profile (but keep as private by default)
      const socialProfileRef = db.collection("social_profiles").doc(userId);
      await socialProfileRef.update({
        accountStatus: "active",
        updatedAt: new Date()
        // Note: Don't automatically restore discoverability - user should opt back in
      });
    } catch (socialError) {
      logger.warn(`Could not update social data for unbanned user ${userId}:`, socialError);
    }

    // Log the unban action
    await db.collection("admin_actions").add({
      actionType: "UNBAN_USER",
      performedBy: request.auth.uid,
      targetUserId: userId,
      details: {
        reason: reason || "Appeal approved"
      },
      timestamp: new Date(),
      outcome: "success"
    });

    logger.info(`User ${userId} successfully unbanned by admin ${request.auth.uid}`);

    return {
      success: true,
      userId: userId,
      unbannedAt: new Date(),
      message: `User ${userId} has been unbanned successfully`
    };

  } catch (error) {
    logger.error(`Error unbanning user ${userId}:`, error);
    
    // Log failed unban attempt
    await db.collection("admin_actions").add({
      actionType: "UNBAN_USER",
      performedBy: request.auth.uid,
      targetUserId: userId,
      details: {
        reason: reason,
        error: error.message
      },
      timestamp: new Date(),
      outcome: "failed"
    });

    throw new Error(`Failed to unban user: ${error.message}`);
  }
});

/**
 * Admin-only Cloud Function to get user ban history and details
 */
exports.getUserBanInfo = onCall(async (request) => {
  // Verify admin permissions
  if (!request.auth || !request.auth.token.admin) {
    throw new Error("Only admin users can view ban information");
  }

  const { userId } = request.data;

  if (!userId) {
    throw new Error("userId is required");
  }

  try {
    // Get Firebase Auth user info
    const userRecord = await auth.getUser(userId);
    
    // Get ban history from Firestore
    const banHistorySnapshot = await db.collection("user_bans")
      .where("userId", "==", userId)
      .orderBy("bannedAt", "desc")
      .get();

    const banHistory = banHistorySnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      bannedAt: doc.data().bannedAt?.toDate?.()?.toISOString() || doc.data().bannedAt,
      unbannedAt: doc.data().unbannedAt?.toDate?.()?.toISOString() || doc.data().unbannedAt
    }));

    // Get current user profile info
    let userProfile = null;
    try {
      const profileDoc = await db.collection("users").doc(userId).get();
      if (profileDoc.exists) {
        userProfile = profileDoc.data();
      }
    } catch (profileError) {
      logger.warn(`Could not fetch profile for user ${userId}:`, profileError);
    }

    return {
      success: true,
      userInfo: {
        uid: userRecord.uid,
        email: userRecord.email,
        displayName: userRecord.displayName,
        disabled: userRecord.disabled,
        createdAt: userRecord.metadata.creationTime,
        lastSignIn: userRecord.metadata.lastSignInTime,
        customClaims: userRecord.customClaims || {},
        providerData: userRecord.providerData.map(p => ({
          providerId: p.providerId,
          email: p.email,
          displayName: p.displayName
        }))
      },
      userProfile: userProfile,
      banHistory: banHistory,
      currentlyBanned: userRecord.disabled || userRecord.customClaims?.banned === true
    };

  } catch (error) {
    logger.error(`Error getting ban info for user ${userId}:`, error);
    throw new Error(`Failed to get user ban info: ${error.message}`);
  }
});

/**
 * Admin-only Cloud Function to list all banned users
 */
exports.listBannedUsers = onCall(async (request) => {
  // Verify admin permissions
  if (!request.auth || !request.auth.token.admin) {
    throw new Error("Only admin users can list banned users");
  }

  const { limit = 50, offset = 0, severity = null } = request.data;

  try {
    let query = db.collection("user_bans")
      .where("status", "==", "active")
      .orderBy("bannedAt", "desc");

    if (severity) {
      query = query.where("severity", "==", severity);
    }

    query = query.limit(limit);
    if (offset > 0) {
      const offsetSnapshot = await db.collection("user_bans")
        .where("status", "==", "active")
        .orderBy("bannedAt", "desc")
        .limit(offset)
        .get();
      
      if (!offsetSnapshot.empty) {
        const lastVisible = offsetSnapshot.docs[offsetSnapshot.docs.length - 1];
        query = query.startAfter(lastVisible);
      }
    }

    const snapshot = await query.get();
    
    const bannedUsers = snapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      bannedAt: doc.data().bannedAt?.toDate?.()?.toISOString() || doc.data().bannedAt
    }));

    // Get total count for pagination
    const totalSnapshot = await db.collection("user_bans")
      .where("status", "==", "active")
      .select()
      .get();

    return {
      success: true,
      bannedUsers: bannedUsers,
      total: totalSnapshot.size,
      hasMore: snapshot.size === limit,
      nextOffset: offset + snapshot.size
    };

  } catch (error) {
    logger.error("Error listing banned users:", error);
    throw new Error(`Failed to list banned users: ${error.message}`);
  }
});

/**
 * Admin-only Cloud Function to search users by email or display name
 * Fixed implementation with robust search similar to social user search
 */
exports.searchUsers = onCall(async (request) => {
  // Verify admin permissions  
  if (!request.auth || !request.auth.token.admin) {
    throw new Error("Only admin users can search for users");
  }

  const { query: searchQuery, limit = 50 } = request.data;

  if (!searchQuery || searchQuery.length < 3) {
    throw new Error("Search query must be at least 3 characters long");
  }

  try {
    logger.info(`Admin user search initiated with query: "${searchQuery}"`);
    
    // Normalize search query for case-insensitive matching
    const normalizedQuery = searchQuery.toLowerCase().trim();
    
    // Get all users from the collection (since Firestore doesn't support case-insensitive queries well)
    // We'll limit the initial query and do client-side filtering
    const allUsersSnapshot = await db.collection("users")
      .limit(500) // Get a reasonable subset to search through
      .get();

    logger.info(`Retrieved ${allUsersSnapshot.size} users for search processing`);

    // Client-side filtering for robust search functionality
    const matchingUsers = [];
    const userMap = new Map();
    
    allUsersSnapshot.docs.forEach(doc => {
      const data = doc.data();
      const userId = doc.id;
      
      // Extract searchable fields
      const email = (data.email || '').toLowerCase();
      const displayName = (data.displayName || '').toLowerCase();
      const username = (data.username || '').toLowerCase();
      
      // Apply flexible matching logic (similar to social search)
      const matchesEmail = email.includes(normalizedQuery);
      const matchesDisplayName = displayName.includes(normalizedQuery);
      const matchesUsername = username.includes(normalizedQuery);
      
      if (matchesEmail || matchesDisplayName || matchesUsername) {
        // Create user info object
        const userInfo = {
          uid: userId,
          email: data.email,
          displayName: data.displayName || data.username,
          accountStatus: data.accountStatus || "active",
          createdAt: data.created_at?.toDate?.()?.toISOString() || data.created_at,
          lastActive: data.last_active_at?.toDate?.()?.toISOString() || data.last_active_at,
          currentlyBanned: false // Will be updated below
        };
        
        userMap.set(userId, userInfo);
        matchingUsers.push(userInfo);
      }
    });

    logger.info(`Found ${matchingUsers.length} matching users after filtering`);

    // Limit results to requested amount
    const limitedUsers = matchingUsers.slice(0, limit);
    
    // For each matching user, check if they're banned
    if (limitedUsers.length > 0) {
      const userIds = limitedUsers.map(u => u.uid);
      
      // Query ban status in batches if needed (Firestore 'in' query limit is 10)
      const bannedUserIds = new Set();
      
      for (let i = 0; i < userIds.length; i += 10) {
        const batch = userIds.slice(i, i + 10);
        const banSnapshot = await db.collection("user_bans")
          .where("userId", "in", batch)
          .where("status", "==", "active")
          .get();
        
        banSnapshot.docs.forEach(doc => {
          bannedUserIds.add(doc.data().userId);
        });
      }
      
      // Update ban status for all users
      limitedUsers.forEach(user => {
        user.currentlyBanned = bannedUserIds.has(user.uid);
      });
      
      logger.info(`Updated ban status for ${limitedUsers.length} users. ${bannedUserIds.size} users are currently banned.`);
    }

    logger.info(`Admin user search completed successfully. Returning ${limitedUsers.length} results.`);

    return {
      success: true,
      users: limitedUsers,
      searchQuery: searchQuery,
      totalMatches: matchingUsers.length
    };

  } catch (error) {
    logger.error(`Error searching users with query "${searchQuery}":`, error);
    throw new Error(`Failed to search users: ${error.message}`);
  }
});

// ================================
// ADMIN CLAIM MANAGEMENT FUNCTIONS
// ================================

/**
 * Admin-only Cloud Function to set admin claims for a user
 * This function allows setting custom admin claims for user authentication
 */
exports.setAdminClaim = onCall(async (request) => {
  // For initial setup, allow if no admins exist yet
  const adminQuery = await db.collection("admin_users").limit(1).get();
  const hasExistingAdmins = !adminQuery.empty;
  
  // If admins exist, verify the caller is an admin
  if (hasExistingAdmins && (!request.auth || !request.auth.token.admin)) {
    throw new Error("Only admin users can set admin claims");
  }
  
  const { targetUserId, isAdmin = true } = request.data;
  
  if (!targetUserId) {
    throw new Error("targetUserId is required");
  }
  
  try {
    // Get the user record
    const userRecord = await auth.getUser(targetUserId);
    
    if (!userRecord) {
      throw new Error(`User not found: ${targetUserId}`);
    }
    
    // Set admin custom claim
    await auth.setCustomUserClaims(targetUserId, {
      ...userRecord.customClaims,
      admin: isAdmin,
      adminSetAt: new Date().toISOString(),
      adminSetBy: request.auth?.uid || "system"
    });
    
    // Record this admin in the admin_users collection
    if (isAdmin) {
      await db.collection("admin_users").doc(targetUserId).set({
        userId: targetUserId,
        email: userRecord.email,
        displayName: userRecord.displayName,
        grantedAt: new Date(),
        grantedBy: request.auth?.uid || "system",
        status: "active"
      });
    } else {
      // Remove from admin_users if revoking admin
      await db.collection("admin_users").doc(targetUserId).delete();
    }
    
    // Log the action
    await db.collection("admin_actions").add({
      actionType: isAdmin ? "GRANT_ADMIN" : "REVOKE_ADMIN",
      performedBy: request.auth?.uid || "system",
      targetUserId: targetUserId,
      timestamp: new Date(),
      outcome: "success"
    });
    
    logger.info(`Admin claim ${isAdmin ? 'granted to' : 'revoked from'} user: ${targetUserId}`);
    
    // Verify the claims were set
    const updatedUser = await auth.getUser(targetUserId);
    
    return {
      success: true,
      userId: targetUserId,
      email: userRecord.email,
      adminStatus: isAdmin,
      customClaims: updatedUser.customClaims,
      message: `Admin claim ${isAdmin ? 'granted' : 'revoked'} successfully`
    };
    
  } catch (error) {
    logger.error(`Error setting admin claim for user ${targetUserId}:`, error);
    
    // Log failed attempt
    await db.collection("admin_actions").add({
      actionType: isAdmin ? "GRANT_ADMIN" : "REVOKE_ADMIN",
      performedBy: request.auth?.uid || "system",
      targetUserId: targetUserId,
      error: error.message,
      timestamp: new Date(),
      outcome: "failed"
    });
    
    throw new Error(`Failed to set admin claim: ${error.message}`);
  }
});

// ================================
// SUPPORT TICKET EMAIL FUNCTIONS
// ================================

/**
 * Cloud Function to send support ticket emails
 * Called when support tickets are created or updated
 */
exports.sendSupportTicketEmail = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("Authentication required");
  }

  const {ticketId, subject, description, category, deviceInfo, userEmail, userName} = request.data;
  
  if (!ticketId || !subject || !description || !category) {
    throw new Error("Missing required ticket parameters");
  }

  try {
    // Use Nodemailer to send email
    const nodemailer = require('nodemailer');
    
    // Configure Gmail transporter
    const transporter = nodemailer.createTransporter({
      service: 'gmail',
      auth: {
        user: 'valijianu98@gmail.com',
        pass: process.env.GMAIL_APP_PASSWORD // Set this in Firebase Functions config
      }
    });

    const emailBody = `
New Support Ticket Received

Ticket ID: ${ticketId}
Category: ${category}
Subject: ${subject}

User Information:
- Email: ${userEmail || 'Not provided'}
- Name: ${userName || 'Anonymous'}

Description:
${description}

Device Information:
${deviceInfo || 'Not provided'}

Submitted at: ${new Date().toISOString()}
    `;

    const mailOptions = {
      from: 'valijianu98@gmail.com',
      to: 'valijianu98@gmail.com',
      subject: `Liftrix Support: ${category} - ${subject}`,
      text: emailBody,
      replyTo: userEmail || 'valijianu98@gmail.com'
    };

    const result = await transporter.sendMail(mailOptions);
    
    logger.info(`Support ticket email sent successfully for ticket: ${ticketId}`);
    
    return {
      success: true,
      ticketId: ticketId,
      messageId: result.messageId
    };

  } catch (error) {
    logger.error(`Error sending support ticket email: ${error.message}`, error);
    throw new Error(`Failed to send support ticket email: ${error.message}`);
  }
});

/**
 * Triggered function when support tickets are created in Firestore
 * Automatically sends email notifications
 */
exports.onSupportTicketCreated = onDocumentWritten(
    "support_tickets/{ticketId}",
    async (event) => {
      const ticketId = event.params.ticketId;
      const ticketData = event.data?.after?.data();

      if (!ticketData) {
        logger.info(`Support ticket deleted: ${ticketId}`);
        return null;
      }

      // Only process new tickets (not updates)
      if (ticketData.email_sent) {
        logger.info(`Email already sent for ticket: ${ticketId}`);
        return null;
      }

      try {
        // Get user information
        const userDoc = await db.collection("users").doc(ticketData.user_id).get();
        const userData = userDoc.exists ? userDoc.data() : {};

        // Send email notification
        await exports.sendSupportTicketEmail.run({
          auth: { uid: ticketData.user_id },
          data: {
            ticketId: ticketId,
            subject: ticketData.subject,
            description: ticketData.description,
            category: ticketData.category,
            deviceInfo: ticketData.device_info,
            userEmail: userData.email,
            userName: userData.display_name || userData.username
          }
        });

        // Mark ticket as email sent
        await event.data.after.ref.update({
          email_sent: true,
          email_sent_at: new Date()
        });

        logger.info(`Support ticket email workflow completed for ticket: ${ticketId}`);
        return null;

      } catch (error) {
        logger.error(`Error in support ticket email workflow: ${ticketId}`, error);
        // Don't throw error to avoid retries - log and continue
        return null;
      }
    });
