#!/usr/bin/env bash
# Max TV APK derleme betiği (Gradle gerektirmez).
# Gerekenler (Ubuntu/Debian): sudo apt install openjdk-17-jdk aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

echo "1/5 Kaynaklar (R.java)…"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"

echo "2/5 Java derleniyor…"
javac -nowarn --release 8 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v 'warning: \[options\]' || true
test -f "$OUT/classes/com/maxtv/app/MainActivity.class"

echo "3/5 DEX…"
dalvik-exchange --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes"

echo "4/5 APK paketleniyor…"
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "5/5 İmzalanıyor…"
if [ ! -f maxtv.keystore ]; then
  keytool -genkeypair -keystore maxtv.keystore -storepass maxtv123 -keypass maxtv123 \
    -alias maxtv -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Max TV" >/dev/null 2>&1
fi
apksigner sign --ks maxtv.keystore --ks-pass pass:maxtv123 --key-pass pass:maxtv123 \
  --out MaxTV.apk "$OUT/aligned.apk"
apksigner verify MaxTV.apk && echo "Hazır: $(pwd)/MaxTV.apk"
