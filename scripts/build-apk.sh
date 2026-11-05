#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :androidApp:assembleRelease
mkdir -p dist
cp androidApp/build/outputs/apk/release/androidApp-release.apk "dist/Kinroll-${VERSION:-0.1.0}.apk"
