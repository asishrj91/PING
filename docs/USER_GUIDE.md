# Install and use PING

## Before installing

- Use Android 8.0/API 26 or newer. The broadcaster needs Google Play services for fused location, GPS/location enabled, and internet. Both phones need an up-to-date Android System WebView and internet for Firebase/map tiles.
- Configure your Firebase project using [Firebase setup](FIREBASE_SETUP.md). Have its four configuration values ready.
- Use the current version on **both** phones. Older broadcasters can lack fields required for battery display, observer registration and background SOS.

## Install the APK

1. Obtain the APK from a trusted repository release or [build it yourself](DEVELOPMENT.md).
2. Transfer it to each phone and open it. Android may ask you to allow installations from that browser/file manager; allow it only for the trusted installation source.
3. Open PING once after installation. Installing an APK alone does not start sharing or monitoring.
4. Allow notifications. On the broadcaster, allow precise location and the background/"Allow all the time" location access needed for continued broadcasting. The current app requests location permissions at startup even on an observer; its observer service itself does not request GPS fixes.
5. Android may send you to App info to grant background location separately. Setting names vary by Android/vendor version.

For an update, install over the previous APK using the same signing key and a higher version code. If Android reports an incompatible package/signature, do not immediately uninstall: stop sharing/disconnect and record your config first. A build from another computer may use a different debug certificate. Uninstalling clears local config, session state and anonymous identity.

## Database Setup on both phones

1. Paste the simplified JSON from [Firebase setup](FIREBASE_SETUP.md), or select **Fields** and fill the four entries.
2. Both phones must use the same database URL and Firebase project. Do not enter your Google account password or a service-account private key.
3. Tap **VERIFY & SAVE CONFIG**. Successful anonymous sign-in returns you to the landing page.
4. Check that the database indicator connects. If it stays disconnected, confirm internet access and the database URL.

You can view/edit the saved values later under **Database Settings**. Disconnect and stop sharing before changing projects. **Reset Config** asks for confirmation and resets this phone; it does not delete the Firebase project.

## Set up background operation

On **both phones**, open Android **Settings → Apps → PING**:

- Allow notifications. Keep **SOS alerts** enabled on the observer.
- Set battery use to **Unrestricted**, or enable the vendor's equivalent background-running permission. If offered, allow auto-start/background activity.
- Allow background data; if Data Saver is on, allow unrestricted data for PING as needed.
- Check that Wi-Fi or mobile data stays connected when the screen locks. PING cannot deliver data while the phone has disconnected its network.

On CMF/Nothing OS and Realme, the names and locations of these settings differ. PING cannot force these selections during installation. A persistent foreground notification helps keep the service running; it is not a guarantee against vendor power management.

Monitoring keeps a partial wake lock and a Firebase connection active, so battery use is higher than a push-message-only design. There is no pause/resume tracking feature in this version.

## Broadcaster: Share Location

1. Select **Share Location**.
2. Start broadcasting and share the displayed six-digit PIN with the intended observer privately.
3. Keep **Location sharing active** visible in notifications. Tapping it opens the broadcast controls.
4. The first usable fix sends coordinates. Further detected movement greater than 5 metres from the last successfully published fix sends another location; fixes are requested approximately every 10 seconds.
5. While stationary, a heartbeat is scheduled approximately every 30 seconds after a successful publication. Pending/offline writes, Android scheduling and GPS accuracy can delay updates. Battery percentage and charging state accompany these existing writes.
6. To finish, choose **Stop Broadcast** and confirm. This requests removal of the active server session. Back only leaves the screen; it does not stop broadcasting.

Five metres is a GPS-based threshold, not a guaranteed physical measurement. Indoors, poor satellite reception and position noise can affect movement detection.

## Observer: Track Someone

1. Choose **Track Someone**, enter the broadcaster's PIN and connect.
2. The map centres on the last reported location. The foreground service claims that session for this observer. A second observer is rejected while the claim remains active.
3. Look for the silent **Monitoring SOS** notification. **SOS monitoring · reconnecting** indicates it is not yet connected/ready. Tap an in-app monitoring warning for details.
4. The top status pill shows heartbeat/PIN and movement; the bottom row has **Back arrow → broadcaster battery → Disconnect**.
5. Tap the coordinates/**RECENTER** control to return to the broadcaster's marker after moving the map.

### Status labels

| Display | Meaning |
| --- | --- |
| LIVE + green pulse | A heartbeat/location publication is fresh and the database is connected. |
| WAITING | No heartbeat has arrived yet, or the database is disconnected before the stale threshold. |
| 40s AGO, 2m AGO, etc. | The last reported heartbeat is at least 40 seconds old. |
| NONE YET | The first position is known, but no subsequent movement publication has been recorded. |
| MOVING | Two distinct recent movement timestamps are within 30 seconds of each other, and the newest is under 30 seconds old with a fresh heartbeat. |
| Movement age | Time since the last reported movement; this is separate from heartbeat age. |
| BAT 72% / charging symbol | Last battery report from the broadcaster, not the observer's own battery. It dims when heartbeat is stale and turns red at 20% or lower. |
| BAT -- | No usable battery report is available. |

### Back, Recents and Disconnect

- **Back arrow:** returns to the landing page, preserving the PIN and background monitoring. Track Someone reopens the saved map session.
- **Remove from Recents:** does not request service shutdown. Monitoring continues if Android keeps the foreground service/network alive.
- **Reopen PING:** saved role/session navigation is restored. The map's database listener reattaches; cached data may appear first, followed by server updates when connected. There is no deliberate 30-second observer refresh wait, but fresh broadcaster data still depends on its publication schedule and network.
- **Disconnect:** confirms, stops the local monitor/alarm and requests removal of its observer claim. The next connection requires a PIN.
- **Disconnect offline:** stops local monitoring immediately, but server release is not guaranteed before connectivity/process lifetime ends. If a stale observer claim prevents another phone joining, stop and restart the broadcaster online to create a fresh session. Do not clear another live observer's claim without the broadcaster's intent.

## SOS and acknowledgement

1. On the broadcaster, activate **SOS** while sharing.
2. The observer's running service receives the SOS record and plays the selected SOS-channel sound and repeating vibration. It works independently of the map activity.
3. Acknowledge using **ACKNOWLEDGE SOS** in the notification shade or the map. This silences the observer locally; it does not turn off the broadcaster's SOS flag. The map may show **SOS ACKNOWLEDGED · STILL ACTIVE**.
4. The broadcaster can cancel SOS; ending the broadcast also stops its observer alarm once the change is received.
5. A new SOS activation has a new event ID and can alert again. Returning to the map does not replay an already acknowledged event.

Set the observer's tone in Android **App info → PING → Notifications → SOS alerts → Sound**. The app reads this channel's selected tone/vibration preferences. Ensure alarm volume is audible; silent channel settings, Do Not Disturb and vendor restrictions can affect alerts. The app does not force maximum volume.

The notification itself is silenced to prevent two overlapping tones; PING owns the looping playback. This may suppress Android's heads-up banner, but the notification-shade actions remain. PING does not forcibly open a full-screen map over other apps.

## Limits to understand

- Android **Force stop** and **Active apps → Stop** are explicit stop actions. Open PING again to resume.
- There is no automatic boot receiver: open PING after restarting the phone.
- A service notification does not prove the network is connected or the latest data reached Firebase.
- An SOS that starts and ends entirely while the observer is offline is not queued for later replay. The database stores current state, not an alert history.
- The current tracking record remains on Firebase until its deletion reaches the server. Abrupt shutdown/uninstall/offline stop can leave a stale record.

## Troubleshooting

| Problem | Check |
| --- | --- |
| Config verification fails | Anonymous provider enabled; valid API key; internet; same-project values; applicable API key restrictions. |
| Save works but tracking is denied | Realtime Database rules were published; correct PIN; other observer already registered; old session schema after rules migration. |
| Map is blank/tiles fail | Update WebView; allow access to `tile.openstreetmap.org`; tap the map retry message. Coordinates can remain available during tile failures. |
| No current location | Broadcaster is actually sharing; precise/background location permissions; system Location enabled; wait outdoors for a fix. |
| Heartbeat becomes stale when locked | Check both phones' background battery/data settings and whether Wi-Fi disconnects. A stationary broadcaster should still publish heartbeats while connected. |
| No SOS after closing observer | Confirm monitoring notification before closing; allow notifications/background use; check network and Force stop state. |
| Two phones cannot observe | Only one observer is supported. Disconnect the original online, or restart the broadcaster to clear an orphaned claim. |
| App cannot install over an older build | Compare signing certificates/version codes; see the update instructions above before uninstalling. |

## Phone acceptance checklist

- [ ] Both phones use the same project/database and current app version.
- [ ] First location, movement, stationary heartbeat and broadcaster battery are visible.
- [ ] One observer joins; another is rejected while that claim exists.
- [ ] SOS loops with the map open and stops on acknowledgement.
- [ ] SOS works with the observer removed from Recents and locked; notification acknowledgement stops it.
- [ ] Broadcaster cancellation stops the alarm; a new SOS alerts again.
- [ ] Reopening after acknowledgement does not replay that same SOS.
- [ ] Temporary network loss shows stale/reconnecting status and recovers when the network returns.
- [ ] Disconnect removes monitoring; a different observer can then connect.

Repeat on the actual phones you intend to use. Desktop tests cannot validate CMF/Nothing OS or Realme locked-screen behavior.
