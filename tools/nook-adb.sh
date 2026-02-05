#!/usr/bin/env bash
set -euo pipefail

# Source SDKMAN if available (for Java 8)
if [[ -f "${HOME}/.sdkman/bin/sdkman-init.sh" ]]; then
  set +u
  source "${HOME}/.sdkman/bin/sdkman-init.sh"
  set -u
fi

# NOOK Simple Touch helper for ADB-over-TCP workflows.
#
# Usage examples:
#   tools/nook-adb.sh connect --ip 192.168.1.50
#   tools/nook-adb.sh connect               # interactive (optionally scans)
#   tools/nook-adb.sh install --apk path/to/app.apk
#   tools/nook-adb.sh build-install-run     # runs ant debug, installs, launches
#   tools/nook-adb.sh logcat
#   tools/nook-adb.sh shell
#
# Notes:
# - This script assumes ADB-over-TCP (default port 5555) and does not enable TCP on the device.
# - You may need to run: chmod +x tools/nook-adb.sh

APP_PKG="com.bpmct.trmnl_nook_simple_touch"
APP_ACTIVITY=".DisplayActivity"
DEFAULT_LOGCAT_FILTER="TRMNLAPI:D BCHttpClient:D *:S"

DEFAULT_ADT_ADB="${HOME}/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/sdk/platform-tools/adb"
# NOTE: The Android SDK's `tools/ant/` is a *directory* containing build rules,
# not the Apache Ant executable. The `ant` binary is typically provided by your
# OS package manager (e.g. `apt install ant`) or a standalone Ant install.
DEFAULT_ADT_ANT="${HOME}/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

die() {
  echo "error: $*" >&2
  exit 1
}

have() { command -v "$1" >/dev/null 2>&1; }

pick_adb() {
  if [[ -n "${ADB:-}" ]]; then
    echo "${ADB}"
    return 0
  fi
  if [[ -x "${DEFAULT_ADT_ADB}" ]]; then
    echo "${DEFAULT_ADT_ADB}"
    return 0
  fi
  if have adb; then
    command -v adb
    return 0
  fi
  die "could not find adb. Set ADB=/path/to/adb or install adb in PATH."
}

ADB_BIN="$(pick_adb)"

pick_ant() {
  if [[ -n "${ANT:-}" ]]; then
    echo "${ANT}"
    return 0
  fi
  # `-x` is true for searchable directories; require an executable *file*.
  if [[ -f "${DEFAULT_ADT_ANT}" && -x "${DEFAULT_ADT_ANT}" ]]; then
    echo "${DEFAULT_ADT_ANT}"
    return 0
  fi
  if have ant; then
    command -v ant
    return 0
  fi
  return 1
}

ANT_BIN="$(pick_ant || true)"

ADB_HOSTPORT=""
DEVICE_IP="192.168.1.236"
DEVICE_PORT="5555"
APK_PATH=""
CIDR=""
DO_SCAN="0"
LOGCAT_FILTER="${LOGCAT_FILTER:-${DEFAULT_LOGCAT_FILTER}}"
BUILD_COMPILER="${BUILD_COMPILER:-modern}"
CLEAN_BUILD="0"

port_open() {
  local ip="$1"
  local port="$2"

  if have timeout; then
    timeout 1 bash -c "cat < /dev/null > /dev/tcp/${ip}/${port}" >/dev/null 2>&1
    return $?
  fi

  if have nc; then
    nc -z -w 1 "${ip}" "${port}" >/dev/null 2>&1
    return $?
  fi

  return 1
}

default_cidr() {
  # Pick the first global IPv4 address and assume /24.
  # Example ip output: "2: wlp3s0    inet 192.168.1.10/24 brd ... scope global ..."
  local line ip
  line="$(ip -o -4 addr show scope global 2>/dev/null | head -n 1 || true)"
  [[ -n "${line}" ]] || return 1
  ip="$(echo "${line}" | awk '{print $4}' | cut -d/ -f1)"
  [[ -n "${ip}" ]] || return 1
  echo "${ip%.*}.0/24"
}

discover_candidates() {
  local port="$1"
  local cidr_in="${2:-}"

  # Prefer nmap when available and we have a CIDR.
  if have nmap && [[ -n "${cidr_in}" ]]; then
    nmap -p "${port}" --open -n "${cidr_in}" 2>/dev/null \
      | awk '/Nmap scan report for/ {print $NF}'
    return 0
  fi

  # Otherwise, start from the ARP/neigh table (fast, no subnet scan).
  if have ip; then
    ip neigh show 2>/dev/null \
      | awk '{print $1}' \
      | while read -r ipaddr; do
          if port_open "${ipaddr}" "${port}"; then
            echo "${ipaddr}"
          fi
        done
    return 0
  fi

  return 0
}

prompt_select_ip() {
  local port="$1"
  local candidates=()
  local choice

  if [[ "${DO_SCAN}" == "1" ]]; then
    if [[ -z "${CIDR}" ]]; then
      CIDR="$(default_cidr || true)"
    fi

    echo "Scanning for ADB on port ${port}${CIDR:+ in ${CIDR}}..." >&2
    while IFS= read -r ip; do
      [[ -n "${ip}" ]] && candidates+=("${ip}")
    done < <(discover_candidates "${port}" "${CIDR}" | sort -u)
  fi

  if ((${#candidates[@]} > 0)); then
    echo "Found possible devices:" >&2
    local i=1
    for ip in "${candidates[@]}"; do
      echo "  [${i}] ${ip}:${port}" >&2
      i=$((i+1))
    done
    echo -n "Pick a number, or type an IP: " >&2
    read -r choice
    if [[ "${choice}" =~ ^[0-9]+$ ]] && (( choice >= 1 && choice <= ${#candidates[@]} )); then
      DEVICE_IP="${candidates[$((choice-1))]}"
      return 0
    fi
    DEVICE_IP="${choice}"
    return 0
  fi

  echo -n "Enter device IP (ADB-over-TCP): " >&2
  read -r DEVICE_IP
}

adb_cmd() {
  if [[ -n "${ADB_HOSTPORT}" ]]; then
    ADB_SERVER_SOCKET="tcp:${ADB_HOSTPORT}" "${ADB_BIN}" "$@"
  else
    "${ADB_BIN}" "$@"
  fi
}

# host:port for -s and disconnect. For "adb connect", ADT adb 1.0.31 appends :5555 itself,
# so passing host:5555 becomes host:5555:5555. Pass host only when port is 5555.
device_target() { echo "${DEVICE_IP%%:*}:${DEVICE_PORT%%:*}"; }
device_connect_arg() {
  local p="${DEVICE_PORT%%:*}"
  if [[ "${p:-5555}" = "5555" ]]; then echo "${DEVICE_IP%%:*}"; else echo "${DEVICE_IP%%:*}:${p}"; fi
}

adb_target() {
  local serial
  serial="$(device_target)"
  adb_cmd -s "${serial}" "$@"
}

connect_device() {
  [[ -n "${DEVICE_IP}" ]] || prompt_select_ip "${DEVICE_PORT}"
  [[ -n "${DEVICE_IP}" ]] || die "no device IP provided"

  adb_cmd start-server >/dev/null
  adb_cmd connect "$(device_connect_arg)"
  adb_cmd devices
}

disconnect_device() {
  [[ -n "${DEVICE_IP}" ]] || prompt_select_ip "${DEVICE_PORT}"
  [[ -n "${DEVICE_IP}" ]] || die "no device IP provided"
  adb_cmd disconnect "$(device_target)" || true
  adb_cmd devices
}

ensure_connected() {
  connect_device >/dev/null
}

pick_apk() {
  if [[ -n "${APK_PATH}" ]]; then
    [[ -f "${APK_PATH}" ]] || die "apk not found: ${APK_PATH}"
    echo "${APK_PATH}"
    return 0
  fi

  # Common outputs:
  #   bin/<project>.apk (Eclipse ADT)
  #   bin/<project>-debug.apk (Ant)
  local apk
  if [[ -f "${PROJECT_DIR}/bin/trmnl-nook-simple-touch.apk" ]]; then
    echo "${PROJECT_DIR}/bin/trmnl-nook-simple-touch.apk"
    return 0
  fi

  apk="$(ls -1t "${PROJECT_DIR}"/bin/*-debug.apk 2>/dev/null | head -n 1 || true)"
  if [[ -z "${apk}" ]]; then
    apk="$(ls -1t "${PROJECT_DIR}"/bin/*.apk 2>/dev/null | head -n 1 || true)"
  fi
  [[ -n "${apk}" ]] || die "no APK provided and none found in bin/*.apk. Use --apk or run ant debug."
  echo "${apk}"
}

android_update_project() {
  # Generate Ant build files (build.xml, etc.) for ADT/Ant-era projects.
  #
  # Derive SDK root from adb path:
  #   <sdk>/platform-tools/adb  ->  <sdk>
  local sdk_root android_tool
  sdk_root="$(cd "$(dirname "${ADB_BIN}")/.." && pwd)"
  android_tool="${sdk_root}/tools/android"

  [[ -x "${android_tool}" ]] || die "android tool not found at ${android_tool}. Install old SDK tools or run build from Eclipse ADT."

  # If build.xml already exists, do nothing.
  if [[ -f "${PROJECT_DIR}/build.xml" ]]; then
    return 0
  fi

  echo "Generating Ant build files (android update project)..." >&2
  (cd "${PROJECT_DIR}" && "${android_tool}" update project -p . -t android-20) >/dev/null
}

ant_debug() {
  if [[ -z "${ANT_BIN}" ]]; then
    die "ant not found. Install Apache Ant (ensure `ant` is in PATH), or set ANT=/path/to/ant, or pass --apk."
  fi
  if ! have javac; then
    die "javac not found in PATH. Ant 1.8.x needs a JDK (not just a JRE). Use SDKMAN to `sdk use java 8.x` or set JAVA_HOME to a JDK."
  fi
  android_update_project
  # Old aapt chokes on bin/res/crunch (generated by newer tools).
  if [[ -d "${PROJECT_DIR}/bin/res/crunch" ]]; then
    rm -rf "${PROJECT_DIR}/bin/res/crunch"
  fi
  local ant_targets="debug"
  if [[ "${CLEAN_BUILD}" == "1" ]]; then
    ant_targets="clean debug"
  fi
  (cd "${PROJECT_DIR}" && "${ANT_BIN}" -Dbuild.compiler="${BUILD_COMPILER}" ${ant_targets})
}

install_apk() {
  ensure_connected
  local apk tmp_path
  apk="$(pick_apk)"
  tmp_path="/data/local/tmp/trmnl-nook-simple-touch.apk"
  adb_target wait-for-device
  adb_target push "${apk}" "${tmp_path}"
  adb_target shell pm install -r "${tmp_path}"
  adb_target shell rm "${tmp_path}" >/dev/null 2>&1 || true
}

run_app() {
  ensure_connected
  adb_target shell am start -n "${APP_PKG}/${APP_ACTIVITY}"
}

logcat() {
  ensure_connected
  set -f
  adb_target logcat -v time ${LOGCAT_FILTER}
  set +f
}

shell_into() {
  ensure_connected
  adb_target shell
}

usage() {
  cat <<'EOF'
Usage:
  tools/nook-adb.sh [global options] <command> [command options]

Global options:
  --adb /path/to/adb         Use a specific adb binary (or set ADB env var)
  --ant /path/to/ant         Use a specific ant binary (or set ANT env var)
  --clean                    Clean before building (slower)
  (env) BUILD_COMPILER=modern Override Ant compiler adapter (default: modern)
  (env) ANT=/path/to/ant     Use a specific ant binary (or ensure `ant` is in PATH)
  --adb-server host:5037     Use a remote ADB server (sets ADB_SERVER_SOCKET)
  --ip <device-ip>           Device IP for ADB-over-TCP
  --port <port>              Device port (default: 5555)
  --scan                      Try to discover devices on the LAN
  --cidr <a.b.c.0/24>        CIDR for nmap scanning (optional)
  --logcat-filter "<filter>" Logcat filter (default: TRMNLAPI + BCHttpClient)

Commands:
  build                  Build APK only (ant debug). No ADB needed.
  connect
  disconnect
  install --apk path/to/app.apk
  run
  build-install-run
  build-install-run-logcat
  install-run-logcat
  logcat
  shell

Examples:
  tools/nook-adb.sh build                 # build only, no device
  tools/nook-adb.sh --clean build
  tools/nook-adb.sh connect --scan
  tools/nook-adb.sh connect               # defaults to 192.168.1.236:5555
  tools/nook-adb.sh connect --ip 192.168.1.50
  tools/nook-adb.sh install --apk bin/trmnl-nook-simple-touch-debug.apk
  tools/nook-adb.sh build-install-run     # runs ant debug, installs, launches
  tools/nook-adb.sh --clean build-install-run
  tools/nook-adb.sh install-run-logcat    # install + run + logcat
  tools/nook-adb.sh build-install-run-logcat --ant /path/to/ant
EOF
}

parse_global_opts() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --adb)
        shift
        [[ $# -gt 0 ]] || die "--adb requires a value"
        ADB_BIN="$1"
        shift
        ;;
      --ant)
        shift
        [[ $# -gt 0 ]] || die "--ant requires a value"
        ANT_BIN="$1"
        shift
        ;;
      --clean)
        CLEAN_BUILD="1"
        shift
        ;;
      --adb-server)
        shift
        [[ $# -gt 0 ]] || die "--adb-server requires host:port"
        ADB_HOSTPORT="$1"
        shift
        ;;
      --ip)
        shift
        [[ $# -gt 0 ]] || die "--ip requires a value"
        # store host only so device_target never becomes host:5555:5555
        DEVICE_IP="${1%%:*}"
        shift
        ;;
      --port)
        shift
        [[ $# -gt 0 ]] || die "--port requires a value"
        DEVICE_PORT="$1"
        shift
        ;;
      --scan)
        DO_SCAN="1"
        shift
        ;;
      --cidr)
        shift
        [[ $# -gt 0 ]] || die "--cidr requires a value (e.g. 192.168.1.0/24)"
        CIDR="$1"
        shift
        ;;
      --logcat-filter)
        shift
        [[ $# -gt 0 ]] || die "--logcat-filter requires a value"
        LOGCAT_FILTER="$1"
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        # first non-option is the command; leave rest for main
        REMAINING_ARGS=("$@")
        return 0
        ;;
    esac
  done
  REMAINING_ARGS=()
}

main() {
  REMAINING_ARGS=("$@")
  parse_global_opts "$@"
  set -- "${REMAINING_ARGS[@]}"

  local cmd="${1:-}"
  shift || true

  case "${cmd}" in
    build)
      ant_debug
      ;;
    connect)
      connect_device
      ;;
    disconnect)
      disconnect_device
      ;;
    install)
      while [[ $# -gt 0 ]]; do
        case "$1" in
          --apk)
            shift
            [[ $# -gt 0 ]] || die "--apk requires a value"
            APK_PATH="$1"
            shift
            ;;
          *)
            die "unknown option for install: $1"
            ;;
        esac
      done
      install_apk
      ;;
    run)
      run_app
      ;;
    build-install-run)
      ant_debug
      install_apk
      run_app
      ;;
    build-install-run-logcat)
      ant_debug
      install_apk
      adb_target logcat -c
      run_app
      logcat
      ;;
    install-run-logcat)
      install_apk
      adb_target logcat -c
      run_app
      logcat
      ;;
    logcat)
      logcat
      ;;
    shell)
      shell_into
      ;;
    ""|-h|--help)
      usage
      ;;
    *)
      die "unknown command: ${cmd} (run with --help)"
      ;;
  esac
}

main "$@"
