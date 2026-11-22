#!/usr/bin/env bash
set -euo pipefail

echo "Building and verifying signed release APK via Gradle (assembleRelease + verifyReleaseSignatureAndEncryption)..."
gradle :app:assembleRelease

ROOT_SIGNED_APK="CyosPlay-v1.08rv5-release-signed.apk"
INTEGRITY_ENVELOPE="app/build/outputs/apk/release/release-integrity.enc"

if [ ! -f "$ROOT_SIGNED_APK" ] || [ ! -f "$INTEGRITY_ENVELOPE" ]; then
    echo "Error: Verified release APK or encrypted integrity envelope not found."
    exit 1
fi

echo "Verified signed release APK ($ROOT_SIGNED_APK) and encrypted integrity envelope ($INTEGRITY_ENVELOPE)."
