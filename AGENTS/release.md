# Release notes (agent guidance)

This document is **for agents to assist users** when they inevitably ask
“how do I cut a release?”. The agent should **not** perform release steps
unless the user explicitly requests it.

## Release flow (tags + GitHub Actions)

Releases are driven by annotated tags like `v0.1.0`. The CI workflow
`build-apk.yml` stamps the manifest version, builds a signed release APK,
and publishes a GitHub Release with the APK attached.

Suggested guidance to give users:
- Ensure `main` is clean and up to date.
- (Optional) Stamp the manifest locally for traceability:
  - `./tools/set-version.sh v0.1.0` (uses `python3`)
  - Commit the version bump.
- Create and push the tag:
  - `git tag v0.1.0`
  - `git push origin main --tags`
- Wait for the **tag** CI run (not the branch run). Both trigger, but only the
  tag run (`refs/tags/v*`) creates the GitHub Release. Use `gh run list` and
  look for the run with the tag ref (e.g., `v0.1.0`), then `gh run watch <id>`.
- After CI completes, update release notes with a "What's New" section:
  ```
  gh release edit v0.1.0 --notes "## What's New

  - **Feature name** - Brief description of the change."
  ```

## Required GitHub secrets (release signing)

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
