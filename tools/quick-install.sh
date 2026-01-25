#!/usr/bin/env bash
# Quick install script that handles timeouts better
# Usage: tools/quick-install.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

APK="${PROJECT_DIR}/bin/trmnl-nook-simple-touch.apk"
DEVICE="192.168.1.236:5555"

# Find ADB - try common locations
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

echo "=== Installing APK to ${DEVICE} ==="
echo "APK: ${APK}"
echo "Size: $(du -h "${APK}" | cut -f1)"
echo ""

# Check if device is connected
echo "Checking device connection..."
"${ADB_BIN}" -s "${DEVICE}" wait-for-device
"${ADB_BIN}" -s "${DEVICE}" shell getprop ro.build.version.release || { echo "error: device not responding" >&2; exit 1; }
echo "Device connected ✓"
echo ""

# Method: push to temp, then install (avoids timeout)
TMP_PATH="/data/local/tmp/trmnl-install.apk"

echo "Step 1/3: Pushing APK to device (this may take 30-60 seconds)..."
"${ADB_BIN}" -s "${DEVICE}" push "${APK}" "${TMP_PATH}" || {
    echo "error: push failed" >&2
    exit 1
}
echo "Push complete ✓"
echo ""

echo "Step 2/3: Installing from device storage..."
"${ADB_BIN}" -s "${DEVICE}" shell pm install -r "${TMP_PATH}" || {
    echo "error: install failed" >&2
    "${ADB_BIN}" -s "${DEVICE}" shell rm -f "${TMP_PATH}" || true
    exit 1
}
echo "Install complete ✓"
echo ""

echo "Step 3/3: Cleaning up..."
"${ADB_BIN}" -s "${DEVICE}" shell rm -f "${TMP_PATH}"
echo "Cleanup complete ✓"
echo ""

echo "=== Installation successful! ==="
echo ""
echo "To launch the app, run:"
echo "  ${ADB_BIN} -s ${DEVICE} shell am start -n com.bpmct.trmnl_nook_simple_touch/.FullscreenActivity"
echo ""
echo "Or use Eclipse's Run button (it should detect the app is installed)."
