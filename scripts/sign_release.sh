#!/usr/bin/env bash
set -euo pipefail

# Find Android SDK build tools apksigner
APKSIGNER=$(find "${ANDROID_SDK_ROOT:-/opt/android/sdk}"/build-tools -name apksigner | sort -V | tail -n1)

if [ -z "$APKSIGNER" ]; then
    echo "Error: apksigner not found in ANDROID_SDK_ROOT."
    exit 1
fi

KEYSTORE_PATH="cyosplay-release.keystore"
KEY_ALIAS="cyosplay_release"
STORE_PASS="cyosplay2026"
KEY_PASS="cyosplay2026"

INPUT_APK="${1:-app/build/outputs/apk/release/app-release.apk}"
ROOT_APK="cyosplay-1.03-release.apk"

echo "Building release APK via Gradle..."
gradle :app:assembleRelease

if [ ! -f "$INPUT_APK" ]; then
    echo "Error: Release APK not generated at $INPUT_APK"
    exit 1
fi

echo "Copying signed release APK to project root ($ROOT_APK)..."
cp "$INPUT_APK" "$ROOT_APK"

echo "Verifying release signatures on $INPUT_APK and $ROOT_APK..."
"$APKSIGNER" verify --verbose --print-certs "$INPUT_APK"

echo "Successfully verified signed release APK: $INPUT_APK and $ROOT_APK"
