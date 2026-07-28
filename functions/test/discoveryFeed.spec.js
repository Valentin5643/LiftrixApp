"use strict";

const assert = require("node:assert/strict");
const {after, before, describe, test} = require("node:test");
const {deleteApp, getApps} = require("firebase-admin/app");
const {FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");
const firebaseFunctionsTest = require("firebase-functions-test")({
  projectId: process.env.GCLOUD_PROJECT || "demo-liftrix-contracts",
});

const functions = require("../index");

after(async () => {
  firebaseFunctionsTest.cleanup();
  await Promise.all(getApps().map((app) => deleteApp(app)));
});

describe("discovery feed contract", () => {
  test("unit: discovery payload uses the modular server timestamp", () => {
    const entry = functions.__test.buildDiscoveryFeedEntry(
        "target-user",
        "post-id",
        {
          user_id: "author-user",
          created_at: Timestamp.fromMillis(1_700_000_000_000),
        },
        42,
    );

    assert.deepEqual(entry.created_at, FieldValue.serverTimestamp());
    assert.equal(entry.feed_type, "DISCOVERY");
    assert.equal(entry.post_id, "post-id");
  });

  test("unit: wrapped handler exits offline for a private post", async () => {
    const wrapped = firebaseFunctionsTest.wrap(
        functions.generateFeedOnPostCreation,
    );

    await assert.doesNotReject(() => wrapped({
      params: {postId: "private-post"},
      data: {
        before: {},
        after: {
          user_id: "author-user",
          visibility: "PRIVATE",
          created_at: Timestamp.fromMillis(1_700_000_000_000),
        },
      },
    }));
  });
});

describe("discovery feed emulator", {skip: !process.env.FIRESTORE_EMULATOR_HOST}, () => {
  const db = getFirestore();
  const authorId = "synthetic-author";
  const targetId = "synthetic-target";
  const postId = "synthetic-public-post";
  const feedId = `${targetId}_discovery_${postId}`;
  let originalRandom;

  before(async () => {
    originalRandom = Math.random;
    Math.random = () => 0;
    await db.collection("users").doc(targetId).set({
      last_active_at: Timestamp.now(),
      privacy_settings: {discoverable: true},
      discovery_settings: {max_posts_per_day: 10},
    });
  });

  after(async () => {
    Math.random = originalRandom;
    await Promise.all([
      db.collection("feed_cache").doc(feedId).delete(),
      db.collection("users").doc(targetId).delete(),
    ]);
  });

  test("emulator: public post creates a timestamped discovery entry", async () => {
    const wrapped = firebaseFunctionsTest.wrap(
        functions.generateFeedOnPostCreation,
    );
    const createdAt = Timestamp.now();

    await wrapped({
      params: {postId},
      data: {
        before: {},
        after: {
          user_id: authorId,
          visibility: "PUBLIC",
          created_at: createdAt,
          like_count: 0,
          comment_count: 0,
        },
      },
    });

    const feedSnapshot = await db.collection("feed_cache").doc(feedId).get();
    assert.equal(feedSnapshot.exists, true);
    assert.equal(feedSnapshot.get("feed_type"), "DISCOVERY");
    assert.ok(feedSnapshot.get("created_at") instanceof Timestamp);
  });
});
