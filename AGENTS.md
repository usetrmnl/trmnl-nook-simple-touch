# Agent notes (legacy Android / NOOK Simple Touch)

This repository targets **very old Android** and an **Eclipse ADT / Ant-era** workflow. When you propose changes, **do not suggest modern Android tooling or APIs** unless they are explicitly compatible with **Android 2.1 (API 7)**.

## Ground truth from this repo (do not “upgrade” casually)

- **Runtime target**: `minSdkVersion=7` and `targetSdkVersion=7` (Android 2.1 / Eclair)
- **API surface rule**: treat **API 7 as the maximum allowed API level** for code/design decisions, even if the project compiles against a newer SDK.
- **Compile target**: `target=android-20` (compile SDK used by the old tools; does *not* imply it’s OK to use API>7 at runtime)
- **Screen / UX target**: **600x800** (design/layout targeting this fixed resolution)
- **Project type**: Eclipse **ADT** project (`.project`, `.classpath`)
- **Support libs**: legacy `android-support-v4.jar` and an external `appcompat_v7` *library project* (`android.library.reference.1=../appcompat_v7`)
- **Common dev environment**: Android **ADT Bundle (2014-07-02)** + Eclipse (ADT-era)

Files that prove this:
- `AndroidManifest.xml`
- `project.properties`
- `.project` / `.classpath`
- `libs/android-support-v4.jar`

## Hard constraints for suggestions (the “don’t do modern stuff” list)

- **No Gradle / Android Studio migration** unless explicitly requested.
- **No AndroidX** (this project is pre-AndroidX, pre-Gradle).
- **No Kotlin**, no Jetpack Compose, no Jetpack “modern” libraries.
- **No runtime permissions**, notification channels, WorkManager, etc. (these are later Android concepts).
- **No Java 8+ language features** in app code (no lambdas, streams, `java.time`, etc.). Even if the machine has a newer JDK, the *Android toolchain/bytecode level* for this era is limited.
- **Assume old device constraints**: slow CPU, limited memory, e-ink refresh characteristics; prefer small dependencies and simple layouts.
- **Assume e-ink UX constraints**: prioritize **high contrast** and readability; avoid subtle greys, thin strokes, and animation-heavy UI.

## What *is* appropriate here

- Java code written in a **Java 5/6-style** (simple classes, no modern language features).
- Compatibility patterns like:
  - `Build.VERSION.SDK_INT` checks (already used in `SystemUiHider`)
  - Support library APIs from `android-support-v4` and `appcompat_v7` of this era
- Eclipse ADT / Ant-era project structure: `src/`, `gen/`, `res/`, `libs/`, `project.properties`
- Layouts designed for **600x800**:
  - Prefer deterministic layouts (avoid “phone/tablet responsive” complexity)
  - Use `dp`/`sp` and test for readability on e-ink

## Period-correct references (start here)

- **nookDevs wiki (community archive)**: `https://nookdevs.com/` (also `http://nookdevs.com/Main_Page`)
- **Device tree / low-level reference**: `https://github.com/NookSimpleTouchTeam/android_device_bn_nst`
- **Legacy app/version compatibility breadcrumbs**: `https://xdaforums.com/t/list-5-23-14-old-apps-for-nook-simple-touch.2690166/`
- **B&N retirement note (context for services)**: `https://help.barnesandnoble.com/hc/en-us/articles/19894497906715-Retired-NOOK-Devices`
- **Excellent practical reference (ADT bundle + JDK 8 + NST revival)**: `https://github.com/jonatron/trook/`

## Android docs guidance (important)

Modern `developer.android.com` pages often assume much newer Android versions. For API-7-era work:

- Prefer **the offline docs shipped with the legacy Android SDK** (historically under the SDK’s `docs/` directory).
- When in doubt, prefer *older* official docs and examples (ADT/Ant era) over modern Android Studio/Gradle guidance.

## Interpreting “Android 2.1 / API 7 for everything”

In practice:

- **API usage must be limited to API 7** unless there is a strict `Build.VERSION.SDK_INT` guard (and a fallback path).
- **Do not introduce** code, libraries, or UX assumptions that require newer Android versions.

## Build / tooling notes (practical, ADT-era)

This repo is meant to be worked on with **Eclipse ADT** and/or the old Android **Ant** tooling.

- **Known local environment (your setup)**:
  - ADT bundle: `adt-bundle-linux-x86_64-20140702/`
  - Eclipse: `adt-bundle-linux-x86_64-20140702/eclipse` (run this)
- **Pointing the project at your SDK**:
  - Create a `local.properties` (not committed) containing:
    - `sdk.dir=/absolute/path/to/adt-bundle-linux-x86_64-20140702/sdk`
- **SDK components to have installed** (via the ADT bundle’s SDK Manager):
  - Android SDK Platform **20** (compile target, matches `project.properties`)
  - Android SDK Platform **7** (API 7 / Android 2.1 — to verify APIs & docs)
  - Platform-tools (adb)

## If you want to provide offline docs/SDK artifacts

If you download/zip any old SDK bundles or docs, add a short note here with:

- the filename,
- the exact version number (tools/platform/build-tools),
- and where it should be installed/extracted.

