# Firebase Deployment Guide

This is the canonical Firebase deployment entry point for Liftrix. Deploy only the tracked root sources selected by `firebase.json`; files under `docs/firebase` are guidance and are never copied into a deployable location.

## Prerequisites

- Firebase CLI installed and authenticated with the least-privileged operator account.
- A clean Git worktree at the reviewed release commit.
- An explicit Firebase project ID. Do not use `.firebaserc`, `firebase use`, or a local default project.
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

## Deploy

Use the tracked script from the repository root. Without `--execute` it validates the source set and prints a dry-run plan.

```bash
bash scripts/firebase_deploy.sh deploy \
  --project PROJECT_ID \
  --release-tag firebase-release-YYYYMMDD \
  --previous-backend-ref PREVIOUS_TAG_OR_COMMIT \
  --firestore-export gs://BUCKET/exports/DATE \
  --storage-recovery "RECOVERY_EVIDENCE_ID" \
  --execute
```

The script refuses missing/invalid project IDs, dirty tracked state, missing root sources, a missing previous baseline, or absent backup evidence. It creates and pushes the release tag before deploying in this order:

1. Functions
2. Firestore indexes
3. Firestore rules
4. Storage rules
5. Realtime Database rules

It records the explicit target, commit/tag, previous baseline, source inventory, CLI revision, deploy output, and receipt directory. It never prints or stores secrets and never restores data automatically.

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
- Never use destructive cleanup, delete indexes, relax App Check, or deploy hosting from this procedure.
- Keep receipt directories outside the tracked source tree or under ignored `.release-receipts/`.
- Treat a successful CLI command as deployment evidence, not proof that product flows are healthy; smoke evidence is required.
