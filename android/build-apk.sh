#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${XPARTY_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
: "${ANDROID_HOME:?Set XPARTY_ANDROID_SDK or ANDROID_HOME to the Android SDK}"
: "${XPARTY_KEYSTORE:?Set XPARTY_KEYSTORE to the original private release keystore}"
: "${XPARTY_STORE_PASS:?Set XPARTY_STORE_PASS to its password}"
chmod +x gradlew
./gradlew :app:assembleRelease --no-daemon --console=plain
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk build/Xparty-1.2.0.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose build/Xparty-1.2.0.apk
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 build/Xparty-1.2.0.apk
