# Release notes (agent guidance)

This document is **for agents to assist users** when they inevitably ask
“how do I cut a release?”. The agent should **not** perform release steps
unless the user explicitly requests it.

## Release flow (tags + GitHub Actions)

Releases are driven by annotated tags like `v0.1.0`. The CI workflow
`build-apk.yml` stamps the manifest version, builds a signed release APK,
and publishes a GitHub Release with the APK attached.

### Versioning nuance (when to bump major/minor/patch)

This project follows semantic-versioning *in spirit*:

- **Patch** (`0.x.Y`) for small fixes that should be low risk.
- **Minor** (`0.X.0`) when changes are significant, user-visible, or may affect
  server compatibility / settings (even if backwards compatible).
- **Major** (`X.0.0`) only for truly breaking changes.

In practice: if you find yourself writing release notes that include multiple
new capabilities, settings, or networking behavior changes, prefer a **minor**
bump so users expect meaningful change.

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

  - **Feature name** - Brief description of the change, explaining what was wrong and how it's fixed."
  ```
  Use past tense for fixes ("Fixed X") and describe both the problem and solution.

## Required GitHub secrets (release signing)

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
