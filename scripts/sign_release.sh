#!/usr/bin/env bash
set -euo pipefail

echo "Building and verifying CyosPlay 1.08rv6 release APK (assembleRelease + verifyReleaseSignatureAndEncryption)..."
gradle :app:assembleRelease

EXPORTED_APK="export/CyosPlay-1.08rv6-release.apk"
EXPORTED_SHA256="export/CyosPlay-1.08rv6-release.apk.sha256"

if [ ! -f "$EXPORTED_APK" ] || [ ! -f "$EXPORTED_SHA256" ]; then
    echo "Error: Verified release APK was not exported to $EXPORTED_APK."
    exit 1
fi

echo "Verified signed release APK exported to $EXPORTED_APK"
cat "$EXPORTED_SHA256"
