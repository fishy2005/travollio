# Travollio

A Fable Mono travel companion. Native Android (Kotlin, Jetpack Compose, Room).

## In-app updates (Settings tab)

The app can scan for and install new builds from the **Settings tab** ⟶ **Scan for
updates**. This is how new versions of the app reach your phone without a full
reinstall.

How the pipeline works:

- Each signed APK is published as a **GitHub Release** asset, tagged
  `v{versionCode}-{versionName}` — e.g. `v4-0.3.2`.
- On your phone, the app calls the GitHub **Releases API**
  (`releases/latest`), parses the tag for the version, and compares `versionCode`
  against its own `BuildConfig.VERSION_CODE`.
- If newer, it downloads the `.apk` asset to `cacheDir/updates/` and hands it to
  Android's package installer via `FileProvider`.
- The update **installs over the existing app** (data is kept) because the new APK
  is signed with the **same certificate** as the installed one — the `signingConfigs.release`
  block in `app/build.gradle.kts` uses this box's debug keystore by default.

### One-time GitHub setup (once)

1. Create a GitHub account + a token with `repo` scope:
   `Settings → Developer settings → Personal access tokens → Generate new token (classic)`.
2. Put the token somewhere the scripts can read it:
   `~/travollio/.gitignore` already excludes `*.local.properties`, so a friendly
   home for it is `~/.config/travollio/token`.
3. Set repo identity + create the repo + push:
   ```bash
   OWNER=your-github-username REPO=travollio scripts/setup.sh
   ```
   This git-inits, creates the (public) GitHub repo, writes `github.local.properties`,
   and pushes.

### Build + publish a new release

```bash
scripts/release.sh                      # bump patch version (0.3.1 → 0.3.2, code+1)
scripts/release.sh 5 0.4.0              # explicit versionCode + versionName
scripts/release.sh 6 0.4.1 "Fixed X"    # with release notes
MANDATORY=1 scripts/release.sh          # mark it [mandatory] in-app
```

`release.sh` bumps the version in `app/build.gradle.kts`, builds the signed release
APK, tags it, and pushes a GitHub Release with the APK asset. Then on your phone:
**Settings → Scan for updates → Download → Install.**

### Build toolchain

- Gradle 8.10.2 (`~/gradle-8.10.2/bin/gradle`) — **must run on JDK ≤ 23**. The box's
  default is JDK 25, which Gradle 8.10.2 can't run. A self-contained JDK 21 lives in
  `~/.jdks/jdk-21.0.12.1+1`; `release.sh` picks it up automatically (or set `JAVA_HOME`).
  ```bash
  JAVA_HOME=~/.jdks/jdk-21.0.12.1+1 ~/gradle-8.10.2/bin/gradle :app:assembleRelease
  ```

## Signing

By default the release is signed with the debug keystore
(`~/.android/debug.keystore`) so upgrades match the current sideload. For a real
deploy per-build signature, create `signing.local.properties` (gitignored):
```
keystore=/absolute/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```
**Never** change the signing key once a version is in the field — Android will
refuse to update over a differently-signed install.

## Agent configuration

`gemini.local.properties` (gitignored) — see the comments in `app/build.gradle.kts`.
