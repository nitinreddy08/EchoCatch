# Hardware runbook: OnePlus 11R and OnePlus Buds 4

Status: Draft v0.1 · 2026-10-20 · covers spikes S1, S2, S3, S5 and S6 from [04-spikes](../plan/04-spikes.md).

You do not need any technical knowledge. The test app is called **EchoCatch Lab**. It records from the microphone only while a test is running. It stores no audio and has no internet permission, so nothing can leave your phone unless you tap Share and choose where to send it.

Plan for about 45 minutes of active time (Parts 1 and 2), plus a few "leave the phone alone" waits (Part 3) and one optional long test (Part 4). You can do the parts on different days.

## Part 0. Install the app (5 minutes)

1. On a computer or on the phone, open your repository on GitHub, then **Actions**, then the newest run on the branch `claude/init-54nd5l` with a green tick.
2. Scroll to **Artifacts** and download **echocatch-lab-apk**. It is a zip file containing `spikes-debug.apk`.
3. Unzip it on the phone (Files app), tap the APK and allow "Install unknown apps" for the app you used to open it.
4. If Play Protect warns about an unknown developer, choose **Install anyway**. This is a test build signed with a debug key.
5. Open **EchoCatch Lab**, tap **Grant permissions** and allow microphone, nearby devices (Bluetooth) and notifications.
6. Tap **Scan devices**. You should see a list of audio devices. Connect the OnePlus Buds 4 first if they are not connected, then scan again.

Write down: the phone's Android version (Settings, About device).

## Part 1. S1: earbuds microphone and your music (15 minutes)

Goal: find out what happens to music when the app uses the earbuds' microphone, and which route works.

Do this with the Buds 4 in your ears.

1. Start music in another app (for example Spotify). Keep it playing.
2. In EchoCatch Lab, choose **Method = PREFERRED_DEVICE**, **Capture preset = VOICE_RECOGNITION**, **Seconds = 10**, **Sample rate = 16000**.
3. Tap each **Test mic** button in the list (phone microphone first, then any Bluetooth ones). While it records, **listen to your music** and say a few words normally.
4. When the app says "S1 done", tap how the music sounded: **good**, **phone-call quality**, or **stopped or paused**.
5. Repeat with **Method = COMMUNICATION_DEVICE**.
6. Optional extras if you have time: repeat step 2 to 4 with **Capture preset = UNPROCESSED** and with **Sample rate = 32000**.
7. Make sure you try at least once with the **podcast** in place of music.

Tip: if no Bluetooth microphone appears in the list, tell me. That is a useful result too.

## Part 2. S3: lowering your music for the alert (15 minutes)

Goal: find out which way of asking works best for your players.

1. Start a player (Spotify, YouTube, YouTube Music, a podcast app, or any other you use). Type its name in the **Player under test** box.
2. Choose **Focus type = GAIN_TRANSIENT_MAY_DUCK**, **Usage = ASSISTANCE_NAVIGATION_GUIDANCE**, **Content type = SPEECH**. Tap **Play alert now** and listen to what the music does.
3. Tap what you heard: **ducked**, **paused**, **ignored**, **did not resume**, or **alert hard to hear**. You can tap more than one.
4. Repeat for these combinations (one tap on each spinner, then Play alert):
   - GAIN_TRANSIENT_MAY_DUCK with NOTIFICATION and SONIFICATION
   - GAIN_TRANSIENT with ASSISTANCE_NAVIGATION_GUIDANCE and SPEECH
   - GAIN_TRANSIENT with ALARM and SONIFICATION
5. Do the whole thing again with a **podcast or audiobook** instead of music. Speech apps often behave differently.
6. Tap **Show active players** while something is playing, once for each player. This shows how the other app labels its sound.
7. Tap **Volume-duck fallback test** once. Note whether the music got quieter and came back.

If you have several players, test your two most-used ones in full and the rest with only the first combination.

## Part 3. S2 and S5: does the background service survive? (about 1 hour, mostly waiting)

Goal: find out how reliably a listening service can run on your OnePlus, and how the microphone behaves.

1. Set **Mode = cycle** and **Cycle every = 60**. Tap **Start service**. A notification "EchoCatch Lab running" appears.
2. Press **Home** and use the phone normally for 10 minutes. Then lock the screen and leave it for **30 minutes**.
3. Unlock and open EchoCatch Lab. Tap **Show last events**. Each line `mic_cycle` shows that the microphone opened in the background. Do not worry about reading the details.
4. Start music, put the Buds 4 in, and leave the service running for 10 more minutes. Take one earbud out and put it back. Then tap **Share report**.

Start paths to test (each one is a separate test):

- **From the notification.** Tap **Stop** in the notification. A new notification "EchoCatch Lab stopped" appears. Tap **Start** in it. Then check **Show last events** for `fgs_started` or `fgs_start_failed`.
- **From Quick Settings.** Swipe down twice, tap the pencil icon, drag the **EchoCatch Lab** tile into the top area. Tap the tile with the app **closed**. Then check the events for `tile_start_requested` or `tile_start_failed`.
- **After a reboot.** Restart the phone and **do not open the app**. After 5 minutes open it and tap **Show last events**. Look for `boot_completed` and `boot_start_failed`. Failure here is expected and useful.

Coexistence (S5): with the service running in **mic_open** mode, do each of these and tap **Show last events** after each one:

- start a phone call (or make one to a friend), then hang up
- use the voice assistant (for example "Hey Google")
- record a voice message in a chat app

Look for `capture_silenced_changed` and `recording_config_changed`. Also tell me whether the call or the assistant worked normally while the lab app was listening.

## Part 4. S6: battery cost (optional, 2 hours or more)

Goal: measure what listening costs. Only do this if you have the time. Use the same conditions for every run: screen off, Buds 4 connected, same music and volume, and the phone on a table (not charging), starting at the same battery level (for example 80 percent).

| Run | What to do | How long |
|---|---|---|
| R0 | Just play the music. Nothing else. Note the battery percent at the start and the end. | 1 hour |
| R1 | **Mode = idle**, tap **Start service**, then play the same music. | 1 hour |
| R2 | **Mode = mic_open**, **Read size = 100**, tap **Start service**, then play the same music. | 1 hour |
| R2b | Repeat R2 with **Read size = 20**, then with **200** | 1 hour each |

After each run tap **Show last events** (the app writes a `battery` line every minute) and tap **Share report**. Do not try to read the numbers yourself.

## Sending results back

After each part, tap **Share report (JSON, no audio)** at the bottom and send it to yourself (email or a notes app) or paste it into our chat. The report includes your phone model, Android version, the audio devices the phone sees, and the results of each test. It does not include audio, Bluetooth addresses, or your name.

Also write a few lines for me: what you heard in each test (for example "Spotify ducked, YouTube Music paused", "Buds 4 sounded like a phone call"), and anything surprising.

## Safety and tidy-up

- The volume-duck test lowers the music volume for about two seconds and then restores it. Press the volume buttons if something looks wrong.
- To stop everything: tap **Stop service** or **Stop** in the notification.
- To remove the app: uninstall **EchoCatch Lab** like any other app. It leaves nothing behind.
