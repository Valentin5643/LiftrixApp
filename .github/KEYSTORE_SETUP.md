# Keystore Setup Guide for CI/CD

This guide explains how to configure keystore credentials for both local development and GitHub Actions CI/CD.

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

### Security Best Practices

1. **Never commit keystore files** to version control
2. **Never commit passwords** in plain text
3. **Rotate credentials** if accidentally exposed
4. **Limit access** to GitHub repository secrets
5. **Use separate keystores** for debug and release builds
6. **Backup keystore** in secure offline location

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
1. Immediately revoke/rotate all credentials
2. Generate new keystore (if keystore was exposed)
3. Update all GitHub Secrets with new values
4. Use `git filter-branch` or BFG Repo-Cleaner to remove from history

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
3. **Decode keystore** from KEYSTORE_BASE64 secret
4. **Build release APK** using environment variables
5. **Run unit tests** with coverage report
6. **Upload artifacts** (APK, test results, coverage)
7. **Clean up** temporary keystore file

The workflow runs on:
- Push to `master` or `develop` branches
- Pull requests targeting `master` or `develop`

## Reference

- [Android Developer - Sign your app](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions - Encrypted secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Gradle - Signing configurations](https://developer.android.com/studio/build/gradle-tips#sign-your-app)
