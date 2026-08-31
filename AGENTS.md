# Agent / Developer Notes

This file documents non-obvious implementation decisions and dead ends for future agents or contributors working on this codebase.

---

## Forcing the Screen Off on NOOK Simple Touch (Android 2.1 / API 7)

The NOOK Simple Touch runs a heavily customised Android 2.1 (Eclair) firmware. Putting the screen to sleep programmatically from a third-party app is much harder than it looks. This section documents every approach tried and the one that actually works.

### Device facts
- SoC: OMAP3 (Texas Instruments)
- Android API: 7 (Eclair 2.1) — `AndroidManifest.xml` targets minSdk/targetSdk 7; the README has the same. (An earlier revision of this file incorrectly said Gingerbread 2.3 / API 10.)
- ADB: runs as `uid=0` (root), `ro.secure=0`
- Rooted via Phoenix Project; `/system/bin/su` and `/system/xbin/su` present
- Superuser manager: `com.noshufou.android.su` (stores per-UID **and** per-command grants in `permissions.sqlite`)
- Screen timeout setting: `mTotalDelaySetting=120000` (2 min), controlled by `Settings.System.SCREEN_OFF_TIMEOUT`
- EPD (e-ink) driver: `NATIVE-EPD` / `EPD#PowerManager` — screensaver rendering is triggered by `PowerManagerService` during the normal screen-off path
- Power button fires `KEY_POWER (0x66)` on `/dev/input/event1`

### What does NOT work

| Approach | Why it fails |
|---|---|
| `PowerManager.goToSleep(long)` | Requires `android.permission.DEVICE_POWER` — a `signatureOrSystem` permission. Not grantable to third-party apps even on a rooted device. Throws `SecurityException`. |
| `KEYCODE_POWER` via `input keyevent 26` | No-op on NOOK firmware. The power key is handled at the kernel level before it reaches Android's input system. |
| `sendevent /dev/input/event1 1 116 1` (raw EV_KEY KEY_POWER) | Also no-op for sleep purposes. The kernel intercepts it before `PowerManagerService` sees it in a useful way. |
| `echo mem > /sys/power/state` via `su` | Works as a raw kernel suspend, **but** bypasses the Android power manager entirely. Result: no EPD screensaver renders, wake behaviour is broken (home button unreliable), and it is indistinguishable from a hard crash from Android's perspective. |
| `service call power 6 i32 ...` via `su` | Wrong transaction number. `i64` is not supported by this firmware's `service` binary — only `i32` and `s16`. |
| `service call power 2 i32 <uptime> i32 0` via `su` | Correct binder call (`IPowerManager.goToSleep`, transaction 2 on this firmware), and it works from ADB shell. **But** the Superuser dialog appears first, which itself counts as user activity and resets the power manager idle timer. By the time the user taps Allow and `su` actually executes, the timestamp passed to `goToSleep` is stale — the power manager ignores it. Even if you pre-grant via `permissions.sqlite`, the grant is per-command (the timestamp is part of the command string), so it prompts every time. |
| `am broadcast -a com.bn.standby.ACTION_PRE_STANDBY` | WindowManager logs `Handling com.bn.standby.ACTION_PRE_STANDBY` but the broadcast alone does not sleep the screen — it is sent *by* the power manager *after* the screen is already off, not the trigger for it. |

### What DOES work — the `WRITE_SETTINGS` timeout trick

`android.permission.WRITE_SETTINGS` is a **normal** permission any third-party app can hold. Using it, the app can set `Settings.System.SCREEN_OFF_TIMEOUT` to a very small value (e.g. 1000 ms). Android's own `PowerManagerService` then fires the natural screen-off path within ~1 second, which:

- Triggers the NOOK EPD screensaver render (`EPD#PowerManager fillRegion`)
- Calls `set_screen_state 0` properly
- Broadcasts `ACTION_SCREEN_OFF`
- Sets up the keyguard correctly so home/power button wakes the device normally

The sleep sequence in `DisplayActivity.sleepNow()`:

1. Write the current TRMNL image to `/media/screensavers/TRMNL/display.png` (so the NOOK screensaver shows the right content)
2. Schedule an `AlarmManager` wake alarm for the next refresh cycle
3. Turn WiFi off if auto-disable is enabled
4. Call `getWindow().clearFlags(FLAG_KEEP_SCREEN_ON)` — removes the wake lock
5. Set `sleepPending = true` — blocks `onResume` from re-asserting `FLAG_KEEP_SCREEN_ON` during the pause/resume cycle caused by the menu dismissing
6. Post a 5-second delayed `Runnable` (grace period for the user's finger to lift) that sets `Settings.System.SCREEN_OFF_TIMEOUT = 1000`
7. The user's original `SCREEN_OFF_TIMEOUT` (captured into prefs at startup, before any override) is restored by an `ACTION_SCREEN_OFF` receiver the moment the screen turns off — so a killed process can never strand the 1s value in `settings.db`. `onResume`/`onPause`/`onDestroy` restore it as safety nets, and startup self-heals if a previous run left a stranded value. Never hardcode a restore value — use the captured original.

**Required manifest permission:**
```xml
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```

No root, no su, no Superuser dialog.

---

## Build Environment

- Build tool: Apache Ant 1.8.3 (bundled in ADT bundle)
- Java runtime target: API 7 / Android 2.1 (compiled against the android-20 platform jar bundled with the ADT — see the note in `project.properties`)
- Build command (inside devcontainer):
  ```
  cd /workspace && /opt/adt/adt-bundle-linux-x86_64-20140702/eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant -Dbuild.compiler=modern debug
  ```
- ADB binary: `/opt/adt/adt-bundle-linux-x86_64-20140702/sdk/platform-tools/adb` (v1.0.31)
- ADB connect: use bare IP, e.g. `adb connect 192.168.1.249` — the old client appends `:5555` automatically
- Install: `adb -s 192.168.1.249:5555 install -r /workspace/bin/trmnl-nook-simple-touch-debug.apk`
- If install fails with `INSTALL_FAILED_INSUFFICIENT_STORAGE`: uninstall first (`adb uninstall com.bpmct.trmnl_nook_simple_touch`), then install fresh

## Devcontainer

The devcontainer at `.devcontainer/devcontainer.json` is the canonical build environment. Start it with:
```
cd /home/coder/trmnl-nook-sleep && devcontainer up --workspace-folder .
```
Then exec into it:
```
docker exec <container_id> bash -c "<command>"
```

## Worktrees

Active development happens in the worktree at `/home/coder/trmnl-nook-showcase` on branch `feature/showcase-mode`. The main checkout is at `/home/coder/trmnl-nook-simple-touch`.

> **CRITICAL for agents:** The devcontainer mounts `/home/coder/trmnl-nook` as `/workspace`.
> All source edits MUST be made to files under the active worktree (e.g. `/home/coder/trmnl-nook-showcase/`).
> Editing the main checkout directly has NO effect on builds.
> Always verify with: `docker inspect <container_id> --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{println}}{{end}}'`

---

## RotateLayout Coordinate Geometry

The physical screen is **800px wide × 600px tall** (landscape, hardware mounted sideways). `RotateLayout` applies a 90° rotation to all UI content so it renders correctly.

### Transform mechanics

`dispatchDraw` applies:
```java
canvas.translate(getWidth(), 0);  // getWidth() = 800
canvas.rotate(90);
```

A point at child coordinates `(cx, cy)` maps to physical coordinates:
```
physical_x = 800 - cy
physical_y = cx
```

- **child-X axis → physical-Y axis** (vertical on screen)
- **child-Y axis → physical-X axis** (horizontal on screen, inverted)

### Root child dimensions

`RotateLayout.onMeasure` swaps width/height specs for 90°, so the `root` FrameLayout child thinks it is **800px wide × 600px tall** even though it visually renders as 600px wide × 800px tall on the physical screen.

| `root` field | Value | Physical meaning |
|---|---|---|
| `root.getWidth()` | 800 | physical height |
| `root.getHeight()` | 600 | physical width |

### Menu bar layout rules

The menu is a **HORIZONTAL** `LinearLayout` inside `root`.

- **Width**: fixed value less than 800 (currently `480`) with `Gravity.CENTER` — gives a floating centred bar. Do **not** use `MATCH_PARENT` (that gives 800px = full physical height). Do **not** use 600 (that is `root.getHeight()` = physical width).
- **Height**: `WRAP_CONTENT` (~40px)
- **Buttons**: `new LinearLayout.LayoutParams(0, 40, 1.0f)` — `width=0, weight=1` distributes evenly across child-X (= physical-Y); `height=40` sets bar thickness in child-Y (= physical-X).
- `Button.setMinWidth(0)` + `Button.setMinimumWidth(0)` are **required** — Android's Button has a built-in minimum width that breaks weight distribution without them.

### Re-show clipping fix

After `menuLayout.setVisibility(GONE)` → `VISIBLE`, Android may skip re-measuring since params haven't changed. `requestLayout()` alone is unreliable. The fix: call `setLayoutParams()` with the desired params **every time the menu is shown** — this forces a re-measure and correct `Gravity.CENTER` positioning.

### `dispatchDraw` canvas state

`RotateLayout.dispatchDraw` must call `canvas.save()` / `canvas.restoreToCount()` around the transform. Without it the canvas state leaks to subsequent draw passes, causing right-side EPD ghosting.
