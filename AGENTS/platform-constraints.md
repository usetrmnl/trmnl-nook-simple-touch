# Platform constraints (API 7 / NOOK Simple Touch)

## Ground truth from this repo (do not upgrade casually)

- Runtime target: `minSdkVersion=7` and `targetSdkVersion=7` (Android 2.1 / Eclair)
- API surface rule: treat API 7 as the maximum allowed API level, even if the
  project compiles against a newer SDK.
- Compile target: `target=android-20` (compile SDK used by old tools only)
- Screen / UX target: 600x800 (fixed resolution)
- Project type: Eclipse ADT project (`.project`, `.classpath`)
- Support libs: legacy `android-support-v4.jar` and external `appcompat_v7`
  library project (`android.library.reference.1=../appcompat_v7`)

Files that prove this:
- `AndroidManifest.xml`
- `project.properties`
- `.project` / `.classpath`
- `libs/android-support-v4.jar`

## Hard constraints (do not modernize)

- No Gradle / Android Studio migration unless explicitly requested.
- No AndroidX.
- No Kotlin, Jetpack Compose, or modern Jetpack libraries.
- No runtime permissions, notification channels, WorkManager, etc.
- No Java 8+ language features in app code (no lambdas, streams, `java.time`).
- Assume old device constraints: slow CPU, limited memory, e-ink refresh.

## What is appropriate here

- Java 5/6 style code.
- Compatibility patterns like `Build.VERSION.SDK_INT` checks.
- ADT-era project layout: `src/`, `gen/`, `res/`, `libs/`, `project.properties`.
- Layouts designed for 600x800 using `dp`/`sp`.

## E-ink UI notes (high contrast)

- Prefer black text on white background for critical content.
- Avoid semi-transparent overlays (they render as muddy greys).
- Avoid double outlines (container border + button border).
