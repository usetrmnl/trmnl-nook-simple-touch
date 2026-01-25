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

## E‑ink UI notes (high contrast)

- Prefer **black text on white background** for critical/placeholder content; avoid colored text (it often ends up low-contrast on e‑ink).
- Avoid **semi-transparent overlays** (they tend to render as muddy greys).
- Avoid **double outlines** (e.g., both a container border and a button border) since they can look like unintended “extra lines” when tapping/refreshing.

## Period-correct references (start here)

- **nookDevs wiki (community archive)**: `https://nookdevs.com/` (also `http://nookdevs.com/Main_Page`)
- **Device tree / low-level reference**: `https://github.com/NookSimpleTouchTeam/android_device_bn_nst`
- **Legacy app/version compatibility breadcrumbs**: `https://xdaforums.com/t/list-5-23-14-old-apps-for-nook-simple-touch.2690166/`
- **B&N retirement note (context for services)**: `https://help.barnesandnoble.com/hc/en-us/articles/19894497906715-Retired-NOOK-Devices`
- **Excellent practical reference (ADT bundle + JDK 8 + NST revival)**: `https://github.com/jonatron/trook/`

## Android docs guidance (important)

Modern `developer.android.com` pages often assume much newer Android versions. For API-7-era work:

- Use the **local Android 2.1 (API 7) offline docs in this workspace** as the primary/required reference: start at `android-2.1-api-docs/index.html`.
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

## TLS/SSL limitations on Android 2.1 (API 7)

**Critical constraint**: Android 2.1's OpenSSL library only supports up to **TLS 1.0** (and SSLv3). It **cannot** negotiate TLS 1.2 or TLS 1.3, which most modern APIs require.

### What this means:
- **HTTPS to modern APIs will fail** with SSL handshake errors (e.g., `SSL23_GET_SERVER_HELLO:sslv3 alert handshake failure`)
- This is a **system library limitation**, not something fixable in app code
- TLS 1.2 support was added in Android 4.1 (API 16)

### Workarounds:
1. **HTTP fallback**: If the API supports HTTP, automatically fall back when HTTPS fails
2. **Proxy**: Use a local proxy that accepts TLS 1.2+ and speaks TLS 1.0 to the NOOK
3. **Different API**: Use APIs that still support TLS 1.0 (rare)

### BouncyCastle TLS 1.2 workaround (SpongyCastle 1.58)
This repo includes a **manual TLS client** using SpongyCastle to reach modern HTTPS endpoints on API 7.

Key points:
- **TLS 1.2 only** (SpongyCastle 1.58 does not support TLS 1.3).
- Uses `TlsClientProtocol` with a **manual HTTP request/response** (no `HttpURLConnection`).
- Adds **SNI** and `extended_master_secret` to satisfy modern servers.
- Sets an explicit **cipher suite list** that prioritizes ECDHE_RSA + AES (GCM/CBC).

#### Shortcuts (intentional for now):
- **Trust‑all server certs** (no CA validation or pinning).
- **No hostname verification** beyond SNI (because we bypass JSSE).
- **Limited cipher list**; may need adjustment for other servers.
- **No ALPN/HTTP2** (HTTP/1.1 only).

#### Where implemented:
- `src/com/bpmct/trmnl_nook_simple_touch/BouncyCastleHttpClient.java`

#### Setup / install (SpongyCastle 1.58)
This project uses **SpongyCastle 1.58.0.0** (repackaged BouncyCastle) because Android 2.1’s bundled BC is too old.

1. Download JARs into `libs/`:
   - `https://repo1.maven.org/maven2/com/madgag/spongycastle/core/1.58.0.0/core-1.58.0.0.jar`
   - `https://repo1.maven.org/maven2/com/madgag/spongycastle/prov/1.58.0.0/prov-1.58.0.0.jar`
   - `https://repo1.maven.org/maven2/com/madgag/spongycastle/bctls-jdk15on/1.58.0.0/bctls-jdk15on-1.58.0.0.jar`

2. Ensure JARs are available on the classpath (ADT/Ant picks up `libs/*.jar` automatically).

3. Rebuild the APK.

Notes:
- JARs are **gitignored** (large, local-only).
- SpongyCastle 1.58 provides **TLS 1.2**, not TLS 1.3.

### Implementation notes:
- Custom `TrustManager` can bypass certificate validation, but **cannot** upgrade the TLS protocol version
- Always implement HTTP fallback for API calls on API 7
- Log SSL errors comprehensively for troubleshooting (see `TRMNLAPI` tag in logcat)

## Network API patterns for API 7

### HTTP/HTTPS requests:
- Use `HttpURLConnection` (available in API 7)
- Always call `connect()` explicitly before `getResponseCode()` on API 7
- Set reasonable timeouts (15-20 seconds)
- Handle `-1` response codes (connection failures)
- Implement retry logic for transient failures

### Error handling:
- Log all errors to logcat with full stack traces
- Display user-friendly error messages on screen
- Check for SSL errors specifically and trigger HTTP fallback

### Example pattern (see `FullscreenActivity.java`):
```java
// Try HTTPS first
Object result = fetchUrl(httpsUrl, true);

// If HTTPS fails with SSL error, try HTTP fallback
if (result != null && result.toString().contains("SSL") && httpUrl != null) {
    result = fetchUrl(httpUrl, false);
}
```

## If you want to provide offline docs/SDK artifacts

If you download/zip any old SDK bundles or docs, add a short note here with:

- the filename,
- the exact version number (tools/platform/build-tools),
- and where it should be installed/extracted.

