#!/usr/bin/env bash
# Build the TicMouse Phone companion APK without Gradle, using SDK tools directly.
# Requires: JDK 17 (~/jdk17), Android SDK platform android-34 + build-tools 34.0.0 (~/android-sdk)
# NOTE: AndroidManifest.xml must keep <uses-sdk>; without it the APK has no
# targetSdk and devices reject the install (watch 0.4-0.4.3 lesson).
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/jdk17/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
OUT="app/build-output"
WORK="$OUT/work"
mkdir -p "$WORK"
rm -rf "$WORK/classes" "$WORK/dex" "$WORK/res-compiled"
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/res-compiled"

echo "[1/7] javac"
find app/src/main/java -name '*.java' > "$WORK/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.txt"

echo "[2/7] d8 (dex)"
"$BT/d8" --lib "$ANDROID_JAR" --output "$WORK/dex" $(find "$WORK/classes" -name '*.class')

echo "[3/7] aapt2 compile (res)"
"$BT/aapt2" compile --dir app/src/main/res -o "$WORK/res-compiled/res.zip"

echo "[4/7] aapt2 link"
"$BT/aapt2" link -o "$WORK/app-unsigned.apk" -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml "$WORK/res-compiled/res.zip"

echo "[5/7] add classes.dex + zipalign"
( cd "$WORK/dex" && zip -q "../app-unsigned.apk" classes.dex )
"$BT/zipalign" -f 4 "$WORK/app-unsigned.apk" "$WORK/app-aligned.apk"

echo "[6/7] debug keystore (shared with the watch app)"
KS="../app/build-output/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

echo "[7/7] apksigner"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/ticmouse-phone.apk" "$WORK/app-aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/ticmouse-phone.apk"
echo "DONE: $OUT/ticmouse-phone.apk"
