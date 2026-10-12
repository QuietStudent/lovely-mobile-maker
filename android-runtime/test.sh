#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_CLASSES="$ROOT/.build/test-classes"
mkdir -p "$TEST_CLASSES"
javac -encoding UTF-8 -d "$TEST_CLASSES" "$ROOT/android-runtime/java/org/love2d/android/ArchiveStore.java" \
  "$ROOT/android-runtime/java/org/love2d/android/Downloads.java" \
  "$ROOT/android-runtime/java/org/love2d/android/ModFiles.java" \
  "$ROOT/android-runtime/tests/org/love2d/android/"*.java
java -cp "$TEST_CLASSES" org.love2d.android.ArchiveStoreTest
java -cp "$TEST_CLASSES" org.love2d.android.DownloadsTest
java -cp "$TEST_CLASSES" org.love2d.android.ModFilesTest
