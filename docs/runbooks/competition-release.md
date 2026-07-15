# Competition Release Runbook

This runbook ties one signed Android artifact to one reviewed commit, Firebase target, deployment receipt, and rollback baseline.

## Freeze and ownership

Before the release owner starts:

- Confirm the approved commit is clean and immutable for the rehearsal.
- Confirm package `com.liftrix.app`, version name `1.0.1`, and version code `10001`.
- Confirm the protected GitHub Actions secrets `GOOGLE_SERVICES_JSON_BASE64`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- Confirm the protected Actions variable `RELEASE_CERT_SHA256` matches the canonical certificate, with the digest normalized to lowercase hexadecimal without colons.
- Confirm the canonical fingerprints: SHA-256 `544162ace5fc6b03265f75db6f7cab5409c5981bd0097072d450f3ec01f548da`; SHA-1 `a3a0251d07bfe06d5dba72ee635eb7bfdf69cfb0`.
- Confirm the two encrypted offline keystore backups and their custodians without recording paths, filenames, or passwords.
- Name the previous Android artifact/mapping and previous Firebase backend ref.

## Clean CI release

1. Push the frozen commit to the active `master` workflow.
2. Confirm CI validates the Firebase Android package without printing `google-services.json`.
3. Confirm the release job builds APK and AAB, refuses missing signing inputs, and verifies the APK/AAB certificate against `RELEASE_CERT_SHA256`.
4. Download the single 90-day artifact set named `liftrix-release-<commit>`.
5. Recompute `SHA256SUMS`; compare the APK/AAB/mapping hashes and certificate fingerprints to `release-manifest.json`.
6. Verify `apksigner verify --print-certs` on the APK and install the exact signed APK through the intended competition path.
7. Preserve the commit SHA, version/package, certificate fingerprints, mapping, checksums, workflow run/attempt, and artifact URL in the release record.

The workflow removes decoded Firebase config and the temporary keystore in an `always()` cleanup step. Do not attach either protected input to an artifact.

## Consent gate

On a fresh/no-consent process start, confirm Analytics, Crashlytics, and Performance are disabled. Confirm denial, missing user, and consent-read failure remain disabled. Confirm only the existing stored-consent owner enables all three products after an affirmative stored decision; no UI or event schema changes are part of this release.

## Firebase deploy and smoke

Follow [the canonical Firebase guide](../firebase/firebase-deployment-guide.md). Record:

- explicit project ID and CLI revision;
- predeploy release tag/commit and previous backend ref;
- managed Firestore export URI and Storage recovery evidence;
- ordered component results and receipt directory;
- completed smoke matrix, including App Check and denied cross-user access;
- the matching Android release manifest.

Hosting is excluded. A successful rules/functions deploy does not restore or undo data.

## Rollback decision tree

| Condition | Action |
| --- | --- |
| Android binary defect | Distribute the previous signed artifact and mapping whose manifest matches the approved baseline. Never resign it with a new key. |
| Rules/functions defect | Check out the previous backend tag in a clean worktree and run the script's explicit-project `rollback` mode. |
| Data corruption or accidental writes | Stop code rollback and invoke the managed Firestore/Storage recovery procedure with the recorded export/recovery evidence. |
| Consent/telemetry regression | Keep the binary unavailable, preserve the evidence, and use the previous artifact while investigating the manifest/provider boundary. |

After rollback, repeat the relevant smoke checks and attach a new receipt. State explicitly that code/rules rollback cannot undo already-written data and that uninstall/downgrade can lose unsynced local Room data.

## Final evidence checklist

- [ ] Frozen commit and release tag
- [ ] Explicit Firebase project and previous backend baseline
- [ ] Signed APK, AAB, exact R8 mapping, `release-manifest.json`, and `SHA256SUMS`
- [ ] APK package/version/signature verification
- [ ] Canonical certificate SHA-256 and SHA-1 fingerprints
- [ ] 90-day artifact retention confirmed
- [ ] Firebase export and Storage recovery evidence
- [ ] Ordered deploy receipt and CLI revision
- [ ] Consent cold-start evidence
- [ ] Smoke evidence and rollback rehearsal/commands
