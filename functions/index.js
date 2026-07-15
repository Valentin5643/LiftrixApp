

const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {getFirestore, FieldValue} = require("firebase-admin/firestore");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {onDocumentDeleted} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {HttpsError, onCall} = require("firebase-functions/v2/https");
const {logger} = require("firebase-functions");

// Initialize Firebase Admin SDK
initializeApp();

const auth = getAuth();
const db = getFirestore();

const CALLABLE_OPTIONS = Object.freeze({enforceAppCheck: true});
const ONE_HOUR_MS = 60 * 60 * 1000;

function requireAuthenticated(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Authentication required");
  }
  return request.auth;
}

function requireAdmin(request) {
  const caller = requireAuthenticated(request);
  if (caller.token.admin !== true) {
    throw new HttpsError("permission-denied", "Admin privileges required");
  }
  return caller;
}

function requireString(value, field, maxLength) {
  if (typeof value !== "string" || value.trim().length === 0 ||
      value.length > maxLength) {
    throw new HttpsError(
        "invalid-argument",
        `${field} must be a non-empty string of at most ${maxLength} characters`,
    );
  }
  return value.trim();
}

function callableFailure(error, publicMessage) {
  if (error instanceof HttpsError) {
    return error;
  }
  return new HttpsError("internal", publicMessage);
}

async function enforceCallableRateLimit(
    userId,
    operation,
    maximumAttempts,
    windowMs = ONE_HOUR_MS,
) {
  const rateLimitRef = db.collection("callable_rate_limits")
      .doc(`${operation}_${encodeURIComponent(userId)}`);
  const now = Date.now();

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(rateLimitRef);
    const current = snapshot.exists ? snapshot.data() : {};
    const windowStartedAt = current.window_started_at || 0;
    const withinWindow = now - windowStartedAt < windowMs;
    const attempts = withinWindow ? current.attempts || 0 : 0;

    if (attempts >= maximumAttempts) {
      throw new HttpsError(
          "resource-exhausted",
          "Too many requests. Try again later.",
      );
    }

    transaction.set(rateLimitRef, {
      user_id: userId,
      operation,
      window_started_at: withinWindow ? windowStartedAt : now,
      attempts: attempts + 1,
      updated_at: FieldValue.serverTimestamp(),
    });
  });
}

const FOLLOW_COUNTER_EVENTS_COLLECTION = "follow_relationship_counter_events";
const SOCIAL_PROFILES_COLLECTION = "social_profiles";

async function updateFollowCounters(event, data, delta, eventType) {
  if (!data) {
    logger.warn("PROFILE_FOLLOW_COUNTER_SKIP reason=missing_data");
    return null;
  }

  const followerId = data.followerId;
  const followingId = data.followingId;
  const status = data.status;

  if (!followerId || !followingId) {
    logger.warn(
        "PROFILE_FOLLOW_COUNTER_SKIP reason=missing_fields",
        {followerId, followingId},
    );
    return null;
  }

  if (followerId === followingId) {
    logger.warn(
        "PROFILE_FOLLOW_COUNTER_SKIP reason=self_follow",
        {followerId},
    );
    return null;
  }

  if (status !== "ACCEPTED") {
    logger.info(
        "PROFILE_FOLLOW_COUNTER_SKIP reason=status_not_accepted",
        {status, followerId, followingId},
    );
    return null;
  }

  const eventId = event.id || `${eventType}_${data.id || "unknown"}`;
  const eventRef = db.collection(FOLLOW_COUNTER_EVENTS_COLLECTION).doc(eventId);
  const followerRef = db.collection(SOCIAL_PROFILES_COLLECTION).doc(followerId);
  const followingRef = db.collection(SOCIAL_PROFILES_COLLECTION).doc(followingId);

  await db.runTransaction(async (tx) => {
    const eventSnap = await tx.get(eventRef);
    if (eventSnap.exists) {
      logger.info("PROFILE_FOLLOW_COUNTER_SKIP reason=already_processed", {
        eventId,
        eventType,
      });
      return;
    }

    const followerSnap = await tx.get(followerRef);
    if (followerSnap.exists) {
      tx.update(followerRef, {
        followingCount: FieldValue.increment(delta),
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      logger.warn("PROFILE_FOLLOW_COUNTER_MISSING profile=follower", {
        followerId,
      });
    }

    const followingSnap = await tx.get(followingRef);
    if (followingSnap.exists) {
      tx.update(followingRef, {
        followerCount: FieldValue.increment(delta),
        updatedAt: FieldValue.serverTimestamp(),
      });
    } else {
      logger.warn("PROFILE_FOLLOW_COUNTER_MISSING profile=following", {
        followingId,
      });
    }

    tx.set(eventRef, {
      eventType,
      followerId,
      followingId,
      delta,
      processedAt: FieldValue.serverTimestamp(),
    });
  });

  logger.info("PROFILE_FOLLOW_COUNTER_APPLIED", {
    eventType,
    followerId,
    followingId,
    delta,
  });

  return null;
}

async function handleFollowRelationshipCreated(event) {
  const data = event.data ? event.data.data() : null;
  return updateFollowCounters(event, data, 1, "follow_create");
}

async function handleFollowRelationshipDeleted(event) {
  const data = event.data ? event.data.data() : null;
  return updateFollowCounters(event, data, -1, "follow_delete");
}

async function handleFollowRelationshipUpdated(event) {
  const before = event.data ? event.data.before.data() : null;
  const after = event.data ? event.data.after.data() : null;
  if (!before || !after) {
    logger.warn("PROFILE_FOLLOW_COUNTER_SKIP reason=missing_update_data");
    return null;
  }

  const beforeStatus = before.status;
  const afterStatus = after.status;
  if (beforeStatus === afterStatus) {
    return null;
  }

  if (beforeStatus !== "ACCEPTED" && afterStatus === "ACCEPTED") {
    return updateFollowCounters(event, after, 1, "follow_status_accepted");
  }

  if (beforeStatus === "ACCEPTED" && afterStatus !== "ACCEPTED") {
    return updateFollowCounters(event, after, -1, "follow_status_unaccepted");
  }

  return null;
}

exports.followRelationshipCreatedCounter =
    onDocumentCreated("follow_relationships/{id}", handleFollowRelationshipCreated);
exports.followRelationshipDeletedCounter =
    onDocumentDeleted("follow_relationships/{id}", handleFollowRelationshipDeleted);
exports.followRelationshipUpdatedCounter =
    onDocumentWritten("follow_relationships/{id}", handleFollowRelationshipUpdated);

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
  const entitlement = normalizeSubscriptionEntitlement(subscriptionData);
  const userRecord = await auth.getUser(userId);
  const preservedClaims = preserveNonSubscriptionClaims(
      userRecord.customClaims || {},
  );

  // Base claims
  const claims = {
    ...preservedClaims,
    premium: entitlement.premium,
    tier: entitlement.tier,
    status: entitlement.status,
    subscription_updated_at: new Date().toISOString(),
  };

  // Add feature-specific claims
  if (entitlement.premium) {
    const allFeatures = new Set(entitlement.features);
    // Add feature flags
    claims.features = Array.from(allFeatures);

    // Add specific feature flags for easy access in security rules
    allFeatures.forEach((feature) => {
      claims[`feature_${feature}`] = true;
    });

    // Add tier-specific flags
    if (entitlement.tier === "premium") {
      claims.premium_tier = true;
    } else if (entitlement.tier === "pro") {
      claims.pro_tier = true;
      claims.premium_tier = true; // Pro includes premium features
    }
  }

  // Add expiration info for time-based checks
  if (entitlement.expiresAt) {
    claims.expires_at = entitlement.expiresAt.toISOString();
  }

  return claims;
}

const SUBSCRIPTION_TIERS = Object.freeze(["free", "premium", "pro"]);
const SUBSCRIPTION_STATUSES = Object.freeze([
  "active",
  "trial",
  "expired",
  "cancelled",
  "paused",
  "pending",
]);
const NON_SUBSCRIPTION_CLAIM_KEYS = Object.freeze([
  "admin",
  "adminSetAt",
  "adminSetBy",
  "banned",
  "banReason",
  "bannedBy",
  "bannedAt",
  "banDuration",
  "severity",
  "unbannedBy",
  "unbannedAt",
  "unbanReason",
]);

function preserveNonSubscriptionClaims(existingClaims) {
  const preservedClaims = {};
  NON_SUBSCRIPTION_CLAIM_KEYS.forEach((claimKey) => {
    if (Object.prototype.hasOwnProperty.call(existingClaims, claimKey)) {
      preservedClaims[claimKey] = existingClaims[claimKey];
    }
  });
  return preservedClaims;
}

function normalizeSubscriptionEntitlement(subscriptionData) {
  const rawTier = subscriptionData.tier;
  const rawStatus = subscriptionData.status;
  const rawFeatures = subscriptionData.features;
  const rawExpiresAt = subscriptionData.expires_at;
  const tierRecognized = SUBSCRIPTION_TIERS.includes(rawTier);
  const statusRecognized = SUBSCRIPTION_STATUSES.includes(rawStatus);
  const featuresValid = Array.isArray(rawFeatures) &&
      rawFeatures.every((feature) => typeof feature === "string");
  const expiresAtValid = rawExpiresAt == null ||
      typeof rawExpiresAt.toDate === "function";
  const expiresAt = expiresAtValid && rawExpiresAt ? rawExpiresAt.toDate() : null;
  const isExpired = expiresAt != null && expiresAt <= new Date();
  const isActive = rawStatus === "active" || rawStatus === "trial";
  const wellFormed = tierRecognized && statusRecognized && featuresValid &&
      expiresAtValid;
  const premium = wellFormed && rawTier !== "free" && isActive && !isExpired;
  const effectiveTier = premium ? rawTier : "free";
  const effectiveStatus = wellFormed ?
      (isExpired ? "expired" : rawStatus) : "expired";

  if (!wellFormed || (rawTier !== "free" && !premium)) {
    logger.warn("SUBSCRIPTION_ENTITLEMENT_FAIL_CLOSED", {
      tier: rawTier,
      status: rawStatus,
      featuresValid,
      expiresAtValid,
      isExpired,
    });
  }

  return {
    tier: effectiveTier,
    status: effectiveStatus,
    premium,
    features: premium ? getTierFeatures(effectiveTier) : [],
    expiresAt,
  };
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
    const entitlement = normalizeSubscriptionEntitlement(subscriptionData);

    const updateData = {
      subscription_tier: entitlement.tier,
      subscription_status: entitlement.status,
      premium_features_enabled: entitlement.premium,
      profile_version: FieldValue.increment(1),
      updated_at: new Date(),
    };

    if (entitlement.expiresAt) {
      updateData.subscription_expires_at = entitlement.expiresAt;
    }

    await db.collection("users").doc(userId).update(updateData);

    logger.info(`User profile updated for user: ${userId}`, {
      userId,
      tier: entitlement.tier,
      status: entitlement.status,
      premiumEnabled: entitlement.premium,
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
exports.updateUserClaimsManually = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId", 128);

  try {
    // Get the user's active subscription
    const subscriptionQuery = await db.collection("subscriptions")
        .where("user_id", "==", userId)
        .where("status", "in", ["active", "trial"])
        .orderBy("created_at", "desc")
        .limit(1)
        .get();

    if (subscriptionQuery.empty) {
      throw new HttpsError("not-found", "No active subscription found");
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
    throw callableFailure(error, "Failed to update user claims");
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
exports.regenerateUserFeed = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAuthenticated(request);
  const targetUserId = request.data?.userId ?
      requireString(request.data.userId, "userId", 128) : caller.uid;

  if (targetUserId !== caller.uid && caller.token.admin !== true) {
    throw new HttpsError(
        "permission-denied",
        "Feed regeneration is limited to the caller",
    );
  }

  await enforceCallableRateLimit(caller.uid, "regenerate_feed", 5);

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
    throw callableFailure(error, "Failed to regenerate feed");
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
let messagingClient;

function getMessagingClient() {
  if (!messagingClient) {
    messagingClient = getMessaging();
  }
  return messagingClient;
}

/**
 * Cloud Function to send immediate notifications
 * Called when high-priority events occur (PRs, follow requests)
 */
exports.sendImmediateNotification = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAuthenticated(request);
  const targetUserId = requireString(
      request.data?.targetUserId,
      "targetUserId",
      128,
  );
  const type = requireString(request.data?.type, "type", 40);
  await enforceCallableRateLimit(caller.uid, "immediate_notification", 20);

  try {
    const notification = await resolveCallableNotification(
        caller.uid,
        targetUserId,
        type,
        request.data || {},
    );
    const delivered = await sendImmediateNotificationInternal(
        targetUserId,
        type,
        notification.title,
        notification.body,
        notification.data,
    );
    return {
      success: delivered,
      reason: delivered ? "Delivered or queued" : "Not deliverable",
    };
  } catch (error) {
    logger.error("Error sending immediate notification", error);
    throw callableFailure(error, "Failed to send notification");
  }
});

async function resolveCallableNotification(
    callerUserId,
    targetUserId,
    type,
    requestData,
) {
  if (callerUserId === targetUserId && type === "WORKOUT_REMINDER") {
    return {
      title: requireString(requestData.title, "title", 80),
      body: requireString(requestData.body, "body", 240),
      data: {fromUserId: callerUserId},
    };
  }

  const senderName = await getPublicDisplayName(callerUserId);
  if (type === "FOLLOW_REQUEST") {
    const relationshipId = requireString(
        requestData.data?.relationshipId,
        "relationshipId",
        256,
    );
    const relationship = await db.collection("follow_relationships")
        .doc(relationshipId).get();
    const relationshipData = relationship.exists ? relationship.data() : {};
    const followerId = relationshipData.followerId ||
        relationshipData.follower_user_id;
    const followingId = relationshipData.followingId ||
        relationshipData.following_user_id;

    if (followerId !== callerUserId || followingId !== targetUserId ||
        relationshipData.status !== "PENDING") {
      throw new HttpsError(
          "permission-denied",
          "Notification relationship is not valid",
      );
    }

    return {
      title: "New Follow Request",
      body: `${senderName} wants to follow you`,
      data: {
        fromUserId: callerUserId,
        fromUserName: senderName,
        relationshipId,
      },
    };
  }

  if (type === "GYM_BUDDY_PR") {
    const postId = requireString(requestData.data?.postId, "postId", 256);
    const post = await db.collection("workout_posts").doc(postId).get();
    const postData = post.exists ? post.data() : {};
    const postOwnerId = postData.userId || postData.user_id;
    const hasMutualRelationship =
        await hasAcceptedRelationship(callerUserId, targetUserId) &&
        await hasAcceptedRelationship(targetUserId, callerUserId);

    if (postOwnerId !== callerUserId || !hasMutualRelationship ||
        !(postData.prs_count > 0 || postData.prsCount > 0)) {
      throw new HttpsError(
          "permission-denied",
          "Notification event is not valid",
      );
    }

    return {
      title: `${senderName} hit a PR!`,
      body: "Your gym buddy reached a new personal record.",
      data: {fromUserId: callerUserId, fromUserName: senderName, postId},
    };
  }

  throw new HttpsError(
      "invalid-argument",
      "Unsupported callable notification type",
  );
}

async function getPublicDisplayName(userId) {
  const profile = await db.collection(SOCIAL_PROFILES_COLLECTION)
      .doc(userId).get();
  if (!profile.exists) {
    return "Someone";
  }
  const profileData = profile.data();
  const displayName = profileData.displayName ||
      profileData.display_name || profileData.username || "Someone";
  return typeof displayName === "string" ? displayName.slice(0, 60) : "Someone";
}

async function hasAcceptedRelationship(followerId, followingId) {
  const canonicalId = `${followerId}_${followingId}`;
  const canonical = await db.collection("follow_relationships")
      .doc(canonicalId).get();
  if (canonical.exists) {
    const data = canonical.data();
    const storedFollower = data.followerId || data.follower_user_id;
    const storedFollowing = data.followingId || data.following_user_id;
    return storedFollower === followerId && storedFollowing === followingId &&
        data.status === "ACCEPTED";
  }

  const modern = await db.collection("follow_relationships")
      .where("followerId", "==", followerId)
      .where("followingId", "==", followingId)
      .where("status", "==", "ACCEPTED")
      .limit(1)
      .get();
  if (!modern.empty) {
    return true;
  }

  const legacy = await db.collection("follow_relationships")
      .where("follower_user_id", "==", followerId)
      .where("following_user_id", "==", followingId)
      .where("status", "==", "ACCEPTED")
      .limit(1)
      .get();
  return !legacy.empty;
}

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
  const fromUserId = data?.fromUserId;
  if (fromUserId && await isUserMuted(targetUserId, fromUserId, type)) {
    return false;
  }

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

  const response = await getMessagingClient().sendEachForMulticast(message);
  
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

  await getMessagingClient().sendEachForMulticast(message);
  
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
exports.banUser = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId", 128);
  const reason = requireString(request.data?.reason, "reason", 1000);
  const banDuration = request.data?.banDuration == null ? null :
      requireString(request.data.banDuration, "banDuration", 80);
  const severity = request.data?.severity || "moderate";
  if (!["low", "moderate", "high", "critical"].includes(severity)) {
    throw new HttpsError("invalid-argument", "Invalid ban severity");
  }
  if (userId === caller.uid) {
    throw new HttpsError("failed-precondition", "Admins cannot ban themselves");
  }

  try {
    logger.info(`Admin ${request.auth.uid} attempting to ban user: ${userId}`);

    // Get user record to check if user exists
    const userRecord = await auth.getUser(userId);

    if (!userRecord) {
      throw new HttpsError("not-found", "User not found");
    }

    const bannedAt = new Date();

    // Disable Firebase Auth account and update custom claims separately.
    // Firebase Auth updateUser does not accept customClaims.
    await auth.updateUser(userId, {
      disabled: true,
    });

    await auth.setCustomUserClaims(userId, {
      ...userRecord.customClaims,
      banned: true,
      banReason: reason,
      bannedBy: request.auth.uid,
      bannedAt: bannedAt.toISOString(),
      banDuration: banDuration || null,
      severity: severity || "moderate",
    });

    // Create ban record in Firestore
    const banRecord = {
      userId: userId,
      bannedBy: request.auth.uid,
      reason: reason,
      severity: severity || "moderate",
      banDuration: banDuration || null, // null means permanent
      bannedAt: bannedAt,
      status: "active",
      userEmail: userRecord.email || null,
      userDisplayName: userRecord.displayName || null,
      metadata: {
        userCreatedAt: userRecord.metadata.creationTime,
        lastSignIn: userRecord.metadata.lastSignInTime,
        providerData: userRecord.providerData.map((p) => p.providerId),
      },
    };

    const banDoc = await db.collection("user_bans").add(banRecord);

    // Update user profile to reflect ban status
    try {
      await db.collection("users").doc(userId).update({
        accountStatus: "banned",
        bannedAt: bannedAt,
        bannedBy: request.auth.uid,
        banReason: reason,
        banSeverity: severity || "moderate",
        profileVersion: FieldValue.increment(1),
        updated_at: bannedAt,
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
        bannedAt: bannedAt,
        status: "active",
      });

      // Remove from public discoverable profiles
      const socialProfileRef = db.collection("social_profiles").doc(userId);
      await socialProfileRef.update({
        isDiscoverable: false,
        profileVisibility: "PRIVATE",
        accountStatus: "banned",
        updatedAt: bannedAt,
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
        userEmail: userRecord.email,
        userDisplayName: userRecord.displayName,
      },
      timestamp: bannedAt,
      outcome: "success",
    });

    logger.info(`User ${userId} successfully banned by admin ${request.auth.uid}. Reason: ${reason}`);

    return {
      success: true,
      userId: userId,
      banId: banDoc.id,
      bannedAt: bannedAt.toISOString(),
      message: `User ${userId} has been banned successfully`,
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
        error: error.message,
      },
      timestamp: new Date(),
      outcome: "failed",
    });

    throw callableFailure(error, "Failed to ban user");
  }
});

/**
 * Admin-only Cloud Function to unban/re-enable users
 */
exports.unbanUser = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId", 128);
  const reason = request.data?.reason == null ? "Appeal approved" :
      requireString(request.data.reason, "reason", 1000);

  try {
    logger.info(`Admin ${request.auth.uid} attempting to unban user: ${userId}`);

    const unbannedAt = new Date();
    const userRecord = await auth.getUser(userId);

    // Re-enable Firebase Auth account and update custom claims separately.
    // Preserve unrelated claims such as admin/premium.
    await auth.updateUser(userId, {
      disabled: false,
    });

    await auth.setCustomUserClaims(userId, {
      ...userRecord.customClaims,
      banned: false,
      banReason: null,
      bannedBy: null,
      bannedAt: null,
      banDuration: null,
      severity: null,
      unbannedBy: request.auth.uid,
      unbannedAt: unbannedAt.toISOString(),
      unbanReason: reason || "Appeal approved",
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
        unbannedAt: unbannedAt,
        unbanReason: reason || "Appeal approved",
      });
    }

    // Update user profile
    try {
      await db.collection("users").doc(userId).update({
        accountStatus: "active",
        bannedAt: FieldValue.delete(),
        bannedBy: FieldValue.delete(),
        banReason: FieldValue.delete(),
        banSeverity: FieldValue.delete(),
        unbannedAt: unbannedAt,
        unbannedBy: request.auth.uid,
        profileVersion: FieldValue.increment(1),
        updated_at: unbannedAt,
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
        updatedAt: unbannedAt,
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
        reason: reason || "Appeal approved",
      },
      timestamp: unbannedAt,
      outcome: "success",
    });

    logger.info(`User ${userId} successfully unbanned by admin ${request.auth.uid}`);

    return {
      success: true,
      userId: userId,
      unbannedAt: unbannedAt.toISOString(),
      message: `User ${userId} has been unbanned successfully`,
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
        error: error.message,
      },
      timestamp: new Date(),
      outcome: "failed",
    });

    throw callableFailure(error, "Failed to unban user");
  }
});

/**
 * Admin-only Cloud Function to get user ban history and details
 */
exports.getUserBanInfo = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAdmin(request);
  const userId = requireString(request.data?.userId, "userId", 128);

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
    throw callableFailure(error, "Failed to get user ban information");
  }
});

/**
 * Admin-only Cloud Function to list all banned users
 */
exports.listBannedUsers = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAdmin(request);
  const {limit = 50, offset = 0, severity = null} = request.data || {};
  if (!Number.isInteger(limit) || limit < 1 || limit > 100 ||
      !Number.isInteger(offset) || offset < 0 || offset > 10000) {
    throw new HttpsError("invalid-argument", "Invalid pagination values");
  }
  if (severity != null &&
      !["low", "moderate", "high", "critical"].includes(severity)) {
    throw new HttpsError("invalid-argument", "Invalid ban severity");
  }

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
    throw callableFailure(error, "Failed to list banned users");
  }
});

/**
 * Admin-only Cloud Function to list recent ban management audit logs
 */
exports.getAdminLogs = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAdmin(request);
  const {limit = 100, actionType = null} = request.data || {};
  if (!Number.isInteger(limit) || limit < 1 || limit > 200) {
    throw new HttpsError("invalid-argument", "Invalid log limit");
  }
  if (actionType != null &&
      !["BAN_USER", "UNBAN_USER"].includes(actionType)) {
    throw new HttpsError("invalid-argument", "Invalid action type");
  }

  try {
    const query = db.collection("admin_actions")
      .orderBy("timestamp", "desc")
      .limit(actionType ? limit : Math.max(limit * 3, limit));

    const snapshot = await query.get();

    return snapshot.docs
      .filter((doc) => {
        const logActionType = doc.data().actionType;
        if (actionType) {
          return logActionType === actionType;
        }
        return logActionType === "BAN_USER" || logActionType === "UNBAN_USER";
      })
      .slice(0, limit)
      .map((doc) => {
        const data = doc.data();
        const details = data.details || {};
        const isUnban = data.actionType === "UNBAN_USER";
        const timestamp = data.timestamp?.toDate?.()?.toISOString() ||
          data.timestamp;

        return {
          id: doc.id,
          userId: data.targetUserId || "",
          bannedBy: data.performedBy || "",
          reason: details.reason || details.error || "No reason provided",
          severity: details.severity || "moderate",
          banDuration: details.banDuration || null,
          bannedAt: timestamp,
          unbannedAt: isUnban ? timestamp : null,
          unbannedBy: isUnban ? data.performedBy || null : null,
          unbanReason: isUnban ? details.reason || null : null,
          status: isUnban ? "inactive" : "active",
          userEmail: details.userEmail || null,
          userDisplayName: details.userDisplayName || null,
          metadata: {
            outcome: data.outcome || null,
            actionType: data.actionType || null,
          },
        };
      });

  } catch (error) {
    logger.error("Error getting admin logs:", error);
    throw callableFailure(error, "Failed to get admin logs");
  }
});

/**
 * Admin-only Cloud Function to search users by email or display name
 * Fixed implementation with robust search similar to social user search
 */
exports.searchUsers = onCall(CALLABLE_OPTIONS, async (request) => {
  requireAdmin(request);
  const searchQuery = requireString(request.data?.query, "query", 100);
  const limit = request.data?.limit || 50;
  if (searchQuery.length < 3 || !Number.isInteger(limit) ||
      limit < 1 || limit > 100) {
    throw new HttpsError("invalid-argument", "Invalid search parameters");
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
    throw callableFailure(error, "Failed to search users");
  }
});

// ================================
// ADMIN CLAIM MANAGEMENT FUNCTIONS
// ================================

/**
 * Admin-only Cloud Function to set admin claims for a user
 * This function allows setting custom admin claims for user authentication
 */
exports.setAdminClaim = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAdmin(request);
  const targetUserId = requireString(
      request.data?.targetUserId,
      "targetUserId",
      128,
  );
  const isAdmin = request.data?.isAdmin == null ? true : request.data.isAdmin;
  if (typeof isAdmin !== "boolean") {
    throw new HttpsError("invalid-argument", "isAdmin must be a boolean");
  }
  if (targetUserId === caller.uid && !isAdmin) {
    throw new HttpsError(
        "failed-precondition",
        "Admins cannot revoke their own admin claim through this callable",
    );
  }
  
  try {
    // Get the user record
    const userRecord = await auth.getUser(targetUserId);
    
    if (!userRecord) {
      throw new HttpsError("not-found", "User not found");
    }
    
    // Set admin custom claim
    await auth.setCustomUserClaims(targetUserId, {
      ...userRecord.customClaims,
      admin: isAdmin,
      adminSetAt: new Date().toISOString(),
      adminSetBy: caller.uid,
    });
    
    // Record this admin in the admin_users collection
    if (isAdmin) {
      await db.collection("admin_users").doc(targetUserId).set({
        userId: targetUserId,
        email: userRecord.email,
        displayName: userRecord.displayName,
        grantedAt: new Date(),
        grantedBy: caller.uid,
        status: "active"
      });
    } else {
      // Remove from admin_users if revoking admin
      await db.collection("admin_users").doc(targetUserId).delete();
    }
    
    // Log the action
    await db.collection("admin_actions").add({
      actionType: isAdmin ? "GRANT_ADMIN" : "REVOKE_ADMIN",
      performedBy: caller.uid,
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
      performedBy: caller.uid,
      targetUserId: targetUserId,
      error: error.message,
      timestamp: new Date(),
      outcome: "failed"
    });
    
    throw callableFailure(error, "Failed to set admin claim");
  }
});

// ================================
// SUPPORT TICKET EMAIL FUNCTIONS
// ================================

/**
 * Cloud Function to send support ticket emails
 * Called when support tickets are created or updated
 */
exports.sendSupportTicketEmail = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAuthenticated(request);
  const ticketId = requireString(request.data?.ticketId, "ticketId", 256);

  try {
    const ticketRef = db.collection("support_tickets").doc(ticketId);
    const ticketSnapshot = await ticketRef.get();
    if (!ticketSnapshot.exists ||
        ticketSnapshot.data().user_id !== caller.uid) {
      throw new HttpsError("not-found", "Support ticket not found");
    }
    if (ticketSnapshot.data().email_sent === true) {
      return {success: true, ticketId, alreadySent: true};
    }

    await enforceCallableRateLimit(caller.uid, "support_email", 3);
    const userRecord = await auth.getUser(caller.uid);
    const result = await sendSupportTicketEmailInternal(
        ticketId,
        ticketSnapshot.data(),
        userRecord,
    );
    await ticketRef.update({
      email_sent: true,
      email_sent_at: FieldValue.serverTimestamp(),
    });
    return {success: true, ticketId, messageId: result.messageId};
  } catch (error) {
    logger.error(`Error sending support ticket email: ${ticketId}`, error);
    throw callableFailure(error, "Failed to send support ticket email");
  }
});

async function sendSupportTicketEmailInternal(ticketId, ticketData, userRecord) {
  const subject = requireString(ticketData.subject, "subject", 200);
  const description = requireString(
      ticketData.description,
      "description",
      5000,
  );
  const category = requireString(ticketData.category, "category", 80);
  const deviceInfo = ticketData.device_info == null ? "Not provided" :
      requireString(ticketData.device_info, "deviceInfo", 2000);
  const userEmail = userRecord.email || "Not provided";
  const userName = userRecord.displayName || "Anonymous";
  const nodemailer = require("nodemailer");
  const supportAddress = "valijianu98@gmail.com";
  const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
      user: supportAddress,
      pass: process.env.GMAIL_APP_PASSWORD,
    },
  });
  const emailBody = [
    "New Support Ticket Received",
    `Ticket ID: ${ticketId}`,
    `Category: ${category}`,
    `Subject: ${subject}`,
    `User Email: ${userEmail}`,
    `User Name: ${userName}`,
    `Description: ${description}`,
    `Device Information: ${deviceInfo}`,
    `Submitted at: ${new Date().toISOString()}`,
  ].join("\n\n");

  return transporter.sendMail({
    from: supportAddress,
    to: supportAddress,
    subject: `Liftrix Support: ${category} - ${subject}`,
    text: emailBody,
    replyTo: userRecord.email || supportAddress,
  });
}

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
        await enforceCallableRateLimit(ticketData.user_id, "support_email", 3);
        const userRecord = await auth.getUser(ticketData.user_id);
        await sendSupportTicketEmailInternal(ticketId, ticketData, userRecord);

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

// Auth trigger removed - using document-based trigger pattern below instead
// (firebase-functions v6 has compatibility issues with auth triggers)

exports.cleanupDeletedUser = onDocumentWritten(
  "user_cleanup_requests/{uid}",
  async (event) => {
    const uid = event.params.uid;
    const cleanupData = event.data?.after?.data();
    
    if (!cleanupData) {
      logger.info(`🧹 CLEANUP: Cleanup request deleted for user: ${uid}`);
      return;
    }
    
    logger.info(`🧹 CLEANUP: Starting cleanup for user: ${uid} (reason: ${cleanupData.reason || 'unknown'})`);
  
  try {
    const batch = db.batch();
    let operationCount = 0;
    
    // 1. Delete main user profile document
    logger.info(`🧹 CLEANUP: Deleting user profile for ${uid}`);
    const userDocRef = db.collection("users").doc(uid);
    batch.delete(userDocRef);
    operationCount++;
    
    // 2. Delete social profile document
    logger.info(`🧹 CLEANUP: Deleting social profile for ${uid}`);
    const socialDocRef = db.collection("social_profiles").doc(uid);
    batch.delete(socialDocRef);
    operationCount++;
    
    // 3. Delete user's workouts subcollection (limit to 500 per batch)
    logger.info(`🧹 CLEANUP: Deleting workouts for ${uid}`);
    const workoutsQuery = await db.collection("users").doc(uid)
      .collection("workouts").limit(500).get();
    
    workoutsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (workoutsQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${workoutsQuery.docs.length} workouts to delete for ${uid}`);
    }
    
    // 4. Delete user's templates subcollection
    logger.info(`🧹 CLEANUP: Deleting templates for ${uid}`);
    const templatesQuery = await db.collection("users").doc(uid)
      .collection("templates").limit(500).get();
    
    templatesQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (templatesQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${templatesQuery.docs.length} templates to delete for ${uid}`);
    }
    
    // 5. Delete user's achievements subcollection
    console.log(`🧹 CLEANUP: Deleting achievements for ${uid}`);
    const achievementsQuery = await db.collection("users").doc(uid)
      .collection("achievements").limit(500).get();
    
    achievementsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (achievementsQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${achievementsQuery.docs.length} achievements to delete for ${uid}`);
    }
    
    // 6. Clean up follow relationships where user is follower
    console.log(`🧹 CLEANUP: Deleting follow relationships (as follower) for ${uid}`);
    const followingQuery = await db.collection("follow_relationships")
      .where("follower_user_id", "==", uid).limit(500).get();
    
    followingQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (followingQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${followingQuery.docs.length} following relationships to delete for ${uid}`);
    }
    
    // 7. Clean up follow relationships where user is followed
    console.log(`🧹 CLEANUP: Deleting follow relationships (as followed) for ${uid}`);
    const followersQuery = await db.collection("follow_relationships")
      .where("following_user_id", "==", uid).limit(500).get();
    
    followersQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (followersQuery.docs.length > 0) {
      console.log(`🧹 CLEANUP: Found ${followersQuery.docs.length} follower relationships to delete for ${uid}`);
    }
    
    // 8. Clean up workout posts by the user
    console.log(`🧹 CLEANUP: Deleting workout posts for ${uid}`);
    const workoutPostsQuery = await db.collection("workout_posts")
      .where("user_id", "==", uid).limit(500).get();
    
    workoutPostsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (workoutPostsQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${workoutPostsQuery.docs.length} workout posts to delete for ${uid}`);
    }
    
    // 9. Clean up support tickets
    logger.info(`🧹 CLEANUP: Deleting support tickets for ${uid}`);
    const ticketsQuery = await db.collection("support_tickets")
      .where("user_id", "==", uid).limit(100).get();
    
    ticketsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (ticketsQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${ticketsQuery.docs.length} support tickets to delete for ${uid}`);
    }
    
    // 10. Clean up FCM tokens
    logger.info(`🧹 CLEANUP: Deleting FCM tokens for ${uid}`);
    const tokensQuery = await db.collection("fcm_tokens")
      .where("user_id", "==", uid).limit(100).get();
    
    tokensQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    if (tokensQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${tokensQuery.docs.length} FCM tokens to delete for ${uid}`);
    }
    
    // 11. Clean up notification preferences and history
    logger.info(`🧹 CLEANUP: Deleting notification data for ${uid}`);
    const notificationPrefsQuery = await db.collection("notification_preferences")
      .where("user_id", "==", uid).limit(100).get();
    
    notificationPrefsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });
    
    const notificationHistoryQuery = await db.collection("notification_history")
      .where("user_id", "==", uid).limit(100).get();

    notificationHistoryQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });

    // 12. Delete user settings
    logger.info(`🧹 CLEANUP: Deleting user settings for ${uid}`);
    const settingsDocRef = db.collection("user_settings").doc(uid);
    batch.delete(settingsDocRef);
    operationCount++;

    // 13. Delete subscriptions
    logger.info(`🧹 CLEANUP: Deleting subscriptions for ${uid}`);
    const subscriptionsQuery = await db.collection("subscriptions")
      .where("user_id", "==", uid).limit(100).get();

    subscriptionsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });

    if (subscriptionsQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${subscriptionsQuery.docs.length} subscriptions to delete for ${uid}`);
    }

    // 14. Delete custom exercises
    logger.info(`🧹 CLEANUP: Deleting custom exercises for ${uid}`);
    const customExercisesQuery = await db.collection("custom_exercises")
      .where("user_id", "==", uid).limit(500).get();

    customExercisesQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });

    if (customExercisesQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${customExercisesQuery.docs.length} custom exercises to delete for ${uid}`);
    }

    // 15. Delete analytics cache
    logger.info(`🧹 CLEANUP: Deleting analytics cache for ${uid}`);
    const analyticsCacheQuery = await db.collection("analytics_cache")
      .where("user_id", "==", uid).limit(500).get();

    analyticsCacheQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });

    if (analyticsCacheQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${analyticsCacheQuery.docs.length} analytics cache items to delete for ${uid}`);
    }

    // 16. Delete notifications
    logger.info(`🧹 CLEANUP: Deleting notifications for ${uid}`);
    const notificationsQuery = await db.collection("notifications")
      .where("user_id", "==", uid).limit(500).get();

    notificationsQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });

    if (notificationsQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${notificationsQuery.docs.length} notifications to delete for ${uid}`);
    }

    // 17. Delete gym buddy relationships
    logger.info(`🧹 CLEANUP: Deleting gym buddy relationships for ${uid}`);
    const gymBuddiesQuery = await db.collection("gym_buddies")
      .where("user_id", "==", uid).limit(100).get();

    gymBuddiesQuery.docs.forEach((doc) => {
      batch.delete(doc.ref);
      operationCount++;
    });

    if (gymBuddiesQuery.docs.length > 0) {
      logger.info(`🧹 CLEANUP: Found ${gymBuddiesQuery.docs.length} gym buddy relationships to delete for ${uid}`);
    }

    // Execute all deletions in a single batch
    logger.info(`🧹 CLEANUP: Executing batch delete of ${operationCount} operations for ${uid}`);
    await batch.commit();
    
    logger.info(`✅ CLEANUP SUCCESS: Cleaned up ${operationCount} documents/subcollections for user ${uid}`);
    
    // Log metrics for monitoring
    logger.info(`🧹 CLEANUP_METRICS | user_id=${uid} | operations=${operationCount} | status=success | timestamp=${Date.now()}`);
    
    // Clean up the cleanup request document itself
    try {
      await event.data.after.ref.delete();
      logger.info(`🧹 CLEANUP: Deleted cleanup request document for ${uid}`);
    } catch (deleteError) {
      logger.error(`⚠️ CLEANUP: Could not delete cleanup request for ${uid}:`, deleteError);
    }
    
  } catch (error) {
    logger.error(`❌ CLEANUP ERROR: Failed to clean up data for user ${uid}:`, error);
    logger.info(`🧹 CLEANUP_METRICS | user_id=${uid} | status=error | error="${error.message}" | timestamp=${Date.now()}`);
    
    // Don't throw the error - log and continue
  }
});


exports.bulkCleanupOrphanedData = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAdmin(request);
  logger.info("🧹 BULK_CLEANUP: Starting bulk cleanup of orphaned data");
  logger.info(`🧹 BULK_CLEANUP: Admin ${caller.uid} running cleanup`);
  
  try {
    const results = [];
    let totalCleaned = 0;
    
    // Get all user documents from Firestore
    logger.info("🧹 BULK_CLEANUP: Fetching all user documents from Firestore");
    const usersSnapshot = await db.collection("users").get();
    logger.info(`🧹 BULK_CLEANUP: Found ${usersSnapshot.docs.length} user documents in Firestore`);
    
    // Check each user document to see if the Firebase Auth user still exists
    for (const doc of usersSnapshot.docs) {
      const uid = doc.id;
      
      try {
        // Check if user exists in Firebase Auth
        await auth.getUser(uid);
        // User exists, not orphaned - skip
        logger.info(`🧹 BULK_CLEANUP: User ${uid} exists in Auth - skipping`);
        results.push({ uid, status: "active", action: "skipped" });
        
      } catch (authError) {
        if (authError.code === "auth/user-not-found") {
          // User is orphaned - clean up their data
          logger.info(`🧹 BULK_CLEANUP: User ${uid} not found in Auth - cleaning up orphaned data`);
          
          try {
            // Use the same cleanup logic as the automatic function
            const batch = db.batch();
            let operationCount = 0;
            
            // Delete user profile
            batch.delete(db.collection("users").doc(uid));
            operationCount++;
            
            // Delete social profile
            batch.delete(db.collection("social_profiles").doc(uid));
            operationCount++;
            
            // Delete subcollections (limited to prevent timeout)
            const collections = ["workouts", "templates", "achievements"];
            
            for (const collectionName of collections) {
              const subcollectionSnapshot = await db.collection("users").doc(uid)
                .collection(collectionName).limit(100).get(); // Reduced limit for bulk operation
              
              subcollectionSnapshot.docs.forEach(subDoc => {
                batch.delete(subDoc.ref);
                operationCount++;
              });
            }
            
            // Delete follow relationships
            const followingSnapshot = await db.collection("follow_relationships")
              .where("follower_user_id", "==", uid).limit(100).get();
            
            followingSnapshot.docs.forEach(followDoc => {
              batch.delete(followDoc.ref);
              operationCount++;
            });
            
            const followersSnapshot = await db.collection("follow_relationships")
              .where("following_user_id", "==", uid).limit(100).get();
            
            followersSnapshot.docs.forEach(followDoc => {
              batch.delete(followDoc.ref);
              operationCount++;
            });
            
            // Delete workout posts
            const postsSnapshot = await db.collection("workout_posts")
              .where("user_id", "==", uid).limit(100).get();
            
            postsSnapshot.docs.forEach(postDoc => {
              batch.delete(postDoc.ref);
              operationCount++;
            });
            
            // Execute batch delete
            await batch.commit();
            
            totalCleaned++;
            logger.info(`✅ BULK_CLEANUP: Successfully cleaned up ${operationCount} operations for orphaned user ${uid}`);
            results.push({ uid, status: "orphaned", action: "cleaned", operations: operationCount });
            
          } catch (cleanupError) {
            logger.error(`❌ BULK_CLEANUP: Error cleaning up user ${uid}:`, cleanupError);
            results.push({ uid, status: "orphaned", action: "error", error: cleanupError.message });
          }
          
        } else {
          // Other auth error - skip this user
          logger.error(`🧹 BULK_CLEANUP: Auth error for user ${uid}:`, authError);
          results.push({ uid, status: "auth_error", action: "skipped", error: authError.message });
        }
      }
    }
    
    logger.info(`✅ BULK_CLEANUP COMPLETE: Cleaned up ${totalCleaned} orphaned users out of ${usersSnapshot.docs.length} total users`);
    logger.info(`🧹 BULK_CLEANUP_METRICS | total_users=${usersSnapshot.docs.length} | cleaned=${totalCleaned} | timestamp=${Date.now()}`);
    
    return {
      success: true,
      totalUsers: usersSnapshot.docs.length,
      orphanedCleaned: totalCleaned,
      results: results
    };
    
  } catch (error) {
    logger.error("❌ BULK_CLEANUP FAILED:", error);
    throw callableFailure(error, "Bulk cleanup failed");
  }
});

/**
 * Scheduled Cloud Function to detect AND delete orphaned Firestore data
 * Runs daily at 2 AM UTC to clean up data from deleted Firebase Auth users
 *
 * This function:
 * 1. Scans Firestore for user profiles
 * 2. Checks if corresponding Firebase Auth account exists
 * 3. Automatically deletes orphaned profiles and related data
 */
exports.scheduledOrphanCleanup = onSchedule(
    {
      schedule: "0 2 * * *", // Daily at 2 AM UTC
      timeZone: "UTC",
    },
    async (event) => {
      logger.info("🧹 SCHEDULED_CLEANUP: Starting daily orphaned data cleanup");

      try {
        let orphanedCount = 0;
        let deletedCount = 0;
        const deletedProfiles = [];

        // Get all user documents (process in batches to avoid timeout)
        const usersSnapshot = await db.collection("users").limit(500).get();
        logger.info(`🧹 SCHEDULED_CLEANUP: Checking ${usersSnapshot.docs.length} user profiles`);

        for (const doc of usersSnapshot.docs) {
          const uid = doc.id;

          try {
            // Check if Firebase Auth user still exists
            await auth.getUser(uid);
            // User exists - not orphaned, skip

          } catch (authError) {
            if (authError.code === "auth/user-not-found") {
              orphanedCount++;
              logger.info(`🧹 SCHEDULED_CLEANUP: Found orphaned profile: ${uid}`);

              try {
                // Delete the orphaned user's data using batch operations
                const batch = db.batch();
                let operationCount = 0;

                // 1. Delete main user profile
                batch.delete(db.collection("users").doc(uid));
                operationCount++;

                // 2. Delete social profile
                batch.delete(db.collection("social_profiles").doc(uid));
                operationCount++;

                // 3. Delete user subcollections (workouts, templates, achievements)
                const subcollections = ["workouts", "templates", "achievements"];
                for (const collectionName of subcollections) {
                  const subcollectionSnapshot = await db.collection("users").doc(uid)
                    .collection(collectionName).limit(100).get();

                  subcollectionSnapshot.docs.forEach(subDoc => {
                    batch.delete(subDoc.ref);
                    operationCount++;
                  });
                }

                // 4. Delete follow relationships
                const followingSnapshot = await db.collection("follow_relationships")
                  .where("follower_user_id", "==", uid).limit(100).get();
                followingSnapshot.docs.forEach(followDoc => {
                  batch.delete(followDoc.ref);
                  operationCount++;
                });

                const followersSnapshot = await db.collection("follow_relationships")
                  .where("following_user_id", "==", uid).limit(100).get();
                followersSnapshot.docs.forEach(followDoc => {
                  batch.delete(followDoc.ref);
                  operationCount++;
                });

                // 5. Delete workout posts
                const postsSnapshot = await db.collection("workout_posts")
                  .where("user_id", "==", uid).limit(100).get();
                postsSnapshot.docs.forEach(postDoc => {
                  batch.delete(postDoc.ref);
                  operationCount++;
                });

                // 6. Delete FCM tokens
                const tokensSnapshot = await db.collection("fcm_tokens")
                  .where("user_id", "==", uid).limit(100).get();
                tokensSnapshot.docs.forEach(tokenDoc => {
                  batch.delete(tokenDoc.ref);
                  operationCount++;
                });

                // 7. Delete notification data
                const notifPrefsSnapshot = await db.collection("notification_preferences")
                  .where("user_id", "==", uid).limit(100).get();
                notifPrefsSnapshot.docs.forEach(prefDoc => {
                  batch.delete(prefDoc.ref);
                  operationCount++;
                });

                // Execute batch delete
                await batch.commit();

                deletedCount++;
                deletedProfiles.push(uid);
                logger.info(`✅ SCHEDULED_CLEANUP: Deleted ${operationCount} documents for orphaned user ${uid}`);

              } catch (deleteError) {
                logger.error(`❌ SCHEDULED_CLEANUP: Failed to delete orphaned user ${uid}:`, deleteError);
              }
            }
          }
        }

        logger.info(`✅ SCHEDULED_CLEANUP COMPLETE: Checked ${usersSnapshot.docs.length} profiles, found ${orphanedCount} orphaned, deleted ${deletedCount}`);
        logger.info(`🧹 CLEANUP_METRICS | checked=${usersSnapshot.docs.length} | orphaned=${orphanedCount} | deleted=${deletedCount} | timestamp=${Date.now()}`);

        // Alert if high number of orphaned profiles remaining
        if (orphanedCount > deletedCount + 10) {
          logger.error(`🚨 ALERT: More orphaned profiles exist - may need additional cleanup runs`);
        }

        return null;

      } catch (error) {
        logger.error("❌ SCHEDULED_CLEANUP ERROR:", error);
        throw error;
      }
    });

/**
 * DEPRECATED - Use scheduledOrphanCleanup instead
 * This function only detects orphaned data without deleting it
 */
exports.detectOrphanedData = onSchedule(
    {
      schedule: "0 3 * * *", // Daily at 3 AM UTC (1 hour after cleanup)
      timeZone: "UTC",
    },
    async (event) => {
      logger.info("📊 MONITORING: Starting daily orphaned data detection (deprecated - use scheduledOrphanCleanup)");

      try {
        let orphanedCount = 0;

        // Get sample of user documents (limit to prevent timeout)
        const usersSnapshot = await db.collection("users").limit(100).get();

        for (const doc of usersSnapshot.docs) {
          const uid = doc.id;

          try {
            await auth.getUser(uid);
            // User exists, not orphaned
          } catch (authError) {
            if (authError.code === "auth/user-not-found") {
              orphanedCount++;
            }
          }
        }

        logger.info(`📊 MONITORING: Found ${orphanedCount} orphaned profiles out of ${usersSnapshot.docs.length} checked`);

        // Alert if high number of orphaned profiles
        if (orphanedCount > 5) {
          logger.error(`🚨 ALERT: HIGH ORPHANED DATA COUNT: ${orphanedCount} profiles detected - scheduledOrphanCleanup should have cleaned these`);
        }

        logger.info(`📊 MONITORING_METRICS | orphaned_count=${orphanedCount} | total_checked=${usersSnapshot.docs.length} | timestamp=${Date.now()}`);

      } catch (error) {
        logger.error("📊 MONITORING ERROR:", error);
      }
    });

/**
 * Account Deletion Service (GDPR Compliance - SPEC-20251230)
 *
 * Monitors /deletion_requests collection and processes account deletions.
 * Handles:
 * - Firebase Auth account deletion
 * - Firestore user document + subcollections deletion
 * - Cloud Storage file deletion
 * - Social post anonymization (remove PII, keep anonymized data)
 * - Optional data export before deletion
 *
 * Flow:
 * 1. Android app queues deletion: /deletion_requests/{jobId}
 * 2. This function triggers on status change to PENDING
 * 3. Export data if requested
 * 4. Delete all user data (Auth, Firestore, Storage)
 * 5. Update job status to COMPLETED or FAILED
 */
exports.processAccountDeletion = onDocumentWritten(
    "deletion_requests/{jobId}",
    async (event) => {
      const jobId = event.params.jobId;

      try {
        const deletionData = event.data?.after?.data();

        if (!deletionData) {
          logger.warn(`No deletion data found for job: ${jobId}`);
          return null;
        }

        // Only process if status is PENDING
        if (deletionData.status !== "PENDING") {
          logger.info(`Job ${jobId} already processed (status: ${deletionData.status})`);
          return null;
        }

        const userId = deletionData.userId;
        const exportFirst = deletionData.exportFirst || false;

        logger.info(`🗑️ ACCOUNT_DELETION: Starting deletion for user ${userId} (job: ${jobId}, export: ${exportFirst})`);

        // Update status to PROCESSING
        await db.collection("deletion_requests").doc(jobId).update({
          status: "PROCESSING",
          startedAt: new Date(),
        });

        // Step 1: Export user data if requested
        if (exportFirst) {
          logger.info(`📤 ACCOUNT_DELETION: Exporting data for user ${userId}`);
          await exportUserData(userId);
        }

        // Step 2: Delete Firebase Auth account
        logger.info(`🔐 ACCOUNT_DELETION: Deleting Auth account for user ${userId}`);
        try {
          await auth.deleteUser(userId);
          logger.info(`✅ ACCOUNT_DELETION: Auth account deleted for user ${userId}`);
        } catch (authError) {
          if (authError.code === "auth/user-not-found") {
            logger.warn(`Auth account not found for user ${userId} (already deleted)`);
          } else {
            throw authError;
          }
        }

        // Step 3: Delete Firestore user document and subcollections
        logger.info(`🗄️ ACCOUNT_DELETION: Deleting Firestore data for user ${userId}`);
        await deleteUserFirestoreData(userId);

        // Step 4: Delete Cloud Storage files
        logger.info(`📦 ACCOUNT_DELETION: Deleting Storage files for user ${userId}`);
        await deleteUserStorageFiles(userId);

        // Step 5: Anonymize social posts (GDPR requirement - keep anonymized data)
        logger.info(`👤 ACCOUNT_DELETION: Anonymizing social posts for user ${userId}`);
        await anonymizeSocialPosts(userId);

        // Step 6: Update job status to COMPLETED
        await db.collection("deletion_requests").doc(jobId).update({
          status: "COMPLETED",
          completedAt: new Date(),
        });

        logger.info(`✅ ACCOUNT_DELETION: Deletion completed for user ${userId} (job: ${jobId})`);

        return null;
      } catch (error) {
        logger.error(`❌ ACCOUNT_DELETION ERROR for job ${jobId}:`, error);

        // Update job status to FAILED with error details
        try {
          await db.collection("deletion_requests").doc(jobId).update({
            status: "FAILED",
            failedAt: new Date(),
            error: error.message,
          });
        } catch (updateError) {
          logger.error("Failed to update deletion job status:", updateError);
        }

        throw error;
      }
    });

/**
 * Export user data to Cloud Storage (GDPR requirement).
 * Creates JSON export of all user data for download.
 *
 * @param {string} userId - User ID
 */
async function exportUserData(userId) {
  const {Storage} = require("@google-cloud/storage");
  const storage = new Storage();
  const bucket = storage.bucket(process.env.FIREBASE_STORAGE_BUCKET || `${process.env.GCLOUD_PROJECT}.appspot.com`);

  const exportData = {
    userId: userId,
    exportedAt: new Date().toISOString(),
    data: {},
  };

  try {
    // Export user profile
    const userDoc = await db.collection("users").doc(userId).get();
    if (userDoc.exists) {
      exportData.data.profile = userDoc.data();
    }

    // Export workouts
    const workoutsSnapshot = await db.collection("users").doc(userId).collection("workouts").get();
    exportData.data.workouts = workoutsSnapshot.docs.map((doc) => doc.data());

    // Export templates
    const templatesSnapshot = await db.collection("users").doc(userId).collection("templates").get();
    exportData.data.templates = templatesSnapshot.docs.map((doc) => doc.data());

    // Export social profile
    const socialProfileDoc = await db.collection("social_profiles").doc(userId).get();
    if (socialProfileDoc.exists) {
      exportData.data.socialProfile = socialProfileDoc.data();
    }

    // Export settings
    const settingsDoc = await db.collection("users").doc(userId).collection("settings").doc("preferences").get();
    if (settingsDoc.exists) {
      exportData.data.settings = settingsDoc.data();
    }

    // Upload export to Cloud Storage
    const fileName = `user-data-exports/${userId}/export-${Date.now()}.json`;
    const file = bucket.file(fileName);

    await file.save(JSON.stringify(exportData, null, 2), {
      contentType: "application/json",
      metadata: {
        userId: userId,
        exportedAt: new Date().toISOString(),
      },
    });

    // Generate signed URL (valid for 7 days)
    const [url] = await file.getSignedUrl({
      action: "read",
      expires: Date.now() + 7 * 24 * 60 * 60 * 1000, // 7 days
    });

    logger.info(`📤 Data export created: ${fileName} (URL valid for 7 days)`);

    // TODO: Send email to user with download link
    // await sendDataExportEmail(userId, url);

    return url;
  } catch (error) {
    logger.error(`Failed to export data for user ${userId}:`, error);
    throw error;
  }
}

/**
 * Delete all Firestore data for a user.
 * Deletes user document and all subcollections.
 *
 * @param {string} userId - User ID
 */
async function deleteUserFirestoreData(userId) {
  const batch = db.batch();
  let deletedCount = 0;

  try {
    // Delete user document
    batch.delete(db.collection("users").doc(userId));
    deletedCount++;

    // Delete subcollections (batch limit: 500 operations)
    const subcollections = ["workouts", "templates", "achievements", "settings", "gym_buddies", "chat_history"];

    for (const subcollection of subcollections) {
      const snapshot = await db.collection("users").doc(userId).collection(subcollection).limit(500).get();

      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
        deletedCount++;
      });
    }

    // Delete social profile
    const socialProfileRef = db.collection("social_profiles").doc(userId);
    batch.delete(socialProfileRef);
    deletedCount++;

    // Delete follow relationships
    const followingSnapshot = await db.collection("follow_relationships")
        .where("follower_user_id", "==", userId)
        .limit(500)
        .get();

    followingSnapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
      deletedCount++;
    });

    const followersSnapshot = await db.collection("follow_relationships")
        .where("followed_user_id", "==", userId)
        .limit(500)
        .get();

    followersSnapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
      deletedCount++;
    });

    await batch.commit();

    logger.info(`✅ Deleted ${deletedCount} Firestore documents for user ${userId}`);
  } catch (error) {
    logger.error(`Failed to delete Firestore data for user ${userId}:`, error);
    throw error;
  }
}

/**
 * Delete all Cloud Storage files for a user.
 * Deletes profile images, progress photos, workout media.
 *
 * @param {string} userId - User ID
 */
async function deleteUserStorageFiles(userId) {
  const {Storage} = require("@google-cloud/storage");
  const storage = new Storage();
  const bucket = storage.bucket(process.env.FIREBASE_STORAGE_BUCKET || `${process.env.GCLOUD_PROJECT}.appspot.com`);

  try {
    const folders = [
      `users/${userId}/`,
      `profile-images/${userId}/`,
      `progress-photos/${userId}/`,
      `workout-media/${userId}/`,
    ];

    let deletedCount = 0;

    for (const folder of folders) {
      const [files] = await bucket.getFiles({prefix: folder});

      for (const file of files) {
        await file.delete();
        deletedCount++;
      }
    }

    logger.info(`✅ Deleted ${deletedCount} Storage files for user ${userId}`);
  } catch (error) {
    logger.error(`Failed to delete Storage files for user ${userId}:`, error);
    // Don't throw - storage deletion is non-critical
  }
}

/**
 * Anonymize social posts (GDPR requirement).
 * Remove PII but keep anonymized data for feed integrity.
 *
 * Anonymization:
 * - Set user_id to "deleted-user-{hash}"
 * - Remove caption, media_urls
 * - Keep workout stats (total_volume, exercises_count, prs_count)
 *
 * @param {string} userId - User ID
 */
async function anonymizeSocialPosts(userId) {
  try {
    const postsSnapshot = await db.collection("workout_posts")
        .where("user_id", "==", userId)
        .limit(500)
        .get();

    const batch = db.batch();
    const anonymousId = `deleted-user-${userId.substring(0, 8)}`;

    postsSnapshot.docs.forEach((doc) => {
      const postData = doc.data();

      batch.update(doc.ref, {
        user_id: anonymousId,
        caption: "[Deleted]",
        media_urls: null,
        media_thumbnails: null,
        // Keep workout stats for feed integrity
        total_volume: postData.total_volume || null,
        exercises_count: postData.exercises_count || null,
        prs_count: postData.prs_count || 0,
        visibility: "PRIVATE", // Hide from public feed
      });
    });

    await batch.commit();

    logger.info(`✅ Anonymized ${postsSnapshot.size} posts for user ${userId}`);
  } catch (error) {
    logger.error(`Failed to anonymize posts for user ${userId}:`, error);
    // Don't throw - post anonymization is non-critical
  }
}

/**
 * Cloud Function: aiReport
 * Handles user reports of AI-generated content that may be harmful or violate safety guidelines.
 *
 * @param {Object} data - Request data
 * @param {string} data.messageId - ID of the AI message being reported
 * @param {string} data.messageContent - Content of the AI message
 * @param {string} data.reason - Report reason (HARMFUL_MEDICAL, MISINFORMATION, INAPPROPRIATE, OTHER)
 * @param {string} data.reasonDescription - Human-readable description of the reason
 * @param {string} data.notes - Optional additional notes from the reporter
 * @param {string} data.userId - ID of the user submitting the report
 * @param {Object} context - Firebase Functions context
 * @returns {Promise<{success: boolean, reportId: string}>}
 */
exports.aiReport = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAuthenticated(request);
  const data = request.data || {};

  const {
    messageId,
    messageContent,
    reason,
    reasonDescription,
    notes,
    userId,
  } = data;

  const validatedMessageId = requireString(messageId, "messageId", 256);
  const validatedContent = requireString(
      messageContent,
      "messageContent",
      8000,
  );
  const validatedUserId = requireString(userId, "userId", 128);
  if (!["HARMFUL_MEDICAL", "MISINFORMATION", "INAPPROPRIATE", "OTHER"]
      .includes(reason)) {
    throw new HttpsError("invalid-argument", "Invalid report reason");
  }
  if (caller.uid !== validatedUserId) {
    throw new HttpsError(
        "permission-denied",
        "User can only submit reports on their own behalf",
    );
  }
  const validatedReasonDescription = reasonDescription == null ? "" :
      requireString(reasonDescription, "reasonDescription", 500);
  const validatedNotes = notes == null || notes === "" ? "" :
      requireString(notes, "notes", 2000);
  await enforceCallableRateLimit(caller.uid, "ai_report", 5);

  try {
    // Create AI report document
    const reportRef = await db.collection("ai_reports").add({
      message_id: validatedMessageId,
      message_content: validatedContent,
      reason: reason,
      reason_description: validatedReasonDescription,
      notes: validatedNotes,
      reporter_user_id: caller.uid,
      reported_at: Date.now(),
      status: "PENDING", // PENDING, REVIEWED, RESOLVED, DISMISSED
      reviewed_at: null,
      reviewed_by_user_id: null,
      resolution_notes: null,
      created_at: new Date(),
    });

    logger.info(
        `AI content reported: ${reportRef.id} by user ${caller.uid} ` +
        `for message ${validatedMessageId}`,
    );

    // Send notification to admin channel (optional - requires admin notification setup)
    // await sendAdminNotification('ai-safety', `New AI report: ${reason}`);

    return {
      success: true,
      reportId: reportRef.id,
    };
  } catch (error) {
    logger.error("Error processing AI report:", error);
    throw callableFailure(error, "Failed to process AI report");
  }
});

/**
 * Cloud Function: moderationAction
 * Handles admin moderation actions on user-generated content.
 * Requires admin custom claim verification.
 *
 * @param {Object} data - Request data
 * @param {string} data.actionType - Type of moderation action (HIDE_POST, DELETE_POST, HIDE_COMMENT, DELETE_COMMENT, WARN_USER, SUSPEND_USER, DISMISS_REPORT)
 * @param {string} data.targetId - ID of the target entity (post ID, comment ID, user ID)
 * @param {string} data.targetType - Type of target (POST, COMMENT, USER, REPORT)
 * @param {string} data.reason - Reason for the moderation action
 * @param {number} [data.durationDays] - Suspension duration in days (for SUSPEND_USER)
 * @param {string} [data.reportId] - Associated content report ID (optional)
 * @param {Object} context - Firebase Functions context
 * @returns {Promise<{success: boolean, actionId: string}>}
 */
exports.moderationAction = onCall(CALLABLE_OPTIONS, async (request) => {
  const caller = requireAdmin(request);
  const data = request.data || {};

  const {
    actionType,
    targetId: rawTargetId,
    targetType,
    reason: rawReason,
    durationDays,
    reportId: rawReportId,
  } = data;

  const allowedActions = [
    "HIDE_POST",
    "DELETE_POST",
    "HIDE_COMMENT",
    "DELETE_COMMENT",
    "WARN_USER",
    "SUSPEND_USER",
    "DISMISS_REPORT",
  ];
  if (!allowedActions.includes(actionType)) {
    throw new HttpsError("invalid-argument", "Invalid moderation action");
  }
  const targetId = requireString(rawTargetId, "targetId", 256);
  const reason = requireString(rawReason, "reason", 1000);
  if (!["POST", "COMMENT", "USER", "REPORT"].includes(targetType)) {
    throw new HttpsError("invalid-argument", "Invalid target type");
  }
  const expectedTargetTypes = {
    HIDE_POST: "POST",
    DELETE_POST: "POST",
    HIDE_COMMENT: "COMMENT",
    DELETE_COMMENT: "COMMENT",
    WARN_USER: "USER",
    SUSPEND_USER: "USER",
    DISMISS_REPORT: "REPORT",
  };
  if (expectedTargetTypes[actionType] !== targetType) {
    throw new HttpsError(
        "invalid-argument",
        "Moderation action does not match target type",
    );
  }
  if (durationDays != null &&
      (!Number.isInteger(durationDays) || durationDays < 1 ||
       durationDays > 365)) {
    throw new HttpsError("invalid-argument", "Invalid suspension duration");
  }
  const reportId = rawReportId == null ? null :
      requireString(rawReportId, "reportId", 256);
  if (actionType === "DISMISS_REPORT" && reportId !== targetId) {
    throw new HttpsError(
        "invalid-argument",
        "reportId must match targetId for report dismissal",
    );
  }
  const adminUserId = caller.uid;

  try {
    // Execute the moderation action
    switch (actionType) {
      case "HIDE_POST":
        await db.collection("workout_posts").doc(targetId).update({
          is_hidden: true,
          hidden_reason: reason,
          hidden_at: new Date(),
          hidden_by_user_id: adminUserId,
        });
        logger.info(`Admin ${adminUserId} hid post ${targetId}: ${reason}`);
        break;

      case "DELETE_POST":
        await db.collection("workout_posts").doc(targetId).delete();
        logger.info(`Admin ${adminUserId} deleted post ${targetId}: ${reason}`);
        break;

      case "HIDE_COMMENT":
        await db.collection("post_comments").doc(targetId).update({
          is_hidden: true,
          hidden_reason: reason,
          hidden_at: new Date(),
          hidden_by_user_id: adminUserId,
        });
        logger.info(`Admin ${adminUserId} hid comment ${targetId}: ${reason}`);
        break;

      case "DELETE_COMMENT":
        await db.collection("post_comments").doc(targetId).delete();
        logger.info(`Admin ${adminUserId} deleted comment ${targetId}: ${reason}`);
        break;

      case "WARN_USER":
        await db.collection("account_restrictions").add({
          user_id: targetId,
          restriction_type: "WARNED",
          reason: reason,
          start_time: Date.now(),
          end_time: null,
          is_active: true,
          created_by_user_id: adminUserId,
          created_at: new Date(),
        });
        logger.info(`Admin ${adminUserId} warned user ${targetId}: ${reason}`);
        // Send notification to user (implement via FCM)
        break;

      case "SUSPEND_USER":
        if (!durationDays) {
          throw new HttpsError(
              "invalid-argument",
              "durationDays is required for SUSPEND_USER",
          );
        }
        const endTime = Date.now() + (durationDays * 24 * 60 * 60 * 1000);
        await db.collection("account_restrictions").add({
          user_id: targetId,
          restriction_type: "SUSPENDED",
          reason: reason,
          start_time: Date.now(),
          end_time: endTime,
          is_active: true,
          created_by_user_id: adminUserId,
          created_at: new Date(),
        });
        logger.info(`Admin ${adminUserId} suspended user ${targetId} for ${durationDays} days: ${reason}`);
        // Send notification to user (implement via FCM)
        break;

      case "DISMISS_REPORT":
        if (!reportId) {
          throw new HttpsError(
              "invalid-argument",
              "reportId is required for DISMISS_REPORT",
          );
        }
        await db.collection("content_reports").doc(reportId).update({
          status: "DISMISSED",
          reviewed_at: Date.now(),
          reviewed_by_user_id: adminUserId,
          resolution_notes: reason,
        });
        logger.info(`Admin ${adminUserId} dismissed report ${reportId}: ${reason}`);
        break;

      default:
        throw new HttpsError(
            "invalid-argument",
            `Unknown action type: ${actionType}`,
        );
    }

    // Log the moderation action for audit trail
    const actionRef = await db.collection("moderation_actions").add({
      admin_user_id: adminUserId,
      action_type: actionType,
      target_type: targetType,
      target_id: targetId,
      reason: reason,
      duration_days: durationDays || null,
      report_id: reportId || null,
      created_at: new Date(),
    });

    // Update associated report status if provided
    if (reportId) {
      await db.collection("content_reports").doc(reportId).update({
        status: "RESOLVED",
        reviewed_at: Date.now(),
        reviewed_by_user_id: adminUserId,
        resolution_notes: `Action taken: ${actionType} - ${reason}`,
      });
    }

    logger.info(`Moderation action ${actionRef.id} completed successfully`);

    return {
      success: true,
      actionId: actionRef.id,
    };
  } catch (error) {
    logger.error("Error executing moderation action:", error);
    throw callableFailure(error, "Failed to execute moderation action");
  }
});
