# Firebase Deployment Guide

This is the canonical Firebase deployment entry point for Liftrix. Deploy only the tracked root sources selected by `firebase.json`; files under `docs/firebase` are guidance and are never copied into a deployable location.

## Prerequisites

- Firebase CLI installed and authenticated with the least-privileged operator account.
- A clean Git worktree at the reviewed release commit.
- An explicit Firebase project ID. Do not use `.firebaserc`, `firebase use`, or a local default project.
- At least 20 GiB free on the release-operations filesystem. The script fails before deploy or rollback if the floor is not met.
- A sanitized containment decision: Firebase Admin credential status `revoked-audited`, containment date, responsible role, and a completed historical signer classification. Do not include credential IDs or private audit coordinates.
- A nonempty sanitized containment-evidence file; the script records only its SHA-256, never its path or contents.
- The independently downloaded and verified current and previous Android `release-manifest.json` files.
- A managed Firestore export URI and Storage recovery evidence recorded before deployment.
- A reviewed release tag and the previous backend baseline ref.

## Canonical source set

`firebase.json` must continue to select these root sources:

| Component | Source |
| --- | --- |
| Firestore rules | `firestore.rules` |
| Firestore indexes | `firestore.indexes.json` |
| Storage rules | `storage.rules` |
| Realtime Database rules | `database.rules.json` |
| Functions | `functions/` |

Hosting is deliberately omitted from the competition deployment. Never deploy copied rules, stale snapshots, `cloud-functions/`, or an admin dashboard as a substitute for the root source set.

## Contract release gate

Use Node 22 and Java 21 or newer. Run from a clean checkout before creating the release tag:

```bash
npm --prefix functions ci
npm --prefix functions run lint
npm --prefix functions run test:unit
LIFTRIX_RULES_EMULATOR=1 firebase emulators:exec --only firestore \
  "npm --prefix functions run test:emulator -- --test-name-pattern Firestore.*contracts test/firestoreContracts.spec.js"
FUNCTIONS_DISCOVERY_TIMEOUT=60 firebase emulators:exec --only firestore,functions \
  "npm --prefix functions run test:emulator"
```

The task-specific `LIFTRIX_RULES_EMULATOR=1` opt-in and Firestore-only command
are intentional: valid synthetic
`deletion_requests` and `support_tickets` creates must not invoke destructive or
email Functions while Auth, Storage, and external mail services are absent. The
Functions-inclusive command loads the deployable backend and runs the public
discovery regression. Without the explicit rules-matrix opt-in, the destructive
contract suite skips because it already passed in the isolated Firestore gate.

`firebase.json` predeploy runs Functions lint plus offline unit tests. It does
not start emulators, and a green predeploy never substitutes for both emulator
receipts. Lint continues to enforce parsing, undefined/unused variables,
unreachable code, switch lexical safety, and the other ESLint recommended
correctness rules. Cross-platform formatting, legacy JSDoc debt, and the
separately owned `admin-dashboard/**` package are not part of this Node release
gate.

The canonical client contracts are:

- owner-create-only `deletion_requests/{jobId}` with matching `jobId` and
  authenticated `userId`, exact `PENDING` initial fields, and server-owned
  status transitions;
- owner-create-only `support_tickets/{ticketId}` with matching ticket/user IDs,
  bounded current sync fields, `OPEN`/unsent initial state, and server-owned
  processing;
- authenticated `audit_logs` creation only when payload `userId` equals the
  caller, with admin-only reads and no client update/delete.

No command in this section is live-state evidence. Do not proceed to deploy if
either emulator receipt is absent or if any command exits nonzero.

## Deploy

Use the tracked script from the repository root. Without `--execute` it validates the source set and prints a dry-run plan.

```bash
bash scripts/firebase_deploy.sh deploy \
  --project PROJECT_ID \
  --release-tag firebase-release-YYYYMMDD \
  --previous-backend-ref PREVIOUS_TAG_OR_COMMIT \
  --firestore-export gs://BUCKET/exports/DATE \
  --storage-recovery "RECOVERY_EVIDENCE_ID" \
  --android-manifest /secure/release-set/release-manifest.json \
  --previous-android-manifest /secure/previous-release/release-manifest.json \
  --containment-evidence /secure/receipts/sanitized-containment.txt \
  --credential-status revoked-audited \
  --containment-date YYYY-MM-DD \
  --containment-owner RELEASE_OWNER_ROLE \
  --signer-status not-exposed \
  --execute
```

Allowed completed signer classifications are `not-exposed`, `not-distributed`, `rotation-complete`, and `reset-complete`. The script refuses missing/invalid project IDs, any dirty or untracked source state, low capacity, unresolved containment, an Android manifest from a different commit, missing root sources, a missing previous baseline, or absent backup evidence. It creates and pushes the release tag before deploying in this order:

1. Functions
2. Firestore indexes
3. Firestore rules
4. Storage rules
5. Realtime Database rules

It records the explicit target, commit/tag, previous backend baseline, source inventory, capacity result, sanitized containment status/evidence digest, current and previous Android package/version/certificate/manifest digests, CLI revision, deploy output, and receipt directory. It copies both nonsecret Android manifests into the receipt but never records their original local paths. It never copies or prints containment-evidence contents, never stores secrets, and never restores data automatically.

Treat Functions and Firestore rules as one candidate backend release unit. If
any step in the scripted sequence fails, stop promotion, retain the partial
receipt, and restore the reviewed previous backend baseline before retrying.
Never describe a local commit or a partially completed deploy as the active
backend revision.

The sanitized deployment receipt must record all of the following without
secret values or private account data:

- explicit Firebase project and operator role;
- reviewed commit/release tag plus previous backend baseline;
- Firestore backup/export and Storage recovery evidence identifiers;
- Functions revisions and Firestore rules release/revision reported by the CLI
  or console;
- callable App Check enforcement, required admin claims, and required Remote
  Config state as verified at deploy time;
- timestamps and exit status for clean install, lint, offline unit, isolated
  rules matrix, and Functions/public-discovery emulator gates;
- disposable synthetic IDs used for public-post, support, and deletion smoke;
- smoke results and rollback-tabletop result.

Account-deletion smoke is permitted only for a disposable test user in the
explicit controlled project. A real user, a default project, or an unknown
backup/rollback state is an immediate stop condition.

## Smoke evidence

Complete the generated `smoke-checklist.md` against the signed Android artifact and the deployed project. Include operator, UTC timestamp, device/build identity, successful and denied authorization checks, App Check coverage, and consent-state observations. Then record it:

```bash
bash scripts/firebase_deploy.sh smoke \
  --project PROJECT_ID \
  --release-tag firebase-release-YYYYMMDD \
  --smoke-evidence /secure/receipts/smoke-evidence.md
```

Promotion is not complete until the smoke receipt is attached to the release evidence and reconciles with the Android `release-manifest.json`.

## Rollback

Android and Firebase rollback are separate operations. For Firebase rules/functions rollback, check out the reviewed rollback ref in a clean worktree and run:

```bash
bash scripts/firebase_deploy.sh rollback \
  --project PROJECT_ID \
  --rollback-ref PREVIOUS_BACKEND_TAG \
  --firestore-export gs://BUCKET/exports/DATE \
  --storage-recovery "RECOVERY_EVIDENCE_ID" \
  --execute
```

The script requires `HEAD` to equal the rollback ref, redeploys only the canonical root components, and records a receipt. A rules/functions rollback cannot undo data writes. Firestore restore and Storage recovery require their managed recovery procedures and must be approved and recorded separately.

For Android, distribute the exact prior signed artifact whose `release-manifest.json`, certificate fingerprint, version, commit, mapping, and checksums match the approved baseline. Do not rotate the signing key during rollback.

## Safety rules

- Never add a project ID, secret, service-account file, `.firebaserc`, keystore, or Firebase config to this repository.
- Revoke and audit an exposed credential before history cleanup; never treat a rewritten history as containment.
- Never use destructive cleanup, delete indexes, relax App Check, or deploy hosting from this procedure.
- Keep receipt directories outside the tracked source tree or under ignored `.release-receipts/`.
- Treat a successful CLI command as deployment evidence, not proof that product flows are healthy; smoke evidence is required.
