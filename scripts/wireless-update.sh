#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME_DEFAULT="/Library/Java/JavaVirtualMachines/liberica-jdk-17.jdk/Contents/Home"
export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"

cd "$ROOT_DIR"
echo ">>> Perangkat ADB terhubung:"
adb devices

echo ">>> Building & installing MoonReader ke HP..."
./gradlew installDebug --no-daemon
echo ">>> Selesai! Aplikasi di HP sudah ter-update tanpa kehilangan data."
