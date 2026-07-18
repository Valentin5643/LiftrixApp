# Keystore Setup Guide for CI/CD

This guide explains how to configure the canonical release signing identity and Firebase Android configuration for local development and GitHub Actions CI/CD.

## Overview

The app uses a dual-configuration system for keystore credentials:
- **Local Development**: Credentials stored in `local.properties` (gitignored)
- **CI/CD (GitHub Actions)**: Credentials stored as GitHub Secrets (encrypted)

## Local Development Setup

### 1. Create `local.properties` file

In the project root directory, create a `local.properties` file with the following content:

```properties
# Keystore configuration for local development
KEYSTORE_PATH=../liftrix-release.keystore
KEYSTORE_PASSWORD=your_keystore_password_here
KEY_ALIAS=liftrix
KEY_PASSWORD=your_key_password_here

# Google OAuth Client IDs (optional, for local testing)
GOOGLE_CLIENT_ID_DEBUG=your_debug_client_id
GOOGLE_CLIENT_ID_RELEASE=your_release_client_id
```

### 2. Place keystore file

Place your `liftrix-release.keystore` file in the project root directory (one level up from `app/`).

**IMPORTANT**: Never commit the keystore file or `local.properties` to version control. These are already listed in `.gitignore`.

## GitHub Actions CI/CD Setup

### Required GitHub Secrets

Configure the following secrets in your GitHub repository:

**Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Description | Example Value |
|------------|-------------|---------------|
| `KEYSTORE_BASE64` | Base64-encoded keystore file | (generated from keystore) |
| `KEYSTORE_PASSWORD` | Keystore password | `your_password` |
| `KEY_ALIAS` | Key alias in keystore | `liftrix` |
| `KEY_PASSWORD` | Key password | `your_key_password` |
| `GOOGLE_SERVICES_JSON_BASE64` | Base64-encoded Android Firebase configuration for package `com.liftrix.app` | Protected value; never print it |

`app/build.gradle.kts` is the single reviewable source for the release package and version. The workflow runs `:app:writeReleaseIdentity`, validates the generated nonsecret identity, and compares the signed APK with it. Do not duplicate package or version constants in the workflow. Increment the version in Gradle only after every code fix is frozen; any later code or configuration change invalidates the artifact set.

The release environment also requires the nonsecret Actions variable `RELEASE_CERT_SHA256`. The canonical certificate currently records SHA-256 `544162ace5fc6b03265f75db6f7cab5409c5981bd0097072d450f3ec01f548da` and SHA-1 `a3a0251d07bfe06d5dba72ee635eb7bfdf69cfb0`. Set the variable to the lowercase 64-character SHA-256 digest without separators. The workflow compares the signed APK and AAB identities to this value and records both fingerprints in the release manifest.

Restrict the secrets and variable to the protected competition release environment. Access, changes, and rotations are owned jointly by the release owner and repository administrator; require review before a workflow can read them.

The release job requires at least 20 GiB free in the checked-out workspace before any compile or release build begins. It logs only the available and required byte counts. If the preflight fails, reclaim capacity without deleting the last known-good retained artifact, then start a new clean run.

The workflow also bounds expensive lint/R8 work to two Gradle workers, gives the release daemon a 5 GiB heap and 1 GiB metaspace, and limits R8 to two workers. An out-of-memory or daemon-expiration event is a failed gate; do not upload artifacts from that run or bypass minification/lint to compensate.

### How to Generate KEYSTORE_BASE64

On Linux/macOS:
```bash
base64 -i liftrix-release.keystore | tr -d '\n' > keystore.txt
cat keystore.txt
# Copy the output and paste into GitHub Secret
```

On Windows (PowerShell):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("liftrix-release.keystore")) | Out-File -Encoding ASCII keystore.txt
Get-Content keystore.txt
# Copy the output and paste into GitHub Secret
```

Generate `GOOGLE_SERVICES_JSON_BASE64` the same way from the exact Firebase Android app configuration for `com.liftrix.app`. Do not save the encoded value in the repository, workflow, issue, or build log. CI decodes it directly to the ignored `app/google-services.json`, validates only the package name, and removes it in an always-run cleanup step.

### Record the Canonical Certificate

Derive the certificate fingerprint without exposing passwords in command arguments or logs:

```bash
keytool -list -v -keystore /secure/path/to/release.keystore -alias your_alias
```

Normalize the displayed SHA-256 fingerprint to lowercase hexadecimal without colons and store it as the `RELEASE_CERT_SHA256` Actions variable. The value in that protected variable is the canonical release identity; the workflow fails if the APK or AAB is signed by any other certificate.

Maintain two encrypted offline backups in separately controlled storage. The release owner is accountable for a quarterly restore check; the repository administrator is the recovery approver. Document only the storage category and custodian in release records, never a device path, vault locator, password, or key filename.

### Security Best Practices

1. **Never commit keystore files** to version control
2. **Never commit passwords** in plain text
3. **Rotate credentials** if accidentally exposed
4. **Limit access** to GitHub repository secrets
5. **Use separate keystores** for debug and release builds
6. **Backup the canonical keystore** in two encrypted offline locations with separate custody
7. **Review the canonical SHA-256 fingerprint** before changing `RELEASE_CERT_SHA256`
8. **Remove decoded CI inputs** in an always-run cleanup step

## Generating a New Keystore

If you need to create a new keystore:

```bash
keytool -genkey -v -keystore liftrix-release.keystore \
  -alias liftrix \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Follow the prompts to set passwords and certificate details.

**CRITICAL**: Store the keystore and passwords securely. Losing the keystore means you cannot update your app on Google Play!

## Credential containment and signer classification

If any Firebase Admin credential or signing material may have been exposed, stop releases and backend deploys. The release owner must first revoke/delete the Admin credential, audit IAM and relevant access logs, and create a least-privilege replacement only when automation still requires one. Store replacements only in the approved secret system.

Compare certificate fingerprints for historical keystores and distributed APKs without copying private key material into the repository or CI logs. If an exposed private key signed a distributed/current app identity, use the distribution provider's supported upload/signing-key rotation or reset procedure and update OAuth and App Check registrations in a controlled window. Do not substitute a newly generated local key or silently change `RELEASE_CERT_SHA256`.

The release receipt records only the containment date, responsible role, status `revoked-audited`, signer classification, and public certificate fingerprints. It must not contain credential IDs, secret values, vault paths, private audit paths, or history coordinates.

## Checking for Exposed Credentials

Verify no credentials were accidentally committed:

```bash
# Check git history for local.properties
git log --all --full-history -- local.properties

# Check git history for keystore files
git log --all --full-history -- "*.keystore"

# Search for potential password leaks
git log -p | grep -i "password"
```

If credentials were exposed:

1. Immediately revoke or disable the exposed credential and audit its use.
2. Classify signing impact from public certificate fingerprints and distributed artifacts.
3. Complete the supported rotation/reset path and dependent OAuth/App Check updates when signing impact requires it.
4. Update protected secrets only after the replacement is approved and least-privilege.
5. Consider coordinated history cleanup only after revocation. History rewriting is not containment and must not precede it.

## Troubleshooting

### Build fails with "keystore not found"

**Local Development**:
- Verify `local.properties` exists in project root
- Check `KEYSTORE_PATH` points to correct file location
- Ensure keystore file exists at specified path

**GitHub Actions**:
- Verify `KEYSTORE_BASE64` secret is set correctly
- Check workflow logs for keystore decoding errors
- Ensure base64 encoding was done without line breaks

### "Failed to read key from keystore"

- Verify `KEY_ALIAS` matches the alias in your keystore
- Check `KEYSTORE_PASSWORD` is correct
- Verify `KEY_PASSWORD` is correct
- List keystore contents to verify alias:
  ```bash
  keytool -list -v -keystore liftrix-release.keystore
  ```

### GitHub Actions workflow fails

- Check the workflow run logs in GitHub Actions tab
- Verify all required secrets are configured
- Ensure base64 decoding succeeded in workflow logs
- Verify JDK version matches requirement (JDK 17)

## CI/CD Workflow Overview

The GitHub Actions workflow (`.github/workflows/android.yml`) performs:

1. **Checkout code** from repository
2. **Set up JDK 17** with Gradle caching
3. **Decode the Firebase configuration and canonical keystore** into protected temporary files
4. **Check capacity and validate configuration/signing identity** against the canonical Gradle identity and protected certificate variable
5. **Gate publication** on debug compilation, Room validation, aggregate debug unit tests, `lintFull`, and aggregate `build`
6. **Build the release APK and AAB** using protected signing inputs
7. **Verify package, version, and signing certificate identity** and generate hashes
8. **Upload the release set and verification manifest** only after every gate passes
9. **Clean up** both protected temporary inputs even when an earlier step fails

The uploaded `liftrix-release-<commit>` artifact is retained for 90 days and contains the universal signed APK, signed AAB, exact R8 mapping, `release-manifest.json`, and `SHA256SUMS`. Treat the manifest as the identity record; do not infer provenance from filenames alone.

The workflow runs on:
- Push to `master` or `develop` branches
- Pull requests targeting `master` or `develop`

## Reference

- [Android Developer - Sign your app](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions - Encrypted secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Gradle - Signing configurations](https://developer.android.com/studio/build/gradle-tips#sign-your-app)
