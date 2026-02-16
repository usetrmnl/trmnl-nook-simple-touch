# Agent notes (legacy Android / NOOK Simple Touch)

This repo targets very old Android and an Eclipse ADT / Ant-era workflow.
Do not suggest modern Android tooling or APIs unless they are explicitly
compatible with Android 2.1 (API 7).

Current notes:
- Main entry activity: `DisplayActivity`.
- API credentials/base URL live in app settings (`ApiPrefs`).

## Key Patterns

(Temporary note) ADB-over-TCP on NOOK is flaky after sleep/wake. If `adb devices` shows `offline`, restart host adb and reconnect:

```bash
adb kill-server
adb start-server
adb disconnect <ip>:5555
sleep 1
adb connect <ip>:5555
adb devices
```

Saved file logs (when "Save to file" enabled) live at:
- `/media/My Files/trmnl.log`

To pull tail:
```bash
adb -s <ip>:5555 shell "tail -n 200 '/media/My Files/trmnl.log'"
```

### HTTP Requests
- All HTTPS goes through `BouncyCastleHttpClient` (TLS 1.2 support for old Android)
- HTTP (non-TLS) supported when "Allow HTTP" setting enabled (for BYOS/local servers)
- Self-signed certs supported when "Allow self-signed certificates" enabled
- **Always retry failed requests** with 3s backoff (network often flaky after wake)
- Wait for WiFi connectivity before attempting fetches (`waitForWifiThenFetch()`)
- Image fetches need retry too, not just API calls

### Testing on Device
- Use `NOOK_IP=<ip> tools/nook-adb.sh install-run-logcat` for quick test cycles
- Run logcat in background: `tools/nook-adb.sh logcat` to monitor while testing
- Device often goes offline when WiFi auto-disabled; use "Auto-disable WiFi" setting OFF during dev
- ADB reconnect: `tools/nook-adb.sh connect` after device comes back online

### Boot & Error UX

### Prefs presets (SaaS / self-hosted)

To switch settings quickly, create local presets in `prefs/` (gitignored). Use one argument per line so the shell doesn't have to parse quoting.

Example: `prefs/selfhosted.args`

```
--string
api_id
A1:B2:C3:D4:E5:F6
--string
api_token
YOUR_TOKEN
--string
api_base_url
http://192.168.1.232:2300/api
--bool
allow_http
true
--bool
allow_sleep
false
```

Apply with:

```
tools/nook-adb.sh --ip <ip> set-preset selfhosted
```

For SaaS, create `prefs/saas.args` with the hosted credentials/base URL, then:

```
tools/nook-adb.sh --ip <ip> set-preset saas
```
- Boot screen: header with icon + status text + streaming logs below
- Update status via `setBootStatus("message")` during boot
- On error: show boot header with "Error - tap to retry" + full logs
- Call `hideBootScreen()` only when content successfully loads

### Logging
- `logD()`/`logW()` stream to screen during boot (while `!bootComplete`)
- After boot completes, logs only go to Android logcat
- `logE()` always shows on screen

## Index

### Worktree merge notes

When `main` is checked out in another worktree, you can't switch it here. Use a worktree-safe patch:

```
# from this worktree

git format-patch origin/main --stdout > /tmp/adb-device-ad7e.patch

# then in the main worktree

git apply /tmp/adb-device-ad7e.patch

git commit -am "Merge adb-device-ad7e changes"
```

- `AGENTS/platform-constraints.md`
- `AGENTS/build-tooling.md`
- `AGENTS/release.md`
- `AGENTS/tls-network.md`
- `AGENTS/sleep-wake-cycle.md`
- `AGENTS/ux-patterns.md`
- `AGENTS/references.md`

