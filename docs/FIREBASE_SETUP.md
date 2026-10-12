# Configure Firebase for PING

These steps configure the current listener-based app. There is no FCM sender, Cloud Function, service-account key or paid backend to deploy.

## 1. Create or select a project

1. Open the [Firebase console](https://console.firebase.google.com/).
2. Create a project, or select your existing PING project. Record its **project ID**, which can differ from its display name.
3. Google Analytics is optional and is not used by PING; it can be left disabled.
4. Keep the project on **Spark** if you want to avoid usage-based billing. This app does not require Blaze. If you already upgraded billing separately, changing the app does not change your billing plan.

Firebase's free-plan quotas still apply. Consult [current pricing and limits](https://firebase.google.com/pricing); the app cannot bypass a quota suspension.

## 2. Enable Anonymous Authentication

1. Open **Build → Authentication** and select **Get started** if necessary.
2. Open **Sign-in method** (sometimes shown under providers).
3. Enable **Anonymous**, then save.

PING signs each installation into Firebase anonymously. Users do not enter email/password credentials. Broadcaster ownership and the observer claim use this anonymous user ID. Clearing app data or uninstalling can lose that identity, so stop broadcasts and disconnect observers first.

See [Firebase's Android anonymous-auth guide](https://firebase.google.com/docs/auth/android/anonymous-auth).

## 3. Create a Realtime Database

1. Open **Build → Realtime Database → Create database**. Do not choose Cloud Firestore: PING uses Realtime Database.
2. Choose a suitable database region. Both phones connect to the same database, regardless of their location.
3. Start in **locked mode**. You will publish PING's access rules next.
4. Copy the database's base URL from its Data tab. Examples:

```text
https://YOUR_DATABASE_NAME.firebaseio.com
https://YOUR_DATABASE_NAME.europe-west1.firebasedatabase.app
https://YOUR_DATABASE_NAME.asia-southeast1.firebasedatabase.app
```

Use the exact URL shown by your console; do not guess the database name from the project ID. Do not append `.json`, `/active_sessions`, or Markdown link syntax. See [database locations and URL formats](https://firebase.google.com/docs/database/locations).

## 4. Publish the database rules

For a **new, dedicated PING database**:

1. Open this repository's [`firebase/database.rules.json`](../firebase/database.rules.json).
2. Copy the entire JSON object.
3. In Firebase **Realtime Database → Rules**, replace the locked-mode rules with that object.
4. Click **Publish**.

No terminal, Firebase CLI or billing upgrade is needed to paste rules in the console. Do not use a root rule granting everyone reads/writes, or a blanket `auth != null` write grant: parent grants override more restrictive child rules.

For an **existing database**, first save a copy of its rules. These rules describe PING only and deny other paths; merge carefully if another application uses the same database. Stop existing broadcasts and disconnect observers before migrating. Install the current app on both phones, publish compatible rules, then start a fresh broadcast. Older sessions without `owner_uid` and `session_id` cannot be adopted under these rules.

The rules allow:

- Only the authenticated broadcaster to create, update or delete its session.
- An authenticated client knowing an unclaimed PIN to initially read and claim it.
- Only the broadcaster and claimed observer to read that session after it is claimed.
- The observer to create/update/release its own `observer` record, without writing GPS, battery or SOS fields.

They do not provide strong protection against guessing an unclaimed six-digit PIN. See [security limitations](SECURITY.md).

## 5. Obtain the four app configuration values

For a new setup, use Firebase's **Android app registration** to obtain real Firebase options:

1. Open **Project settings → General → Your apps → Add app → Android**.
2. Use package name **`com.ping.app`**. If a matching Android app already exists, select it instead.
3. Register the app and download its `google-services.json` locally.
4. Find the `client` entry whose `client_info.android_client_info.package_name` is `com.ping.app`.
5. Copy the following values. Do **not** paste the complete downloaded file into PING.

| PING field | Source |
| --- | --- |
| `apiKey` | Matching client's `api_key[0].current_key` |
| `databaseURL` | Realtime Database Data tab; sometimes also `project_info.firebase_url` |
| `projectId` | `project_info.project_id` |
| `appId` | Matching client's `client_info.mobilesdk_app_id` |

`appId` is a Firebase identifier such as `1:123456789:android:abcdef123456`, not the package name `com.ping.app`.

Unlike Firebase's standard compile-time setup, **PING initializes Firebase from values saved on the phone**. Do not add the downloaded file to `PingNative/app`, install the Google Services Gradle plugin, or modify the source. Android registration here supplies configuration; it does not enable FCM or incur a functions deployment requirement. Reference: [Firebase Android setup](https://firebase.google.com/docs/android/setup).

If a previous Firebase config already works with PING, you can keep it. `apiKey` and `databaseURL` are the only fields the current parser strictly requires, but provide all four real values for a new setup. Old extra config keys are accepted and preserved as data; the app ignores them. The Fields form shows only the four active fields. `authDomain`, `storageBucket`, `messagingSenderId` and `measurementId` are not needed.

If your API key has Android application restrictions, ensure its allowed package and signing certificate match the APK actually installed. A locally built debug APK can have a different certificate from somebody else's APK. Review restrictions using [Firebase's API key guide](https://firebase.google.com/docs/projects/api-keys); do not substitute a service-account key.

## 6. Enter configuration in PING on both phones

Open **Database Setup → JSON** and enter the simplified object below, replacing every placeholder:

```json
{
  "apiKey": "YOUR_FIREBASE_API_KEY",
  "databaseURL": "https://YOUR_DATABASE_NAME.firebaseio.com",
  "projectId": "YOUR_PROJECT_ID",
  "appId": "YOUR_FIREBASE_APP_ID"
}
```

A copy is available in [`firebase/config.example.json`](../firebase/config.example.json). Alternatively, choose **Fields** and enter each value separately. Use the same project's values and database URL on both phones.

Tap **VERIFY & SAVE CONFIG**. This checks configuration and attempts anonymous sign-in. A successful save is not a full database-rules test; verify broadcasting and observing in the next steps. The database dot becoming green indicates a connection, not proof that every operation is allowed by your rules.

The current config remains editable in **Database Settings**. Stop broadcasting and disconnect the observer before changing projects. **Reset Config** clears this phone's saved config/session navigation; it does not delete your Firebase project or all server data.

## 7. Verify end to end

1. On the broadcaster, choose **Share Location**, allow required location access, and start broadcasting.
2. On the observer, choose **Track Someone** and enter that PIN.
3. Confirm map position, heartbeat, battery percentage and the observer's **Monitoring SOS** notification.
4. Activate SOS on the broadcaster and acknowledge it on the observer.
5. Repeat with the observer removed from Recents and its screen locked.

Continue with the [phone setup and troubleshooting guide](USER_GUIDE.md). No `firebase deploy --only functions` command is used by this version.

## Optional CLI deployment and local rules tests

The console method above is sufficient. Developers who prefer CLI tools can use Node.js 22+ and the pinned development dependencies:

```powershell
Set-Location .\firebase
npm ci
.\node_modules\.bin\firebase.cmd login
.\node_modules\.bin\firebase.cmd deploy --only database --project YOUR_PROJECT_ID
```

Replace `YOUR_PROJECT_ID` with your own ID. Run the commands from the `firebase` directory so the CLI finds `firebase.json`. No project-specific `.firebaserc` is committed. Tests use the local `demo-ping-tests` project and never need your account or live database:

```powershell
# Java 21+ must be available on PATH for the emulator.
npm test
```
