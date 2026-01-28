# Sleep/Wake Cycle (Deep Sleep Mode)

The NOOK can operate in deep sleep mode to conserve battery. When `allow_sleep` is enabled in settings, the app follows an "Electric-Sign" pattern.

## Flow

```
[Display image] → 5s delay → [Write screensaver] → [Sleep]
                                                      ↓
[Wake on alarm] ← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ [Alarm fires]
      ↓
[Turn WiFi on] → [Wait for connectivity] → [Fetch] → [Display]
```

## Key Functions

### `scheduleScreensaverThenSleep()`
Called after successfully displaying an API image:
1. Waits `SCREENSAVER_DELAY_MS` (5s) so user sees image
2. Writes current image to screensaver path (if enabled)
3. Schedules alarm for next refresh
4. Disables keep-screen-awake
5. Turns off WiFi

### `scheduleReload(sleepMs)`
Sets an `AlarmManager.RTC_WAKEUP` alarm to wake device after `sleepMs`.

### `alarmReceiver`
BroadcastReceiver that fires when alarm triggers:
1. Sets keep-screen-awake
2. Turns WiFi on
3. Calls `waitForWifiThenFetch()` (waits for connectivity before fetching)

### `waitForWifiThenFetch()`
Registers a connectivity BroadcastReceiver:
- When network connects → starts fetch
- Timeout after `CONNECTIVITY_MAX_WAIT_MS` (30s) → shows error

### `setKeepScreenAwake(boolean)`
Toggles `FLAG_KEEP_SCREEN_ON` window flag.

## Settings

- `allow_sleep`: Enable deep sleep mode
- `write_screensaver`: Write displayed image to NOOK screensaver path
- `screensaver_path`: Path to write screensaver (default: `/data/media/...`)

## Timing

- `SCREENSAVER_DELAY_MS`: 5 seconds (delay before sleep)
- `CONNECTIVITY_MAX_WAIT_MS`: 30 seconds (WiFi connect timeout)
- `WIFI_WARMUP_MS`: 45 seconds (legacy warmup delay, now uses connectivity listener)
- `refresh_rate`: From API response (typically 15 minutes)

## WiFi Management

WiFi is **off during sleep** to save power. On wake:
1. `ensureWifiOnWhenForeground()` turns WiFi on
2. `waitForWifiThenFetch()` waits for actual connectivity
3. Fetch only starts once network is connected

This avoids wasting retries on a network that's still connecting.
