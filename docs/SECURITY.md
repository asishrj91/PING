# Security and data handling

## Access model

Each installation uses Firebase Anonymous Authentication. The broadcaster writes a current-state record at `active_sessions/<six-digit PIN>`. The optional `observer` child records the observer's anonymous UID, session ID and connection ID. A transaction coordinates a single observer; the supplied server rules enforce that ownership.

The rules in this repository deny root enumeration and unrelated paths. Only the broadcaster can write its location/battery/SOS. An observer can claim/release its own registration and cannot change those broadcast fields. A delayed release checks connection identity so it cannot remove a newer connection.

**This is a PIN-based personal-sharing design, not strong account-based authorization.** An authenticated client that guesses an unclaimed PIN can initially read/claim that session. There is no server-side PIN-attempt rate limiter, invitation approval, App Check enforcement or end-to-end encryption. Firebase project administrators can access server data. Do not present this as a hardened public multi-user tracking platform without strengthening these controls.

## Data sent and retained

- Realtime Database stores the current coordinates, accuracy, heartbeat/movement timestamps, battery status, SOS event identity, session ID and relevant anonymous user IDs. It is not a location-history or reliable alert-queue system.
- Stop Broadcast requests deletion of its record. Offline/process termination can prevent that deletion reaching the server.
- Firebase SDK and HTTPS connections provide transport protection; PING does not add end-to-end encryption.
- OpenStreetMap tile requests reveal the viewed map area and network address to the tile provider. Attribution remains visible on the map.
- Firebase configuration, remembered sessions and acknowledgement IDs are stored in Android private preferences. Android backup is enabled in the current manifest, so system backup policies may include app data.
- Exact GPS coordinates and full Firebase snapshots are not intentionally written to application debug logs. Avoid attaching private configs/session data to public bug reports.

## Repository hygiene

Firebase client API keys/project identifiers are not administrative credentials; access control depends on rules. This repository nevertheless uses placeholders so contributors don't accidentally connect to somebody else's project. See [Firebase API keys](https://firebase.google.com/docs/projects/api-keys).

Do not commit signing keystores, service-account private keys, OAuth tokens, Firebase CLI credentials or a real `google-services.json`. `.gitignore` excludes common local files, but inspect `git diff --cached` before every initial upload. An ignore rule cannot remove credentials already committed to history; revoke any exposed secrets and clean the history if that occurs.

Only share location with the broadcaster's consent. Keep PINs private, disconnect when finished, and use a dedicated Firebase project for testing.

## Dependency scope

The Android dependency versions are explicit in Gradle; lockfiles pin Node development tools. The Node tools run local rules/map tests and are not shipped in the APK. Firebase CLI/emulator dependencies can have upstream audit findings even on current releases. Review `npm audit --prefix firebase` before exposing developer tooling to untrusted input; do not automatically apply breaking `--force` fixes. No deployment service or Node server is hosted by this app.

If publishing through Google Play, separately review current target-SDK, foreground-service special-use declarations and privacy/distribution requirements. A successful debug build is not proof of store approval or a security audit.
