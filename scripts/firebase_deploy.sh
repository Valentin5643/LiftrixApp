#!/usr/bin/env bash

set -Eeuo pipefail
IFS=$'\n\t'

usage() {
    cat <<'USAGE'
Usage:
  scripts/firebase_deploy.sh deploy --project PROJECT_ID --release-tag TAG \
    --previous-backend-ref REF --firestore-export gs://... \
    --storage-recovery EVIDENCE --android-manifest FILE \
    --previous-android-manifest FILE --containment-evidence FILE \
    --credential-status revoked-audited --containment-date YYYY-MM-DD \
    --containment-owner ROLE --signer-status STATUS \
    [--receipt-dir DIR] [--execute]

  scripts/firebase_deploy.sh smoke --project PROJECT_ID --release-tag TAG \
    --smoke-evidence FILE [--receipt-dir DIR]

  scripts/firebase_deploy.sh rollback --project PROJECT_ID --rollback-ref REF \
    --firestore-export gs://... --storage-recovery EVIDENCE \
    [--receipt-dir DIR] [--execute]

Without --execute, deploy and rollback validate inputs and print a dry-run plan.
Data restore is never automatic; Firestore export and Storage recovery values are evidence only.
USAGE
}

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_value() {
    local option="$1"
    local value="${2:-}"
    [[ -n "$value" && "$value" != --* ]] || fail "$option requires a value"
}

MODE="${1:-}"
[[ "$MODE" == "deploy" || "$MODE" == "smoke" || "$MODE" == "rollback" ]] || {
    usage >&2
    exit 2
}
shift

PROJECT_ID=""
RELEASE_TAG=""
PREVIOUS_BACKEND_REF=""
ROLLBACK_REF=""
FIRESTORE_EXPORT=""
STORAGE_RECOVERY=""
ANDROID_MANIFEST=""
PREVIOUS_ANDROID_MANIFEST=""
CONTAINMENT_EVIDENCE=""
CREDENTIAL_STATUS=""
CONTAINMENT_DATE=""
CONTAINMENT_OWNER=""
SIGNER_STATUS=""
SMOKE_EVIDENCE=""
RECEIPT_DIR=""
EXECUTE=false
MIN_FREE_GIB="${MIN_RELEASE_FREE_DISK_GIB:-20}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --project)
            require_value "$1" "${2:-}"
            PROJECT_ID="$2"
            shift 2
            ;;
        --release-tag)
            require_value "$1" "${2:-}"
            RELEASE_TAG="$2"
            shift 2
            ;;
        --previous-backend-ref)
            require_value "$1" "${2:-}"
            PREVIOUS_BACKEND_REF="$2"
            shift 2
            ;;
        --rollback-ref)
            require_value "$1" "${2:-}"
            ROLLBACK_REF="$2"
            shift 2
            ;;
        --firestore-export)
            require_value "$1" "${2:-}"
            FIRESTORE_EXPORT="$2"
            shift 2
            ;;
        --storage-recovery)
            require_value "$1" "${2:-}"
            STORAGE_RECOVERY="$2"
            shift 2
            ;;
        --android-manifest)
            require_value "$1" "${2:-}"
            ANDROID_MANIFEST="$2"
            shift 2
            ;;
        --previous-android-manifest)
            require_value "$1" "${2:-}"
            PREVIOUS_ANDROID_MANIFEST="$2"
            shift 2
            ;;
        --containment-evidence)
            require_value "$1" "${2:-}"
            CONTAINMENT_EVIDENCE="$2"
            shift 2
            ;;
        --credential-status)
            require_value "$1" "${2:-}"
            CREDENTIAL_STATUS="$2"
            shift 2
            ;;
        --containment-date)
            require_value "$1" "${2:-}"
            CONTAINMENT_DATE="$2"
            shift 2
            ;;
        --containment-owner)
            require_value "$1" "${2:-}"
            CONTAINMENT_OWNER="$2"
            shift 2
            ;;
        --signer-status)
            require_value "$1" "${2:-}"
            SIGNER_STATUS="$2"
            shift 2
            ;;
        --smoke-evidence)
            require_value "$1" "${2:-}"
            SMOKE_EVIDENCE="$2"
            shift 2
            ;;
        --receipt-dir)
            require_value "$1" "${2:-}"
            RECEIPT_DIR="$2"
            shift 2
            ;;
        --execute)
            EXECUTE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "unknown argument: $1"
            ;;
    esac
done

[[ -n "$PROJECT_ID" ]] || fail "--project is required; local/default Firebase targets are forbidden"
[[ "$PROJECT_ID" =~ ^[a-z0-9][a-z0-9-]{4,28}[a-z0-9]$ ]] || fail "--project is not a valid explicit Firebase project ID"

command -v git >/dev/null || fail "git is required"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || fail "run from a Git checkout"
cd "$REPO_ROOT"

[[ -z "$(git status --porcelain --untracked-files=all)" ]] \
    || fail "release operations require a clean frozen commit, including untracked files"

command -v df >/dev/null || fail "df is required for the capacity preflight"
[[ "$MIN_FREE_GIB" =~ ^[1-9][0-9]*$ ]] \
    || fail "MIN_RELEASE_FREE_DISK_GIB must be a positive integer"
(( MIN_FREE_GIB >= 20 )) \
    || fail "MIN_RELEASE_FREE_DISK_GIB cannot be lower than the 20 GiB release floor"
AVAILABLE_FREE_KIB="$(df -Pk "$REPO_ROOT" | awk 'END {print $4}')"
[[ "$AVAILABLE_FREE_KIB" =~ ^[0-9]+$ ]] || fail "unable to determine available repository capacity"
REQUIRED_FREE_KIB=$((MIN_FREE_GIB * 1024 * 1024))
printf 'Capacity preflight: %s KiB available; %s GiB required\n' "$AVAILABLE_FREE_KIB" "$MIN_FREE_GIB"
(( AVAILABLE_FREE_KIB >= REQUIRED_FREE_KIB )) \
    || fail "at least $MIN_FREE_GIB GiB free is required before release operations"

COMMIT_SHA="$(git rev-parse HEAD)"
TIMESTAMP="$(date -u +'%Y%m%dT%H%M%SZ')"
if [[ -z "$RECEIPT_DIR" ]]; then
    RECEIPT_DIR="$REPO_ROOT/.release-receipts/firebase/${TIMESTAMP}-${MODE}"
elif [[ "$RECEIPT_DIR" != /* ]]; then
    RECEIPT_DIR="$REPO_ROOT/$RECEIPT_DIR"
fi
mkdir -p "$RECEIPT_DIR"

if [[ "$MODE" == "smoke" ]]; then
    [[ -n "$RELEASE_TAG" ]] || fail "smoke requires --release-tag"
    [[ -n "$SMOKE_EVIDENCE" && -f "$SMOKE_EVIDENCE" ]] || fail "smoke requires an existing --smoke-evidence file"
    command -v firebase >/dev/null || fail "Firebase CLI is required"
    firebase projects:list --json \
        | jq -e --arg project "$PROJECT_ID" '.result[]? | select(.projectId == $project)' >/dev/null \
        || fail "Firebase CLI cannot access the explicit project"
    firebase functions:list --project "$PROJECT_ID" > "$RECEIPT_DIR/functions-list.txt"
    cp "$SMOKE_EVIDENCE" "$RECEIPT_DIR/smoke-evidence.md"
    printf '%s\n' \
        "mode=smoke" \
        "project=$PROJECT_ID" \
        "release_tag=$RELEASE_TAG" \
        "commit=$COMMIT_SHA" \
        "recorded_at_utc=$TIMESTAMP" \
        "firebase_cli=$(firebase --version)" \
        > "$RECEIPT_DIR/receipt.txt"
    printf 'Smoke evidence recorded in %s\n' "$RECEIPT_DIR"
    exit 0
fi

[[ "$FIRESTORE_EXPORT" == gs://* ]] || fail "--firestore-export must record a managed gs:// export URI"
[[ -n "$STORAGE_RECOVERY" ]] || fail "--storage-recovery evidence is required"
command -v jq >/dev/null || fail "jq is required"

FIRESTORE_RULES="$(jq -er '.firestore.rules' firebase.json)"
FIRESTORE_INDEXES="$(jq -er '.firestore.indexes' firebase.json)"
STORAGE_RULES="$(jq -er '.storage.rules' firebase.json)"
DATABASE_RULES="$(jq -er '.database.rules' firebase.json)"
FUNCTIONS_SOURCE="$(jq -er '.functions[0].source' firebase.json)"

[[ "$FIRESTORE_RULES" == "firestore.rules" ]] || fail "firebase.json must select root firestore.rules"
[[ "$FIRESTORE_INDEXES" == "firestore.indexes.json" ]] || fail "firebase.json must select root firestore.indexes.json"
[[ "$STORAGE_RULES" == "storage.rules" ]] || fail "firebase.json must select root storage.rules"
[[ "$DATABASE_RULES" == "database.rules.json" ]] || fail "firebase.json must select root database.rules.json"
[[ "$FUNCTIONS_SOURCE" == "functions" ]] || fail "firebase.json must select root functions source"
for source_path in "$FIRESTORE_RULES" "$FIRESTORE_INDEXES" "$STORAGE_RULES" "$DATABASE_RULES" "$FUNCTIONS_SOURCE"; do
    [[ -e "$source_path" ]] || fail "selected source does not exist: $source_path"
done

TARGET_REF=""
if [[ "$MODE" == "deploy" ]]; then
    [[ -n "$RELEASE_TAG" ]] || fail "deploy requires --release-tag"
    [[ -n "$PREVIOUS_BACKEND_REF" ]] || fail "deploy requires --previous-backend-ref"
    [[ "$CREDENTIAL_STATUS" == "revoked-audited" ]] \
        || fail "deploy requires --credential-status revoked-audited"
    [[ "$CONTAINMENT_DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
        || fail "deploy requires --containment-date YYYY-MM-DD"
    [[ "$CONTAINMENT_OWNER" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$ ]] \
        || fail "deploy requires a sanitized role token in --containment-owner"
    [[ "$SIGNER_STATUS" =~ ^(not-exposed|not-distributed|rotation-complete|reset-complete)$ ]] \
        || fail "deploy requires a completed --signer-status classification"
    [[ -n "$ANDROID_MANIFEST" && -f "$ANDROID_MANIFEST" ]] \
        || fail "deploy requires an existing --android-manifest"
    [[ -n "$PREVIOUS_ANDROID_MANIFEST" && -f "$PREVIOUS_ANDROID_MANIFEST" ]] \
        || fail "deploy requires an existing --previous-android-manifest"
    [[ -n "$CONTAINMENT_EVIDENCE" && -s "$CONTAINMENT_EVIDENCE" ]] \
        || fail "deploy requires nonempty sanitized --containment-evidence"
    command -v sha256sum >/dev/null || fail "sha256sum is required"

    ANDROID_COMMIT="$(jq -er '.commit_sha' "$ANDROID_MANIFEST")"
    ANDROID_PACKAGE="$(jq -er '.package_id' "$ANDROID_MANIFEST")"
    ANDROID_VERSION_CODE="$(jq -er '.version_code' "$ANDROID_MANIFEST")"
    ANDROID_VERSION_NAME="$(jq -er '.version_name' "$ANDROID_MANIFEST")"
    ANDROID_CERT_SHA256="$(jq -er '.signing_certificate.sha256' "$ANDROID_MANIFEST")"
    jq -e '
      (.artifacts.apk.sha256 | type == "string" and test("^[0-9a-f]{64}$")) and
      (.artifacts.aab.sha256 | type == "string" and test("^[0-9a-f]{64}$")) and
      (.artifacts.mapping.sha256 | type == "string" and test("^[0-9a-f]{64}$"))
    ' "$ANDROID_MANIFEST" >/dev/null
    [[ "$ANDROID_COMMIT" == "$COMMIT_SHA" ]] \
        || fail "Android release manifest commit does not match the frozen checkout"
    [[ "$ANDROID_PACKAGE" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] \
        || fail "Android release manifest package is invalid"
    [[ "$ANDROID_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] \
        || fail "Android release manifest version code is invalid"
    [[ "$ANDROID_VERSION_NAME" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]] \
        || fail "Android release manifest version name is invalid"
    [[ "$ANDROID_CERT_SHA256" =~ ^[0-9a-f]{64}$ ]] \
        || fail "Android release manifest certificate SHA-256 is invalid"

    PREVIOUS_ANDROID_COMMIT="$(jq -er '.commit_sha' "$PREVIOUS_ANDROID_MANIFEST")"
    PREVIOUS_ANDROID_PACKAGE="$(jq -er '.package_id' "$PREVIOUS_ANDROID_MANIFEST")"
    PREVIOUS_ANDROID_VERSION_CODE="$(jq -er '.version_code' "$PREVIOUS_ANDROID_MANIFEST")"
    PREVIOUS_ANDROID_VERSION_NAME="$(jq -er '.version_name' "$PREVIOUS_ANDROID_MANIFEST")"
    PREVIOUS_ANDROID_CERT_SHA256="$(jq -er '.signing_certificate.sha256' "$PREVIOUS_ANDROID_MANIFEST")"
    jq -e '
      (.artifacts.apk.sha256 | type == "string" and test("^[0-9a-f]{64}$")) and
      (.artifacts.aab.sha256 | type == "string" and test("^[0-9a-f]{64}$")) and
      (.artifacts.mapping.sha256 | type == "string" and test("^[0-9a-f]{64}$"))
    ' "$PREVIOUS_ANDROID_MANIFEST" >/dev/null
    [[ "$PREVIOUS_ANDROID_COMMIT" =~ ^[0-9a-f]{40}$ ]] \
        || fail "Previous Android manifest commit is invalid"
    [[ "$PREVIOUS_ANDROID_PACKAGE" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] \
        || fail "Previous Android manifest package is invalid"
    [[ "$PREVIOUS_ANDROID_PACKAGE" == "$ANDROID_PACKAGE" ]] \
        || fail "Previous Android manifest package does not match the current rollback identity"
    [[ "$PREVIOUS_ANDROID_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] \
        || fail "Previous Android manifest version code is invalid"
    [[ "$PREVIOUS_ANDROID_VERSION_NAME" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]] \
        || fail "Previous Android manifest version name is invalid"
    [[ "$PREVIOUS_ANDROID_CERT_SHA256" =~ ^[0-9a-f]{64}$ ]] \
        || fail "Previous Android manifest certificate SHA-256 is invalid"

    PREVIOUS_BACKEND_COMMIT="$(git rev-parse --verify "${PREVIOUS_BACKEND_REF}^{commit}" 2>/dev/null)" \
        || fail "previous backend ref does not exist: $PREVIOUS_BACKEND_REF"
    if git rev-parse --verify "${RELEASE_TAG}^{commit}" >/dev/null 2>&1; then
        [[ "$(git rev-parse "${RELEASE_TAG}^{commit}")" == "$COMMIT_SHA" ]] \
            || fail "release tag already points to a different commit"
    fi
    TARGET_REF="$RELEASE_TAG"
else
    [[ -n "$ROLLBACK_REF" ]] || fail "rollback requires --rollback-ref"
    TARGET_REF="$(git rev-parse --verify "${ROLLBACK_REF}^{commit}" 2>/dev/null)" \
        || fail "rollback ref does not exist: $ROLLBACK_REF"
    [[ "$COMMIT_SHA" == "$TARGET_REF" ]] \
        || fail "checkout the rollback ref in a clean worktree before executing rollback"
fi

cat > "$RECEIPT_DIR/receipt.txt" <<RECEIPT
mode=$MODE
project=$PROJECT_ID
commit=$COMMIT_SHA
target_ref=$TARGET_REF
previous_backend_ref=$PREVIOUS_BACKEND_REF
previous_backend_commit=${PREVIOUS_BACKEND_COMMIT:-}
firestore_export=$FIRESTORE_EXPORT
storage_recovery=$STORAGE_RECOVERY
recorded_at_utc=$TIMESTAMP
execute=$EXECUTE
sources=$FIRESTORE_INDEXES,$FUNCTIONS_SOURCE,$FIRESTORE_RULES,$STORAGE_RULES,$DATABASE_RULES
minimum_free_gib=$MIN_FREE_GIB
available_free_kib=$AVAILABLE_FREE_KIB
credential_status=$CREDENTIAL_STATUS
containment_date=$CONTAINMENT_DATE
containment_owner=$CONTAINMENT_OWNER
signer_status=$SIGNER_STATUS
RECEIPT

if [[ "$MODE" == "deploy" ]]; then
    ANDROID_MANIFEST_SHA256="$(sha256sum "$ANDROID_MANIFEST" | cut -d ' ' -f 1)"
    PREVIOUS_ANDROID_MANIFEST_SHA256="$(sha256sum "$PREVIOUS_ANDROID_MANIFEST" | cut -d ' ' -f 1)"
    CONTAINMENT_EVIDENCE_SHA256="$(sha256sum "$CONTAINMENT_EVIDENCE" | cut -d ' ' -f 1)"
    cp "$ANDROID_MANIFEST" "$RECEIPT_DIR/android-release-manifest.json"
    cp "$PREVIOUS_ANDROID_MANIFEST" "$RECEIPT_DIR/previous-android-release-manifest.json"
    printf '%s\n' \
        "android_manifest_sha256=$ANDROID_MANIFEST_SHA256" \
        "android_package=$ANDROID_PACKAGE" \
        "android_version_code=$ANDROID_VERSION_CODE" \
        "android_version_name=$ANDROID_VERSION_NAME" \
        "android_certificate_sha256=$ANDROID_CERT_SHA256" \
        "previous_android_manifest_sha256=$PREVIOUS_ANDROID_MANIFEST_SHA256" \
        "previous_android_commit=$PREVIOUS_ANDROID_COMMIT" \
        "previous_android_package=$PREVIOUS_ANDROID_PACKAGE" \
        "previous_android_version_code=$PREVIOUS_ANDROID_VERSION_CODE" \
        "previous_android_version_name=$PREVIOUS_ANDROID_VERSION_NAME" \
        "previous_android_certificate_sha256=$PREVIOUS_ANDROID_CERT_SHA256" \
        "containment_evidence_sha256=$CONTAINMENT_EVIDENCE_SHA256" \
        >> "$RECEIPT_DIR/receipt.txt"
fi

cat > "$RECEIPT_DIR/smoke-checklist.md" <<'SMOKE'
# Post-deploy smoke evidence

- [ ] Existing authenticated account can sign in.
- [ ] New account/onboarding path reaches the authenticated shell.
- [ ] Room-first read and one queued write reconcile successfully.
- [ ] Profile image read/upload follows the public/owner authorization boundary.
- [ ] One App Check-protected callable succeeds from the signed release app.
- [ ] A forbidden cross-user read/write is denied.
- [ ] Analytics, Crashlytics, and Performance remain off without consent and enable after stored consent.
- [ ] Firebase CLI deploy result/revision and Android release manifest are attached.

Record results, operator, UTC time, device/build identity, and links; then run the script's `smoke` mode.
SMOKE

printf 'Mode: %s\nProject: %s\nCommit: %s\nSources: %s\n' \
    "$MODE" "$PROJECT_ID" "$COMMIT_SHA" \
    "$FIRESTORE_INDEXES, $FUNCTIONS_SOURCE, $FIRESTORE_RULES, $STORAGE_RULES, $DATABASE_RULES"

if [[ "$EXECUTE" != true ]]; then
    printf '%s\n' \
        "Dry run only. With --execute the script will:" \
        "1. Verify Firebase CLI access to --project $PROJECT_ID." \
        "2. Record the Firebase CLI version and protected source tag/ref." \
        "3. Deploy functions, indexes, Firestore rules, Storage rules, then Realtime Database rules." \
        "4. Save command output and require separate smoke evidence before promotion." \
        "Hosting and data restore are excluded."
    exit 0
fi

command -v firebase >/dev/null || fail "Firebase CLI is required"
firebase projects:list --json \
    | jq -e --arg project "$PROJECT_ID" '.result[]? | select(.projectId == $project)' >/dev/null \
    || fail "Firebase CLI cannot access the explicit project"

if [[ "$MODE" == "deploy" ]]; then
    if ! git rev-parse --verify "${RELEASE_TAG}^{commit}" >/dev/null 2>&1; then
        git tag -a "$RELEASE_TAG" "$COMMIT_SHA" -m "Firebase deployment source $PROJECT_ID at $TIMESTAMP"
    fi
    git push origin "refs/tags/$RELEASE_TAG"
fi

printf 'firebase_cli=%s\n' "$(firebase --version)" >> "$RECEIPT_DIR/receipt.txt"
DEPLOY_LOG="$RECEIPT_DIR/firebase-deploy.log"

deploy_component() {
    local component="$1"
    printf 'Deploying %s to explicit project %s\n' "$component" "$PROJECT_ID" | tee -a "$DEPLOY_LOG"
    firebase deploy --project "$PROJECT_ID" --config firebase.json --only "$component" 2>&1 | tee -a "$DEPLOY_LOG"
}

deploy_component functions
deploy_component firestore:indexes
deploy_component firestore:rules
deploy_component storage
deploy_component database

firebase functions:list --project "$PROJECT_ID" > "$RECEIPT_DIR/functions-list.txt"
printf 'deployment_completed_at_utc=%s\n' "$(date -u +'%Y%m%dT%H%M%SZ')" >> "$RECEIPT_DIR/receipt.txt"
printf 'Deployment completed. Complete %s and record it with smoke mode before promotion.\n' \
    "$RECEIPT_DIR/smoke-checklist.md"
