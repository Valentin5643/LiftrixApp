# Competition Release Runbook

This runbook ties one signed Android artifact to one reviewed commit, Firebase target, deployment receipt, and rollback baseline.

## Freeze and ownership

Before the release owner starts:

- Confirm every child code SPEC is green, then increment the final version in `app/build.gradle.kts` if the release owner requires a new identity. Freeze the resulting clean commit; any subsequent code or configuration change invalidates all candidate artifacts.
- Run `./gradlew :app:writeReleaseIdentity` and record the package/version emitted under `app/build/release-identity/release.properties`. Gradle is the canonical source; do not type a second expected version into CI.
- Confirm the protected GitHub Actions secrets `GOOGLE_SERVICES_JSON_BASE64`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- Confirm the protected Actions variable `RELEASE_CERT_SHA256` matches the canonical certificate, with the digest normalized to lowercase hexadecimal without colons.
- Confirm the canonical fingerprints: SHA-256 `544162ace5fc6b03265f75db6f7cab5409c5981bd0097072d450f3ec01f548da`; SHA-1 `a3a0251d07bfe06d5dba72ee635eb7bfdf69cfb0`.
- Confirm the two encrypted offline keystore backups and their custodians without recording paths, filenames, or passwords.
- Name the previous Android artifact/mapping/manifest and previous Firebase backend ref.

## Credential containment gate

Publication and backend deployment are forbidden until the release owner records all of the following in a private control system and copies only the sanitized fields into the release receipt:

- containment date and responsible role;
- Firebase Admin credential status `revoked-audited` and IAM/log-audit completion;
- signer classification: `not-exposed`, `not-distributed`, `rotation-complete`, or `reset-complete`;
- public certificate SHA-256/SHA-1 fingerprints and any completed OAuth/App Check registration update status.

Do not copy secret values, credential/key IDs, service-account files, vault paths, private audit coordinates, or Git history locations into the repository, workflow logs, artifacts, or receipt. Credential revocation must happen before any coordinated history cleanup.

Prepare a sanitized containment-evidence file in approved private storage. The deploy script records only its SHA-256 so the receipt can be reconciled without copying the file, its path, or its contents.

## Capacity and artifact protection

- Require at least 20 GiB free in the CI workspace and on the release-operations filesystem. CI and the deploy script fail closed below this floor and log only nonsecret capacity counts. Override the default only through the reviewed `MIN_RELEASE_FREE_DISK_GIB` variable; low-space testing should set it above available capacity.
- Confirm the workflow's bounded release envelope remains active: 5 GiB Gradle heap, 1 GiB metaspace, at most two Gradle workers, and at most two R8 workers. Any OOM/daemon-expiration warning invalidates the run even if partial APK/AAB outputs exist.
- Preserve the last known-good artifact in retained storage before reclaiming local capacity. Do not delete or overwrite it to make a candidate build fit.
- Run two consecutive clean workflow builds from the same frozen commit with sufficient capacity. Independently verify both; designate one successful run as the final retained set.
- Once the final manifest is accepted, do not rebuild. Any code, config, protected input, signer, or backend-source change requires a new version/release candidate and a new receipt.

## Clean CI release

1. Push the frozen commit through the protected `competition-release` environment to the active `master` workflow.
2. Confirm the capacity/identity preflight passes and CI validates the Firebase Android package without printing `google-services.json`.
3. Confirm `compileDebugKotlin`, `validateRoomQueries`, aggregate `testDebugUnitTest`, `lintFull`, and aggregate `build` all pass before the release build is staged or uploaded.
4. Confirm the release job refuses missing/malformed config, missing signing inputs, package/version mismatch, certificate mismatch, low capacity, failed gates, and missing/duplicate APK/AAB/mapping outputs.
5. Download the single 90-day artifact set named `liftrix-release-<commit>` to a fresh verification directory. Do not verify files in the runner workspace.
6. Recompute `SHA256SUMS`; compare APK/AAB/mapping hashes, package/version, commit/run, capacity, and certificate fingerprints to `release-manifest.json`.
7. Verify the APK with `apksigner`, inspect package/version with `aapt`, verify the AAB with `jarsigner`/`keytool`, and install the exact hashed APK on a clean competition device.
8. Preserve the final set in the workflow artifact plus approved retained storage. Record a sanitized storage record ID, not a vault path or secret locator.

Example independent checks from inside the downloaded artifact directory:

```bash
sha256sum --check SHA256SUMS
apksigner verify --verbose --print-certs liftrix-*.apk
aapt dump badging liftrix-*.apk
jarsigner -verify liftrix-*.aab
keytool -printcert -jarfile liftrix-*.aab
```

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

After rollback, repeat the relevant smoke checks and attach a new receipt. State explicitly that code/rules rollback cannot undo already-written data. Android reinstall or downgrade changes the binary only; clearing app data or uninstalling may discard unsynced local Room data and must be a separate, explicit recovery decision.

## Sanitized release receipt

Complete this record outside the source tree or under ignored `.release-receipts/`. Attach the workflow-generated manifest rather than transcribing hashes by hand.

| Field | Value |
| --- | --- |
| Containment date / responsible role | |
| Admin credential status / audit status | `revoked-audited` / |
| Signer classification / OAuth-App Check update status | |
| Sanitized containment-evidence SHA-256 | |
| Frozen commit / release tag | |
| Workflow run URL / attempt | |
| Package / version code / version name | |
| Certificate SHA-256 / SHA-1 | |
| APK / AAB / mapping / manifest / SHA256SUMS verified | |
| Final artifact URL / sanitized retained-storage record ID | |
| Current/previous Android manifest digests and commits / previous Firebase ref | |
| Capacity available / required | / 20 GiB |
| Explicit Firebase project / CLI revision / deployed revision | |
| Firestore export / Storage recovery record IDs | |
| Deploy selections / ordered results | |
| Smoke result / rollback tabletop result | |
| Primary-device install / spare-device install | |

The current Android revision is the attached manifest's commit and version. The previous Android revision is its approved predecessor manifest. The current Firebase revision is the deployed receipt commit/tag; the previous revision is `previous_backend_ref`. Never infer these from a filename alone.

## Final evidence checklist

- [ ] Frozen commit and release tag
- [ ] Sanitized credential containment and signer-classification gate
- [ ] 20 GiB capacity floor passed without deleting the last known-good set
- [ ] Two consecutive clean CI release runs completed; one final set designated
- [ ] Explicit Firebase project and previous backend baseline
- [ ] Signed APK, AAB, exact R8 mapping, `release-manifest.json`, and `SHA256SUMS`
- [ ] APK package/version/signature verification
- [ ] Canonical certificate SHA-256 and SHA-1 fingerprints
- [ ] 90-day artifact retention confirmed
- [ ] Firebase export and Storage recovery evidence
- [ ] Ordered deploy receipt and CLI revision
- [ ] Consent cold-start evidence
- [ ] Smoke evidence and rollback rehearsal/commands
- [ ] Exact final APK installed on both presentation devices
- [ ] No rebuild or source/config change after manifest freeze
