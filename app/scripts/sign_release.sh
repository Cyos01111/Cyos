#!/usr/bin/env bash
set -euo pipefail

# Find Android SDK build tools apksigner
APKSIGNER=$(find "${ANDROID_SDK_ROOT:-/opt/android/sdk}"/build-tools -name apksigner | sort -V | tail -n1)

if [ -z "$APKSIGNER" ]; then
    echo "Error: apksigner not found in ANDROID_SDK_ROOT."
    exit 1
fi

KEYSTORE_PATH="${CYOSPLAY_RELEASE_KEYSTORE_PATH:-cyosplay-release.keystore}"
KEY_ALIAS="${CYOSPLAY_RELEASE_KEY_ALIAS:-cyosplay_release}"

if [ -z "${CYOSPLAY_RELEASE_STORE_PASSWORD:-}" ]; then
    echo "Error: CYOSPLAY_RELEASE_STORE_PASSWORD environment variable must be set (never hardcode keystore passwords)."
    exit 1
fi

export CYOSPLAY_RELEASE_KEY_PASSWORD="${CYOSPLAY_RELEASE_KEY_PASSWORD:-$CYOSPLAY_RELEASE_STORE_PASSWORD}"

INPUT_APK="${1:-app/build/outputs/apk/release/app-release.apk}"
OUTPUT_APK="${2:-app/build/outputs/apk/release/cyosplay-release-signed.apk}"

if [ ! -f "$INPUT_APK" ]; then
    echo "Input APK not found at $INPUT_APK. Building release APK first..."
    gradle :app:assembleRelease
fi

echo "Signing $INPUT_APK with $KEYSTORE_PATH ($KEY_ALIAS)..."
"$APKSIGNER" sign \
    --ks "$KEYSTORE_PATH" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "env:CYOSPLAY_RELEASE_STORE_PASSWORD" \
    --key-pass "env:CYOSPLAY_RELEASE_KEY_PASSWORD" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUTPUT_APK" \
    "$INPUT_APK"

echo "Verifying signatures..."
"$APKSIGNER" verify --verbose --print-certs "$OUTPUT_APK"
echo "Successfully created signed release APK at $OUTPUT_APK"
