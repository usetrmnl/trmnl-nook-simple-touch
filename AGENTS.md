# Agent notes (legacy Android / NOOK Simple Touch)

This repo targets very old Android and an Eclipse ADT / Ant-era workflow.
Do not suggest modern Android tooling or APIs unless they are explicitly
compatible with Android 2.1 (API 7).

Current notes:
- Main entry activity: `DisplayActivity`.
- API credentials/base URL live in app settings (`ApiPrefs`).

## Key Patterns

### HTTP Requests
- All HTTPS goes through `BouncyCastleHttpClient` (TLS 1.2 support for old Android)
- **Always retry failed requests** with 3s backoff (network often flaky after wake)
- Wait for WiFi connectivity before attempting fetches (`waitForWifiThenFetch()`)
- Image fetches need retry too, not just API calls

### Boot & Error UX
- Boot screen: header with icon + status text + streaming logs below
- Update status via `setBootStatus("message")` during boot
- On error: show boot header with "Error - tap to retry" + full logs
- Call `hideBootScreen()` only when content successfully loads

### Logging
- `logD()`/`logW()` stream to screen during boot (while `!bootComplete`)
- After boot completes, logs only go to Android logcat
- `logE()` always shows on screen

## Index

- `AGENTS/platform-constraints.md`
- `AGENTS/build-tooling.md`
- `AGENTS/release.md`
- `AGENTS/tls-network.md`
- `AGENTS/sleep-wake-cycle.md`
- `AGENTS/ux-patterns.md`
- `AGENTS/references.md`

