#!/usr/bin/env bash

set -Eeuo pipefail
IFS=$'\n\t'

usage() {
    cat <<'USAGE'
Usage:
  scripts/firebase_deploy.sh deploy --project PROJECT_ID --release-tag TAG \
    --previous-backend-ref REF --firestore-export gs://... \
    --storage-recovery EVIDENCE [--receipt-dir DIR] [--execute]

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
SMOKE_EVIDENCE=""
RECEIPT_DIR=""
EXECUTE=false

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

git diff --quiet -- . || fail "tracked source files have unstaged changes"
git diff --cached --quiet -- . || fail "tracked source files have staged changes"

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
    git rev-parse --verify "${PREVIOUS_BACKEND_REF}^{commit}" >/dev/null 2>&1 \
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
firestore_export=$FIRESTORE_EXPORT
storage_recovery=$STORAGE_RECOVERY
recorded_at_utc=$TIMESTAMP
execute=$EXECUTE
sources=$FIRESTORE_INDEXES,$FUNCTIONS_SOURCE,$FIRESTORE_RULES,$STORAGE_RULES,$DATABASE_RULES
RECEIPT

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
