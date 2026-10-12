#!/usr/bin/env bash
# Reproducible base APK build for Linux or GitHub Actions. Contains no Balatro files.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/.build"
ANDROID_SRC="$BUILD/android"
NDK="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}/ndk/25.2.9519653"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$TOOLCHAIN:$PATH"
mkdir -p "$BUILD" "$ROOT/dist"
if [[ ! -d "$ANDROID_SRC" ]]; then
  git clone https://github.com/WilsontheWolf/lmm-love-android.git "$ANDROID_SRC"
  git -C "$ANDROID_SRC" checkout c36e6685723bf4b85f04cf554a650af400fc50e0
  git -C "$ANDROID_SRC" submodule update --init --recursive
  node "$ROOT/android-runtime/apply-overlay.mjs" "$ANDROID_SRC"
fi

declare -A revisions=(
  [0.9.0]=91759da5702618c3b940fbbe8135954414c0ef34
  [0.10.0]=073060eeb81b3664f476bdd33c263663d48762d1
)
for version in 0.9.0 0.10.0; do
  source="$BUILD/lovely-$version"
  if [[ ! -d "$source" ]]; then
    git clone https://github.com/ethangreen-dev/lovely-injector.git "$source"
    git -C "$source" checkout "${revisions[$version]}"
  fi
  mkdir -p "$ANDROID_SRC/app/src/embed/assets/lmm/licenses" "$ROOT/dist/licenses"
  cp "$source/LICENSE.md" "$ANDROID_SRC/app/src/embed/assets/lmm/licenses/Lovely-$version.txt"
  cp "$source/LICENSE.md" "$ROOT/dist/licenses/Lovely-$version.txt"
  # Build only the patch core, without desktop detours or the Dobby dependency.
  bridge="$BUILD/mobile-$version"
  mkdir -p "$bridge/src"
  cp "$ROOT/android-runtime/native/mobile_lib.rs" "$bridge/src/lib.rs"
  cat > "$bridge/Cargo.toml" <<EOF
[package]
name = "lmm-lovely-mobile"
version = "$version"
edition = "2021"
[workspace]
[lib]
name = "lovely"
crate-type = ["cdylib"]
[dependencies]
lovely-core = { path = "$source/crates/lovely-core" }
once_cell = "1.19.0"
EOF
  for abi in arm64-v8a armeabi-v7a; do
    if [[ "$abi" == arm64-v8a ]]; then target=aarch64-linux-android; compiler=aarch64-linux-android21-clang;
    else target=armv7-linux-androideabi; compiler=armv7a-linux-androideabi21-clang; fi
    linker_var="CARGO_TARGET_$(echo "$target" | tr '[:lower:]-' '[:upper:]_')_LINKER"
    export "$linker_var=$TOOLCHAIN/$compiler"
    export "CC_${target//-/_}=$TOOLCHAIN/$compiler"
    export "AR_${target//-/_}=$TOOLCHAIN/llvm-ar"
    cargo +stable build --release --manifest-path "$bridge/Cargo.toml" --target "$target"
    destination="$ANDROID_SRC/app/src/embed/assets/lmm/lovely/$version/$abi"
    mkdir -p "$destination" "$ROOT/dist/lovely/$version/$abi"
    cp "$bridge/target/$target/release/liblovely.so" "$destination/liblovely.so"
    cp "$destination/liblovely.so" "$ROOT/dist/lovely/$version/$abi/liblovely.so"
  done
done
cp "$ANDROID_SRC/love/src/jni/LuaJIT-2.1/COPYRIGHT" "$ANDROID_SRC/app/src/embed/assets/lmm/licenses/LuaJIT.txt"
cp "$ANDROID_SRC/love/src/jni/love/license.txt" "$ANDROID_SRC/app/src/embed/assets/lmm/licenses/LOVE.txt"
cp "$ANDROID_SRC/app/src/embed/assets/lmm/licenses/"*.txt "$ROOT/dist/licenses/"
cp "$ROOT/LICENSE" "$ROOT/dist/licenses/LMM-AGPL-3.0.txt"

# Rebuild LuaJIT. Never reuse upstream archives with Lovely statically linked.
LUAJIT="$ANDROID_SRC/love/src/jni/LuaJIT-2.1"
for abi in arm64-v8a armeabi-v7a; do
  hostflags=""
  if [[ "$abi" == arm64-v8a ]]; then compiler=aarch64-linux-android21-clang; cross=aarch64-linux-android;
  else compiler=armv7a-linux-androideabi21-clang; cross=armv7a-linux-androideabi; hostflags="-m32"; fi
  make -C "$LUAJIT" clean
  make -C "$LUAJIT" -j2 HOST_CC="gcc $hostflags" HOST_CFLAGS=-D_CRT_SECURE_NO_WARNINGS \
    HOST_LUA=luajit CROSS="$cross-" STATIC_CC="$compiler -fPIC" DYNAMIC_CC="$compiler -fPIC" \
    TARGET_AR="llvm-ar rcus" TARGET_SYS=Linux TARGET_LD="$compiler" TARGET_LDFLAGS=-fuse-ld=lld \
    TARGET_LIBS="-ldl" TARGET_STRIP=llvm-strip BUILDMODE=static amalg
  mkdir -p "$LUAJIT/android/$abi"
  cp "$LUAJIT/src/libluajit.a" "$LUAJIT/android/$abi/libluajit.a"
  cp "$LUAJIT/src/luajit.h" "$LUAJIT/android/$abi/luajit.h"
done
cd "$ANDROID_SRC"
chmod +x gradlew
./gradlew --no-daemon assembleEmbedNoRecordRelease
cp app/build/outputs/apk/embedNoRecord/release/app-embed-noRecord-release.apk "$ROOT/dist/base.apk"
node "$ROOT/android-runtime/make-catalog.mjs" "${LOVELY_DOWNLOAD_BASE:-}" "$ROOT/dist"
echo "Built dist/base.apk. Put it beside index.html before using the web maker."
