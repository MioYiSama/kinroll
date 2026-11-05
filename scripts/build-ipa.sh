#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
KINROLL_IOS_DEVICE_ONLY=1 scripts/gen-ios-project.sh
xcodebuild archive -project iosApp/Kinroll.xcodeproj -scheme Kinroll -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -archivePath build/ios/Kinroll.xcarchive CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""
rm -rf build/ios/unsigned
mkdir -p build/ios/unsigned/Payload dist
cp -R build/ios/Kinroll.xcarchive/Products/Applications/Kinroll.app build/ios/unsigned/Payload/
output="$PWD/dist/Kinroll-${VERSION:-0.1.1}-unsigned.ipa"
rm -f "$output"
(cd build/ios/unsigned && zip -qry "$output" Payload)
