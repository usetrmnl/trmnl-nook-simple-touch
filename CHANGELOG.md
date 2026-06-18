# Changelog

All notable changes to this project will be documented in this file.

## [v0.15.0] - 2026-06-17

### Fixed
- **Wi-Fi no longer fails to reconnect after waking from sleep** - On wake the app turns the Wi-Fi radio on and then immediately checks whether it is enabled, but `setWifiEnabled()` is asynchronous and the radio is usually still powering on at that moment. The app treated that "still enabling" state as "radio off, nothing to wait for", showed the "This smart device needs WiFi" screen, and went back to sleep without ever polling the server (#44). The wake path now distinguishes a radio that is still enabling from one that is genuinely off: it waits for the radio to come up and associate (and re-enables it when sleep mode left it off) instead of giving up immediately.

### Added
- **Configurable Wi-Fi connect timeout** (`Settings → Network → WiFi connect timeout`) - How long the device waits for Wi-Fi to associate on wake before giving up. The default is 5 s (unchanged behavior); raise it if your access point or busy 2.4 GHz environment is slow to join. Clamped to 5–120 s.

---

## [v0.13.2] - 2026-04-22

### Fixed
- **Wi-Fi recovery after connectivity timeout** - When a battery-powered Nook wakes from sleep and its Wi-Fi radio gets stuck in a bad reconnection state, the app now toggles Wi-Fi off/on (up to 2 attempts) before falling back to the no-wifi screen. Previously the device would show "Couldn't connect" and stay stuck until manually rebooted.

---

## [v0.12.2] - 2026-03-29

### Fixed
- **Menu → Next no longer gets stuck on "Connecting..."** - Opening the menu and tapping Next was cancelling the WiFi connectivity wait mid-fetch.

---

## [v0.12.1] - 2026-03-29

### Fixed
- **Aggressive sleep wake cycles no longer flash the log screen.** The app now renders the new image before sleeping, so you see the image instead of status text.
- Screen timeout is always restored if you navigate away or the app exits mid-sleep.

---

## [v0.12.0] - 2026-03-29

### Fixed
- **Aggressive sleep now reliably fires after every scheduled refresh.** Previously the super-sleep check used `fetchReason` and flag state that could be clobbered by `onResume()`, meaning the device often stayed awake after displaying a new image. Simplified to: if Aggressive Sleep is enabled and the fetch was not triggered by the user (menu tap), call `sleepNow()` immediately after the image renders — no flags, no race conditions.
- `sleepPending` is now correctly cleared in `onResume()` and the screen timeout is always restored to 120 s on wake, regardless of sleep path.

### Notes
- All source edits must target the worktree at `/home/coder/trmnl-nook-sleep/` — see AGENTS.md.

---

## [v0.11.0] - 2026-03-28

### Added
- **Aggressive Sleep** (`Settings → General → Aggressive sleep`): puts the device to sleep immediately after each scheduled image refresh rather than waiting for the screensaver timeout. Battery savings vs. standard deep sleep are TBD.
- **Sleep button** (`Settings → System → Sleep`): manually triggers an immediate sleep from the settings screen.
- `android.permission.WRITE_SETTINGS` permission — used to set `SCREEN_OFF_TIMEOUT = 1000 ms` to trigger Android's natural screen-off path (no root required). Restored to 120 s on wake.
- `AGENTS.md`: documents the build environment, all failed sleep approaches, and the working `WRITE_SETTINGS` trick.

### Changed
- Screensaver/sleep-ready delay reduced from 5 s to 2 s.
- Deep sleep hint always visible when "Sleep between updates" is enabled.
- README: added Aggressive Sleep section and listed it in Features.

### Removed
- `android.permission.DEVICE_POWER` (was unused / ungrantable).

---

## [v0.10.0] - 2026-03-25

### Added
- Gift Mode restart flow improvements.

---

*Older releases are documented on the [GitHub Releases](https://github.com/usetrmnl/trmnl-nook-simple-touch/releases) page.*
