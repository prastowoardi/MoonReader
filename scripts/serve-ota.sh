#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${1:-8787}"
JAVA_HOME_DEFAULT="/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home"
export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"

cd "$ROOT_DIR"
echo ">>> Building debug APK..."
./gradlew assembleDebug --no-daemon

APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
IP="$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo '127.0.0.1')"
STAGE_DIR="$ROOT_DIR/build/ota-serve"
mkdir -p "$STAGE_DIR"
cp "$APK_PATH" "$STAGE_DIR/app-debug.apk"

TS="$(date +%s)"
cat > "$STAGE_DIR/version.json" <<EOF
{
  "versionCode": 2,
  "versionName": "1.0.0-$TS",
  "apkUrl": "http://$IP:$PORT/app-debug.apk",
  "changelog": "Build lokal dari Mac ($(date '+%d %b %Y %H:%M'))",
  "buildTimestamp": $TS
}
EOF

echo ""
echo "=================================================="
echo "MoonReader WiFi OTA Server siap!"
echo "Di HP (buka MoonReader -> Lainnya -> Pembaruan Aplikasi):"
echo "  URL Update : http://$IP:$PORT/version.json"
echo "Atau buka langsung di browser HP:"
echo "  http://$IP:$PORT/app-debug.apk"
echo "Tekan Ctrl+C untuk berhenti."
echo "=================================================="
python3 -m http.server "$PORT" --directory "$STAGE_DIR"
