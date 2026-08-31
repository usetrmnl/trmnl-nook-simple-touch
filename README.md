# TRMNL client for Nook Simple Touch

A [TRMNL client](https://trmnl.com/developers) for the Nook Simple Touch (BNRV300) and Nook Simple Touch with Glowlight (BNRV350). These devices usually go for around $30 on eBay and have an 800x600 e-ink display.

<table>
<tr>
<td width="33%" align="center"><img src="images/configuration.jpg" alt="Configuration"><br><em>Configuration screen</em></td>
<td width="33%" align="center"><img src="images/display.jpg" alt="Display"><br><em>Fullscreen view</em></td>
<td width="33%" align="center"><img src="images/dialog.jpg" alt="Dialog"><br><em>Menu dialog</em></td>
</tr>
</table>

Questions or feedback? Please [open an issue](https://github.com/bpmct/trmnl-nook-simple-touch/issues/new).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Install](#install)
  - [Easy Setup](#easy-setup-recommended)
  - [Manual Setup](#manual-setup)
- [Device Settings](#device-settings)
- [Features](#features)
- [Deep Sleep Mode](#deep-sleep-mode)
- [Aggressive Sleep](#aggressive-sleep)
- [Frames and Cases](#frames-and-cases)
- [Gift Mode](#gift-mode)
- [Roadmap](#roadmap)
- [Other Nook Models](#other-nook-models)
- [Development](#development)
- [Disclaimer](#disclaimer)

## Prerequisites
- Root the device using the [Phoenix Project](https://xdaforums.com/t/nst-g-the-phoenix-project.4673934/). I used "phase 4" (the minimal rooted install for customization). The phases were confusing because you do not need phase 1/2/3 (each is a separate backup). Phase 4 comes in two firmware flavors — this app is developed and tested against **FW 1.2.2** (`NST_Phase4_122.zip`), so prefer that one. The FW 1.1.5 image may behave differently (the Phoenix author notes the e-ink controller code differs between firmware versions).
- Buy a [TRMNL BYOD license](https://shop.usetrmnl.com/collections/frontpage/products/byod) and grab your SSID + API key from Developer Settings after login (or use your own server).

## Install

### Easy Setup (recommended)

I built [a web tool](https://nooks.bpmct.net/manage/) that handles the whole setup over USB — installs the app, configures settings, and gets you on WiFi, all from the browser. No ADB needed.

> Requires Chrome or Edge (WebUSB). Your NOOK must be rooted first via the [Phoenix Project](https://xdaforums.com/t/nst-g-the-phoenix-project.4673934/) phase 4 before it'll show up.

[![nooks.bpmct.net setup wizard](images/nooks-webapp.png)](https://nooks.bpmct.net/manage/)

The wizard walks you through five steps:

1. **Connect** — Plug in your NOOK via USB, then click "Connect Device" to open the browser's USB picker. If prompted on the NOOK screen, tap *Allow* to authorize the connection.
2. **Setup** — Reads your device model, verifies the TRMNL app is installed (installing or updating it if needed), and applies any missing system settings automatically.
3. **TRMNL** — Sign up at trmnl.com, then enter the MAC address and Device API Key from your device's Developer Perks page. Self-hosted (BYOS) and gift mode are available under advanced options.
4. **Network** — If your NOOK is already on a network, this step confirms the connection and moves on. Otherwise, it scans for nearby networks and writes the credentials directly to the device.
5. **Done** — Your NOOK is ready. Unplug the USB cable and manage plugins and settings at trmnl.com.

The web tool configures all required device settings automatically, including those listed in [Device Settings](#device-settings) below.

---

### Manual Setup

1. Download the APK from [GitHub Releases](https://github.com/bpmct/trmnl-nook-simple-touch/releases).
2. Connect the Nook Simple Touch over USB and copy the APK over.
3. Open the included `ES File Explorer` app.
4. In ES File Explorer: `Favorites -> "/" -> "media" -> "My Files".`
5. Tap the APK and install.
6. Connect your device to WiFi.
7. Open the app and configure the device info.

After installing manually, you'll also need to configure [Device Settings](#device-settings).

## Device Settings

In the TRMNL Device settings, set the device type to "Nook Simple Touch" as the TRMNL team was nice enough to add support for this device!

The [web tool](https://nooks.bpmct.net/manage/) applies all of the settings below automatically. If you installed manually, configure each one by hand:

| Where | Setting | Value | Purpose |
|-------|---------|-------|----------|
| `Nook Settings → Display → Screensaver` | Screensaver | TRMNL / 2 min timeout | Points the screensaver at the TRMNL image and ensures it activates for deep sleep |
| `Apps → Nook Touch Mod` | Hide screensaver banner | Enabled | Hides the text overlay on the screensaver |
| `Apps → Nook Touch Mod` | Disable drag to unlock | Enabled | Skips the drag-to-unlock gesture on screensaver wake |
| `Apps → Nook Touch Mod` *(optional)* | Home button (short press) | Launches TRMNL app | Remaps the physical Home button to open TRMNL directly |
| `Apps → Nook Touch Mod` *(optional)* | Home button (long press) | Opens App Drawer | Remaps long-press Home to the app drawer |
| TRMNL app → Settings → General | Sleep between updates | Enabled | Enables deep sleep between refresh cycles |
| TRMNL app → Settings → General | Aggressive sleep | Enabled | Sleeps immediately after each refresh rather than waiting for timeout |

> **Note:** Nook Touch Mod settings require [Nook Mod Manager (NMM)](https://xdaforums.com/t/nst-g-the-phoenix-project.4673934/) to be installed (included in Phoenix Project phase 4). The Home button remapping rows are optional — the app works without them.

## Features

- On-device config UI for device ID, API key, and API URL (BYOS)
- Fetches your screen and shows it fullscreen, bypassing the lock screen until you exit
- Respects playlist intervals to advance to the next screen
- TLS v1.2 via BouncyCastle (not included in Android 2.1)
- BYOD support for TRMNL and custom server URLs
- Reports battery voltage and Wi-Fi signal strength
- Deep sleep mode for 30+ day battery life
- Aggressive sleep for maximum battery savings (benchmarking TBD)
- Gift Mode for pre-configuring devices as gifts

## Deep Sleep Mode

Without deep sleep, expect ~60 hours of battery life. With deep sleep and a 30-minute refresh rate, battery lasts 30+ days. The app writes each image to the Nook's screensaver, turns off WiFi, and sets an RTC alarm to wake for the next refresh.

To enable:
1. In the app: Enable "Sleep between updates"
2. In `Nook Settings → Display → Screensaver`: Set to "TRMNL" with 2-minute timeout
3. In `Apps → Nook Touch Mod`: Enable "Hide Screensaver Banner"

## Aggressive Sleep

Aggressive sleep is an optional mode on top of deep sleep that puts the device to sleep immediately after each scheduled image refresh, rather than waiting for the screensaver timeout. This can further improve battery life, though benchmarking is still in progress — exact savings are TBD.

To enable:
1. First enable "Sleep between updates" (see [Deep Sleep Mode](#deep-sleep-mode))
2. In the app: Settings → General → Enable "Aggressive sleep"

You can also trigger a manual sleep at any time from Settings → System → "Sleep".

## Frames and Cases

The Nook Simple Touch often develops sticky residue on its rubberized surfaces as it ages. [iFixit](https://www.ifixit.com/Device/Nook_BNRV300) has great teardown and repair guides if you need to clean or refurbish your device.

<img src="images/frame-comparison.jpg" alt="3D-printed frame (left) vs original case (right)" width="500">

For a custom frame, I recommend this [3D-printed case on Thingiverse](https://www.thingiverse.com/thing:7140441). It requires:
- M3x4 flush screws
- M3x5x4 threaded inserts (soldering iron required to install)
- The original screws and inserts from the Nook Simple Touch

## Gift Mode

Gift Mode displays setup instructions instead of fetching content—perfect for giving a pre-configured device as a gift.

To set up:
1. Buy a [BYOD license](https://shop.usetrmnl.com/products/byod) for the recipient
2. Get the friendly device code from [trmnl.com/claim-a-device](https://trmnl.com/claim-a-device)
3. In the app: Settings → Enable "Gift mode" → "Configure Gift Mode"
4. Enter your name, recipient's name, and the device code

## Roadmap

See [GitHub Issues](https://github.com/bpmct/trmnl-nook-simple-touch/issues) for the roadmap and to submit feature requests.

## Development
See the CI workflow for build details ([`build-apk.yml`](https://github.com/bpmct/trmnl-nook-simple-touch/blob/main/.github/workflows/build-apk.yml)), and the `tools/` adb scripts for build/install workflows. A development guide is coming (https://github.com/bpmct/trmnl-nook-simple-touch/issues/8). In the meantime, the project can be built with surprisingly minimal, self-contained dependencies.

## Other Nook Models

This repository targets legacy Nook devices running Android 2.1 (API 7), which requires different tooling and approaches than modern Android. For newer Nook devices like the Nook Glowlight 4, see [trmnl-nook](https://github.com/usetrmnl/trmnl-nook).

If you have another Nook model from this era that you'd like to test, please [open an issue](https://github.com/bpmct/trmnl-nook-simple-touch/issues/new)!

## Disclaimer
AI was used to help code this repo. I have a software development background, but did not want to relearn old Java and the Android 2.1 ecosystem. Despite best-effort scanning and review, the device and/or this software may contain vulnerabilities. Use at your own risk, and if you want to be safer, run it on a guest network.
