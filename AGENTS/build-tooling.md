# Build and tooling notes (ADT / Ant era)

## Local environment expectations

- ADT bundle root:
  `~/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/`
- Eclipse launcher:
  `~/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/eclipse`
- SDK directory used by tools:
  `~/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/sdk`

Point the project to the SDK with an untracked `local.properties`:
`sdk.dir=/absolute/path/to/adt-bundle-linux-x86_64-20140702/sdk`

## Build targets / properties

- Project target: `target=android-20` (compile target required by ADT bundle; runtime API remains 7)
- Minimum runtime API is still 7 (see `AndroidManifest.xml`)

## Runtime config storage

- API credentials and base URL are no longer stored in files.
- `ApiPrefs` persists them via `SharedPreferences`.

## Locating binaries (what we learned)

ADB (preferred order in scripts):
1. `ADB=/path/to/adb` (explicit env)
2. ADT bundle: `sdk/platform-tools/adb`
3. `adb` in PATH

Ant (used by ADT/Eclipse):
- Eclipse bundles Ant in:
  `eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant`
- The Android SDK `tools/ant/` directory is *not* an executable.
- You can pass `ANT=/path/to/ant` or use the script default.

AAPT / Build Tools:
- AAPT is resolved from SDK Build Tools, for example:
  `sdk/build-tools/android-4.4W/aapt`

Java compiler:
- Ant uses the JDK compiler from PATH.
- When using SDKMAN, ensure a JDK is active (`javac` available).
- Ant compiler adapter should be `modern` (default in scripts).

## Ant + aapt crunching issue

Symptom:
- `aapt` fails with `invalid resource directory name: .../bin/res/crunch`

Cause:
- Legacy Ant + aapt trip over the `bin/res/crunch` output folder.

Fix:
- Disable the `-crunch` target via `custom_rules.xml` (override target name).
- `custom_rules.xml` should remain in the repo.

## Helper scripts

- `tools/nook-adb.sh` supports:
  - `connect` - connect to NOOK via ADB-over-TCP
  - `install --apk <path>` - install APK
  - `build-install-run` - Ant build, install, launch app
  - `logcat` - stream filtered logcat
  - `shell` - open adb shell
- Use `--adb`, `--ant`, and `--logcat-filter` overrides as needed.

## Worktree setup

When working in a git worktree, you need:
1. `local.properties` - copy from another worktree or create with:
   `sdk.dir=/home/benpotter/Downloads/adt-bundle-linux-x86_64-20140702/adt-bundle-linux-x86_64-20140702/sdk`
2. SpongyCastle JARs in `libs/` - copy from another worktree or download per `libs/README_SPONGYCASTLE.md`

## Release flow

Release guidance now lives in `AGENTS/release.md`.
