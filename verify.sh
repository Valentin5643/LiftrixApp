#!/usr/bin/env bash
set -euo pipefail

# Default verification script for an Android/Kotlin Gradle app.
# If AGENTS.md defines a different verification command, AGENTS.md wins.
# If your module is not :app, edit these tasks before using this script.

if [ ! -f "./gradlew" ]; then
  echo "ERROR: ./gradlew not found. Run this from the repo root or update scripts/verify.sh."
  exit 1
fi

./gradlew --no-daemon \
  :app:lintDebug \
  :app:compileDebugKotlin \
  :app:testDebugUnitTest
