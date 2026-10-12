# Local validation: 1.1.4

Validated on 10 October 2026. These are local results, not a claim that the GitHub workflow or physical-phone acceptance tests have run.

| Check | Result |
| --- | --- |
| Android debug build | Passed; APK generated |
| JVM unit tests | 37 passed, 0 failures/errors |
| Android lint | 0 errors, 41 warnings |
| Offline map regression check | Passed |
| Realtime Database emulator rules | 6 passed, including concurrent observer claims |
| Repository scan | No personal project ID, detected API-key/private-key strings, or developer absolute paths found in publishable files |
| Documentation relative links | All checked targets exist |
| Git ignore checks | APKs, build output, SDK path, Node dependencies and temporary helpers excluded |

Remaining lint warnings concern dependency/target SDK updates, orientation, wake-lock usage, WebView JavaScript, launcher resources and minor code recommendations. These were not suppressed or treated as proof of runtime reliability. The existing Android toolchain was retained to avoid an unrelated SDK migration.

The Firebase development-tool dependency audit reported **16 findings: 12 high, 4 moderate**. These are transitive dependencies of the CLI/rules-testing tools, not Android APK libraries. npm's proposed complete fix changes major versions; no forced downgrade or breaking fix was applied. Review upstream updates before using these tools with untrusted input. The root Playwright-tool installation reported no vulnerabilities at installation time.

The optional online browser map check was not rerun for this cleanup; the map implementation is unchanged. Lock-screen delivery, SOS audio and background behavior still require the two-phone acceptance checks in [the user guide](USER_GUIDE.md). New database rules were tested locally and have **not** been published to any live project.

## Changes in this preparation

- Removed the unused Google Services Gradle plugin and unused navigation dependency.
- Simplified config fields to `apiKey`, `databaseURL`, `projectId` and `appId`; older extra fields remain readable for compatibility.
- Removed unused imports/callback plumbing and exact-location/full-snapshot debug logging.
- Removed a duplicate Gradle wrapper directory and pinned the distribution checksum.
- Added project-neutral database rules, emulator tests, dependency lockfiles, CI and setup guides.
- Added repository ignore/format rules. Local APKs and caches are retained outside source control.

No FCM dependency, messaging service, Cloud Function or production Firebase project configuration is included. Existing observer monitoring and broadcaster services remain the app's background mechanism.
