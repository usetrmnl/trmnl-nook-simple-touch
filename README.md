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
- [Device Settings](#device-settings)
- [Features](#features)
- [Deep Sleep Mode](#deep-sleep-mode)
- [Frames and Cases](#frames-and-cases)
- [Gift Mode](#gift-mode)
- [Roadmap](#roadmap)
- [Development](#development)
- [Disclaimer](#disclaimer)

## Prerequisites
- Root the device using the [Phoenix Project](https://xdaforums.com/t/nst-g-the-phoenix-project.4673934/). I used "phase 4" (the minimal rooted install for customization). The phases were confusing because you do not need phase 1/2/3 (each is a separate backup).
- Buy a [TRMNL BYOD license](https://shop.usetrmnl.com/collections/frontpage/products/byod) and grab your SSID + API key from Developer Settings after login (or use your own server).

## Install
- Download the APK from [GitHub Releases](https://github.com/bpmct/trmnl-nook-simple-touch/releases).
- Connect the Nook Simple Touch over USB and copy the APK over.
- Open the included `ES File Explorer` app.
- In ES File Explorer: `Favorites -> "/" -> "media" -> "My Files".`
- Tap the APK and install.
- Connect your device to WiFi
- Open the app and configure the device info

## Device Settings

In the TRMNL Device settings, set the device type to "Nook Simple Touch" as the TRMNL team was nice enough to add support for this device!

## Features

- On-device config UI for device ID, API key, and API URL (BYOS)
- Fetches your screen and shows it fullscreen, bypassing the lock screen until you exit
- Respects playlist intervals to advance to the next screen
- TLS v1.2 via BouncyCastle (not included in Android 2.1)
- BYOD support for TRMNL and custom server URLs
- Reports battery voltage and Wi-Fi signal strength
- Deep sleep mode for 30+ day battery life
- Gift Mode for pre-configuring devices as gifts

## Deep Sleep Mode

Without deep sleep, expect ~60 hours of battery life. With deep sleep and a 30-minute refresh rate, battery lasts 30+ days. The app writes each image to the Nook's screensaver, turns off WiFi, and sets an RTC alarm to wake for the next refresh.

To enable:
1. In the app: Enable "Sleep between updates"
2. In `Nook Settings → Display → Screensaver`: Set to "TRMNL" with 2-minute timeout
3. In `Apps → Nook Touch Mod`: Enable "Hide Screensaver Banner"

## Frames and Cases

The Nook Simple Touch often develops sticky residue on its rubberized surfaces as it ages. [iFixit](https://www.ifixit.com/Device/Barnes_%26_Noble_Nook_Simple_Touch) has great teardown and repair guides if you need to clean or refurbish your device.

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

For newer Nook devices like the Nook Glowlight 4, see [trmnl-nook](https://github.com/usetrmnl/trmnl-nook).

## Disclaimer
AI was used to help code this repo. I have a software development background, but did not want to relearn old Java and the Android 2.1 ecosystem. Despite best-effort scanning and review, the device and/or this software may contain vulnerabilities. Use at your own risk, and if you want to be safer, run it on a guest network.
