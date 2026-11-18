#!/bin/sh
set -eu
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
ANDROID="$SDK/platforms/android-37.0/android.jar"
mkdir -p build/vendor-test
find vendor/libadb/src -name '*.java' > build/vendor-test-sources.txt
javac -classpath "$ANDROID:deps/*" -d build/vendor-test @build/vendor-test-sources.txt tests/AdbStreamTest.java
java -cp "build/vendor-test:$ANDROID:deps/*" io.github.muntashirakon.adb.AdbStreamTest
