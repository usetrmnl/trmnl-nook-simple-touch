# TRMNL client for Nook Simple Touch

This is a [TRMNL client](https://trmnl.com/developers) for the Nook Simple Touch (BNRV300). The device usually goes for around $30 on eBay and has an 800x600 e-ink display.

<table>
<tr>
<td width="33%" align="center"><img src="images/configuration.jpg" alt="Configuration"><br><em>Configuration screen</em></td>
<td width="33%" align="center"><img src="images/display.jpg" alt="Display"><br><em>Fullscreen view</em></td>
<td width="33%" align="center"><img src="images/dialog.jpg" alt="Dialog"><br><em>Menu dialog</em></td>
</tr>
</table>

Questions or feedback? Please [open an issue](https://github.com/bpmct/trmnl-nook-simple-touch/issues/new).

## What about [trmnl-nook](https://github.com/usetrmnl/trmnl-nook)?

The [trmnl-nook](https://github.com/usetrmnl/trmnl-nook) repository is designed for much newer versions of Android (targeting Nook Glowlight 4 and similar modern devices). This repository (`trmnl-nook-simple-touch`) targets the legacy Nook Simple Touch running Android 2.1 (API 7), which requires different tooling and approaches.

I'd love to work with the TRMNL team to combine these repositories, but a lot of the code will remain seperate due to the fundamental differences in Android versions and device capabilities. The core TRMNL API integration and display logic share similarities, but the power management, wake/sleep handling, and build tooling differ significantly between Android 2.1 and modern Android versions.

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

In the TRMNL Device settings, set the device type to "Amazon Kindle 7" (800x600). This matches the Nook Simple Touch's display resolution. See [issue #10](https://github.com/bpmct/trmnl-nook-simple-touch/issues/10) for why this workaround is needed and efforts to add a dedicated device type.

## What this client does
- On-device config UI for device ID, API key, and API URL (BYOS)
- Fetches your screen and shows it full screen on the Nook, bypassing the lock screen until you exit
- Properly respects playlist intervals to advance to the next screen
- TLS v1.2 via BouncyCastle (not included in Android 2.1)
- BYOD support for TRMNL and custom server URLs
- Reports battery voltage and Wi-Fi signal strength
- Deep sleep mode for 30+ day battery life
- Gift Mode for pre-configuring devices as gifts

## Deep Sleep Mode

Without deep sleep, expect ~60 hours of battery life. With deep sleep enabled and a 30-minute [refresh rate in TRMNL](https://help.trmnl.com/en/articles/10113695-how-refresh-rates-work#h_854b46ae51), battery lasts upwards of 30 days (~0.125% drain per hour). Longer intervals should perform even better.

### How it works

When deep sleep is enabled, the app follows this cycle:

1. **Display** — Shows fetched image for 5 seconds
2. **Screensaver write** — Copies the image to the NOOK's screensaver path
3. **Sleep** — Disables keep-screen-awake, turns off WiFi, sets RTC wake alarm
4. **NOOK sleeps** — After screen timeout (2 min), device enters deep sleep showing the screensaver
5. **Wake** — Wake alarm fires, WiFi reconnects, fetches next image, repeat

Power savings come from WiFi being off during sleep, the device entering true hardware sleep between refreshes, and e-ink displays consuming no power to maintain an image—only to update it.

### Configuration

Deep sleep is not enabled by default. To set it up:

1. **TRMNL app settings** — Enable "Allow Sleep" and "Write Screensaver"
2. **Nook display settings** — `Settings → Display → Screen timeout`: 2 minutes, then set Screensaver to "TRMNL"
3. **Hide screensaver banner** — In Nook apps: `Nook Touch Mod → Configure Mod Options`: Hide screensaver banner

## Roadmap
- [ ] Rubber cleaning and mounting options (https://github.com/bpmct/trmnl-nook-simple-touch/issues/11)
- [ ] Test on the Glowlight BNRV350 (https://github.com/bpmct/trmnl-nook-simple-touch/issues/6)

## Development
See the CI workflow for build details ([`build-apk.yml`](https://github.com/bpmct/trmnl-nook-simple-touch/blob/main/.github/workflows/build-apk.yml)), and the `tools/` adb scripts for build/install workflows. A development guide is coming (https://github.com/bpmct/trmnl-nook-simple-touch/issues/8). In the meantime, the project can be built with surprisingly minimal, self-contained dependencies.

## Disclaimer
AI was used to help code this repo. I have a software development background, but did not want to relearn old Java and the Android 2.1 ecosystem. Despite best-effort scanning and review, the device and/or this software may contain vulnerabilities. Use at your own risk, and if you want to be safer, run it on a guest network.
