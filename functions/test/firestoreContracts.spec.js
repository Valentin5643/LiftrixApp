"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {after, afterEach, before, describe, test} = require("node:test");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const {
  Timestamp,
  deleteDoc,
  doc,
  getDoc,
  setDoc,
  updateDoc,
} = require("firebase/firestore");

const functions = require("../index");

const emulatorAvailable = Boolean(process.env.FIRESTORE_EMULATOR_HOST);
const rulesMatrixEnabled = process.env.LIFTRIX_RULES_EMULATOR === "1";
let testEnvironment;

function deletionPayload(userId, jobId) {
  return {
    userId,
    requestedAt: 1_700_000_000_000,
    exportFirst: false,
    status: "PENDING",
    jobId,
  };
}

function supportPayload(userId, ticketId) {
  return {
    user_id: userId,
    ticket_id: ticketId,
    category: "Bug Report",
    subject: "Synthetic support subject",
    description: "Synthetic support description",
    device_info: null,
    app_version: null,
    status: "OPEN",
    created_at: Timestamp.fromMillis(1_700_000_000_000),
    updated_at: null,
    email_sent: false,
    sync_version: 1,
  };
}

describe("defensive Functions contract validation", () => {
  test("unit: deletion validation binds the document job ID", () => {
    assert.deepEqual(
        functions.__test.validatePendingDeletionRequest(
            "job-id",
            deletionPayload("owner-user", "job-id"),
        ),
        {userId: "owner-user", exportFirst: false},
    );
    assert.throws(() => functions.__test.validatePendingDeletionRequest(
        "job-id",
        deletionPayload("owner-user", "spoofed-job"),
    ));
  });

  test("unit: support validation rejects server-owned initial state", () => {
    assert.deepEqual(
        functions.__test.validateSupportTicket(
            "ticket-id",
            supportPayload("owner-user", "ticket-id"),
        ),
        {userId: "owner-user"},
    );
    assert.throws(() => functions.__test.validateSupportTicket(
        "ticket-id",
        {...supportPayload("owner-user", "ticket-id"), email_sent: true},
    ));
  });
});

describe(
    "Firestore deletion/support/audit contracts",
    {skip: !emulatorAvailable || !rulesMatrixEnabled},
    () => {
  before(async () => {
    testEnvironment = await initializeTestEnvironment({
      projectId: process.env.GCLOUD_PROJECT || "demo-liftrix-contracts",
      firestore: {
        rules: fs.readFileSync(
            path.resolve(__dirname, "..", "..", "firestore.rules"),
            "utf8",
        ),
      },
    });
  });

  afterEach(async () => testEnvironment.clearFirestore());
  after(async () => testEnvironment?.cleanup());

  test("emulator: deletion permits only the exact owner create", async () => {
    const ownerDb = testEnvironment.authenticatedContext("owner-user").firestore();
    const intruderDb = testEnvironment.authenticatedContext("intruder-user").firestore();
    const ownerRef = doc(ownerDb, "deletion_requests/job-owner");

    await assertSucceeds(setDoc(
        ownerRef,
        deletionPayload("owner-user", "job-owner"),
    ));
    await assertFails(getDoc(ownerRef));
    await assertFails(updateDoc(ownerRef, {status: "PROCESSING"}));
    await assertFails(deleteDoc(ownerRef));
    await assertFails(setDoc(
        doc(intruderDb, "deletion_requests/job-foreign"),
        deletionPayload("owner-user", "job-foreign"),
    ));
    await assertFails(setDoc(
        doc(ownerDb, "deletion_requests/job-malformed"),
        deletionPayload("owner-user", "wrong-job"),
    ));
  });

  test("emulator: support permits only the exact unsent owner create", async () => {
    const ownerDb = testEnvironment.authenticatedContext("owner-user").firestore();
    const intruderDb = testEnvironment.authenticatedContext("intruder-user").firestore();
    const ownerRef = doc(ownerDb, "support_tickets/ticket-owner");

    await assertSucceeds(setDoc(
        ownerRef,
        supportPayload("owner-user", "ticket-owner"),
    ));
    await assertFails(getDoc(ownerRef));
    await assertFails(updateDoc(ownerRef, {email_sent: true}));
    await assertFails(deleteDoc(ownerRef));
    await assertFails(setDoc(
        doc(intruderDb, "support_tickets/ticket-foreign"),
        supportPayload("owner-user", "ticket-foreign"),
    ));
    await assertFails(setDoc(
        doc(ownerDb, "support_tickets/ticket-sent"),
        {...supportPayload("owner-user", "ticket-sent"), email_sent: true},
    ));
    await assertFails(setDoc(
        doc(ownerDb, "support_tickets/ticket-mismatch"),
        supportPayload("owner-user", "wrong-ticket"),
    ));
  });

  test("emulator: audit identity cannot be spoofed", async () => {
    const ownerDb = testEnvironment.authenticatedContext("owner-user").firestore();
    const adminDb = testEnvironment.authenticatedContext(
        "admin-user",
        {admin: true},
    ).firestore();
    const ownerRef = doc(ownerDb, "audit_logs/audit-owner");

    await assertSucceeds(setDoc(ownerRef, {
      eventType: "DATA_TAMPERING",
      userId: "owner-user",
      timestamp: Timestamp.fromMillis(1_700_000_000_000),
    }));
    await assertFails(setDoc(doc(ownerDb, "audit_logs/audit-spoofed"), {
      eventType: "DATA_TAMPERING",
      userId: "foreign-user",
      timestamp: Timestamp.fromMillis(1_700_000_000_000),
    }));
    await assertFails(getDoc(ownerRef));
    await assertSucceeds(getDoc(doc(adminDb, "audit_logs/audit-owner")));
    await assertFails(updateDoc(ownerRef, {eventType: "CONFLICT_RESOLUTION"}));
    await assertFails(deleteDoc(ownerRef));
  });
    },
);
