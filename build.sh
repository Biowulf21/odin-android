#!/bin/sh
set -eu
cd "$(dirname "$0")"
: "${PLUS_LENS_KEYSTORE_PASSWORD:?Set PLUS_LENS_KEYSTORE_PASSWORD to your signing keystore password}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
export ANDROID_HOME="$SDK"
TOOLS="$SDK/build-tools/36.0.0"
sh test.sh
sh test-adb-stream.sh
./gradlew --no-daemon assembleRelease
"$TOOLS/zipalign" -f -p 4 build/outputs/apk/release/Odin-release-unsigned.apk build/odin-aligned.apk
KEY="${PLUS_LENS_KEYSTORE:-../plus-lens-signing.p12}"
if [ ! -f "$KEY" ]; then
    keytool -genkeypair -keystore "$KEY" -storetype PKCS12 -storepass:env PLUS_LENS_KEYSTORE_PASSWORD -alias pluslens -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Odin Personal Build" -noprompt
fi
"$TOOLS/apksigner" sign --ks "$KEY" --ks-pass env:PLUS_LENS_KEYSTORE_PASSWORD --ks-key-alias pluslens --out build/Odin.apk build/odin-aligned.apk
"$TOOLS/apksigner" verify --verbose build/Odin.apk
echo "Built build/Odin.apk"
