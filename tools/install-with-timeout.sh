#!/usr/bin/env bash
# Install APK with better timeout handling for slow NOOK devices
# Usage: tools/install-with-timeout.sh [APK_PATH]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Default APK path
APK="${1:-${PROJECT_DIR}/bin/trmnl-nook-simple-touch.apk}"
DEVICE="${ADB_DEVICE:-192.168.1.236:5555}"

# Find ADB
if [[ -n "${ADB:-}" && -x "${ADB}" ]]; then
    ADB_BIN="${ADB}"
elif [[ -x "${HOME}/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/sdk/platform-tools/adb" ]]; then
    ADB_BIN="${HOME}/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/sdk/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
else
    echo "error: adb not found. Set ADB=/path/to/adb" >&2
    exit 1
fi

[[ -f "${APK}" ]] || { echo "error: APK not found: ${APK}" >&2; exit 1; }

echo "Installing ${APK} to ${DEVICE}..."
echo "Using method: push + pm install (faster for large APKs)"

# Method 1: Push to temp location, then install (often faster)
TMP_PATH="/data/local/tmp/trmnl-nook-simple-touch.apk"

echo "Step 1: Pushing APK to device..."
"${ADB_BIN}" -s "${DEVICE}" push "${APK}" "${TMP_PATH}"

echo "Step 2: Installing from device storage..."
"${ADB_BIN}" -s "${DEVICE}" shell pm install -r "${TMP_PATH}"

echo "Step 3: Cleaning up..."
"${ADB_BIN}" -s "${DEVICE}" shell rm -f "${TMP_PATH}"

echo "Installation complete!"
