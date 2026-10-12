# Development and publishing

## Requirements

- Android Studio with Android SDK platform 34 and build tools 34.0.0.
- JDK 17 for the Android Gradle build (Gradle wrapper 8.7).
- Node.js 22 or later for optional JavaScript checks.
- JDK 21 or later for the optional Firebase database emulator.

Open `PingNative` in Android Studio. Let it create the ignored `local.properties` containing your SDK path. Set the Gradle JDK to 17. Android Studio normally supplies the SDK; a terminal build also needs `ANDROID_HOME` or `sdk.dir` in `local.properties`.

No Firebase account, configuration file, Google Services plugin, or production credentials are required to compile. Firebase configuration is entered on the phone after installation.

## Build and validate

From the repository root in PowerShell:

```powershell
Set-Location .\PingNative
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On Linux/macOS use `bash ./gradlew` instead. The APK is created at `PingNative/app/build/outputs/apk/debug/app-debug.apk`. It is a debug build signed with the machine's development key. Updates require the same signing key; independently built debug APKs may require uninstalling the previous app, which erases its local configuration and session state.

For distribution, use Android Studio's **Generate Signed Bundle / APK** workflow and maintain your own private signing key. Never commit the key or its passwords. An APK installed directly does not need a Play Store listing; publishing to Play requires a separate review of target SDK, foreground-service, location, and policy requirements.

### Map checks

From the repository root:

```powershell
npm ci
npm test
npx playwright install chromium
npm run test:map:browser
```

`npm test` is an offline map regression check. The browser check needs Internet access for OpenStreetMap tiles; it checks rendering, following, rotation, offline feedback and recovery. Alternatively set `PING_CHROME_PATH` to an installed Chromium/Chrome executable. Screenshots are written under ignored `PingNative/build/reports/map`.

### Database rules

Use JDK 21+ for this command (switch back to JDK 17 for Android):

```powershell
Set-Location .\firebase
npm ci
npm test
```

This starts a local Realtime Database emulator on port 9000 with the synthetic project `demo-ping-tests`. No login, billing account, or live database is involved. Tests cover ownership, observer exclusivity, concurrent claims, unauthorized writes, and field validation. The first run downloads the emulator.

Dependencies in `firebase/package.json` are local development tools, not deployed Cloud Functions. Review `npm audit` findings when updating them; do not use `npm audit fix --force` without reviewing compatibility.

## CI

`.github/workflows/ci.yml` runs Android unit tests, lint, a debug build, the offline map check, and database rule tests on pushes and pull requests. It does not deploy anything and requires no Firebase secrets. The browser tile check is deliberately manual because it depends on an external map service. A local successful run does not imply the GitHub-hosted workflow has already run.

## Push to GitHub

Create an empty GitHub repository, then run these commands from the PING repository root. If Git has not been initialized, first run `git init -b main`.

```powershell
git status --short
git add .
git diff --cached --stat
git diff --cached
git commit -m "Prepare PING Android app and setup documentation"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

Review the staged files before committing. APKs, build outputs, SDK paths, IDE caches, Firebase configuration downloads, private keys, and Node dependencies are ignored. The Gradle wrapper JAR and npm lockfiles belong in Git. Never add secrets with `git add -f`. If `origin` already exists, inspect it with `git remote -v` before changing it.

No license for PING's original code has been selected. Choose a license if you intend to grant others reuse rights; third-party license notices must be retained regardless. See [third-party notices](../THIRD_PARTY_NOTICES.md).

## Device acceptance checks

Automated checks cannot establish reliability under every vendor's background restrictions. Before sharing a release, use two phones to verify configuration, a single observer joining, battery display, GPS movement, stationary heartbeat, SOS and acknowledgment, screen rotation, returning from Recents, and recovery after Wi-Fi/mobile-data loss. Verify that Disconnect and Stop Broadcasting stop their respective services. See the [user guide](USER_GUIDE.md) for expected limitations and phone settings.
