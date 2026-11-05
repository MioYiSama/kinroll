#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
work="$root/build/ios/llama-b12121"
output="$root/iosApp/Frameworks/llama.xcframework"

# The upstream b12121 binary has an iPhoneOS slice but no iOS Simulator slice.
# Keep its device binary unchanged; build the missing CPU simulator slice from the same tag.
if [[ -f "$output/Info.plist" ]]; then
    if [[ "${KINROLL_IOS_DEVICE_ONLY:-0}" == 1 || -d "$output/ios-arm64-simulator" ]]; then exit 0; fi
fi
mkdir -p "$work" "$root/iosApp/Frameworks"
archive="$work/llama-b12121-xcframework.zip"
if [[ ! -f "$archive" ]]; then
    curl -fL https://github.com/ggml-org/llama.cpp/releases/download/b12121/llama-b12121-xcframework.zip -o "$archive"
fi
printf '%s  %s\n' '00bc6fec00beba802a3912a6afb81e1678da5248a6a92548a9ce66724a3e7638' "$archive" | shasum -a 256 -c -
if [[ ! -d "$work/official/build-apple/llama.xcframework" ]]; then
    mkdir -p "$work/official"
    unzip -q "$archive" -d "$work/official"
fi
official="$work/official/build-apple/llama.xcframework/ios-arm64/llama.framework"
frameworks=(-framework "$official")

if [[ "${KINROLL_IOS_DEVICE_ONLY:-0}" != 1 ]]; then
    source="$root/third_party/llama.cpp"
    if [[ ! -f "$source/CMakeLists.txt" ]] || [[ "$(git -C "$source" describe --tags --exact-match 2>/dev/null || true)" != b12121 ]]; then
        source="$work/source"
        if [[ ! -f "$source/CMakeLists.txt" ]]; then
            git clone --depth 1 --branch b12121 https://github.com/ggml-org/llama.cpp.git "$source"
        fi
    fi
    build="$work/simulator-build"
    cmake -S "$source" -B "$build" -G Xcode \
        -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT=iphonesimulator \
        -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=17.0 \
        -DCMAKE_XCODE_ATTRIBUTE_SUPPORTED_PLATFORMS=iphonesimulator \
        -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED=NO \
        -DBUILD_SHARED_LIBS=OFF -DGGML_BACKEND_DL=OFF \
        -DGGML_CPU=ON -DGGML_METAL=OFF -DGGML_BLAS=ON \
        -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
        -DLLAMA_BUILD_APP=OFF -DLLAMA_BUILD_COMMON=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_TOOLS=OFF \
        -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_SERVER=OFF \
        -DLLAMA_BUILD_MTMD=ON -DMTMD_VIDEO=OFF -DLLAMA_OPENSSL=OFF
    cmake --build "$build" --config Release --target mtmd --parallel "$(sysctl -n hw.ncpu)"
    simulator="$work/simulator/llama.framework"
    rm -rf "$work/simulator"
    mkdir -p "$work/simulator"
    cp -R "$official" "$simulator"
    rm -rf "$simulator/_CodeSignature"
    /usr/libexec/PlistBuddy -c 'Set :CFBundleSupportedPlatforms:0 iPhoneSimulator' "$simulator/Info.plist"
    /usr/libexec/PlistBuddy -c 'Set :DTPlatformName iphonesimulator' "$simulator/Info.plist"
    /usr/libexec/PlistBuddy -c "Set :DTSDKName iphonesimulator$(xcrun --sdk iphonesimulator --show-sdk-version)" "$simulator/Info.plist"
    release=Release-iphonesimulator
    xcrun libtool -static -o "$work/combined.a" \
        "$build/src/$release/libllama.a" \
        "$build/ggml/src/$release/libggml.a" \
        "$build/ggml/src/$release/libggml-base.a" \
        "$build/ggml/src/$release/libggml-cpu.a" \
        "$build/ggml/src/ggml-blas/$release/libggml-blas.a" \
        "$build/tools/mtmd/$release/libmtmd.a" \
        "$build/vendor/hash/$release/libvendor-hash.a"
    xcrun --sdk iphonesimulator clang++ -dynamiclib \
        -isysroot "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
        -arch arm64 -mios-simulator-version-min=17.0 \
        -Wl,-force_load,"$work/combined.a" \
        -framework Foundation -framework Metal -framework Accelerate \
        -install_name '@rpath/llama.framework/llama' -o "$simulator/llama"
    frameworks+=(-framework "$simulator")
fi
rm -rf "$output"
xcodebuild -create-xcframework "${frameworks[@]}" -output "$output"
