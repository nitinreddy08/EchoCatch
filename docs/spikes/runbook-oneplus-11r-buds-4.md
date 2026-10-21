# Hardware runbook: OnePlus 11R and OnePlus Buds 4

Status: Draft v0.1 · 2026-10-20 · covers spikes S1, S2, S3, S5 and S6 from [04-spikes](../plan/04-spikes.md).

You do not need any technical knowledge. The test app is called **EchoCatch Lab**. It records from the microphone only while a test is running. It stores no audio and has no internet permission, so nothing can leave your phone unless you tap Share and choose where to send it.

Plan for about 45 minutes of active time (Parts 1 and 2), plus a few "leave the phone alone" waits (Part 3) and one optional long test (Part 4). You can do the parts on different days.

## What changed since the first run (version 2)

Your first report was very helpful and showed what to fix. The new build has **guided tests**: the app tells you when to start, then asks how the music sounded right after each test, so you cannot attach an answer to the wrong test. Reports are now compact so they fit in one chat message, and the app uses one fixed signing key, so future builds install over the old one.

**One-time step:** the version you have now was signed with a different key. Before installing version 2, tap **Share events only** in the old app if you want to keep its service events, then **uninstall EchoCatch Lab**, then install the new one. After this, updates install over the top.

## Part 0. Install the app (5 minutes)

1. On a computer or on the phone, open your repository on GitHub, then **Actions**, then the newest run on the branch `claude/init-54nd5l` with a green tick.
2. Scroll to **Artifacts** and download **echocatch-lab-apk**. It is a zip file containing `spikes-debug.apk`.
3. Unzip it on the phone (Files app), tap the APK and allow "Install unknown apps" for the app you used to open it.
4. If Play Protect warns about an unknown developer, choose **Install anyway**. This is a test build signed with a debug key.
5. Open **EchoCatch Lab**, tap **Grant permissions** and allow microphone, nearby devices (Bluetooth) and notifications.
6. Tap **Scan devices**. You should see a list of audio devices. Connect the OnePlus Buds 4 first if they are not connected, then scan again.

Write down: the phone's Android version (Settings, About device).

## Part 1. S1: earbuds microphone and your music (about 5 minutes)

Goal: find out whether the earbuds' microphone works, and what it does to your music.

1. Put the Buds 4 in your ears and connect them. Start music in another app (for example Spotify) and keep it playing.
2. In EchoCatch Lab tap **Scan devices**. The log should say "Earbuds microphone is listed."
3. Tap **Start the earbuds microphone test**. It runs four tests of 10 seconds each. Before each one a box tells you what is being tested. Tap **Start**, **keep talking** (read something aloud) and **listen to your music** while it records.
4. After each test a box asks how the music sounded. Choose the closest answer. If you are not sure, choose **Not sure**.
5. When it finishes, tap **Share report** at the bottom and paste it here.

Do it once with music and, if you have time, once more with a podcast playing.

Why this matters: the first run only tried one way of opening the earbuds' microphone, and it returned silence. The new test also tries the way that opens the Bluetooth call link, which may change your music's sound. That is exactly what we need to see.

## Part 2. S3: lowering your music for the alert (about 5 minutes per player)

Goal: find out which way of asking works best for your players.

1. Start a player (Spotify, YouTube, YouTube Music, a podcast app, or any other you use) and keep it playing.
2. In the lab app, type the **name of that app** in the box under step 3 (for example "Spotify"). It must be the app's name, not a person's name.
3. Tap **Start the alert test**. It plays four alerts. Before each one a box tells you what is being tested. Tap **Play alert** and **listen to what your music does**.
4. After each alert a box asks what happened. Tick everything that applies (for example "ducked" and "alert was hard to hear"), then tap **Done**.
5. Repeat with a **podcast or audiobook** app. Speech apps often behave differently, and they matter most.
6. Optional: under "Advanced", tap **Show active players** while something is playing, and tap **Volume-duck fallback test** once. Note whether the music got quieter and came back.

If you have several players, test your two most-used ones in full and the rest once each.

## Part 3. S2 and S5: does the background service survive? (about 1 hour, mostly waiting)

Goal: find out how reliably a listening service can run on your OnePlus, and how the microphone behaves.

1. Set **Mode = cycle** and **Cycle every = 60**. Tap **Start service**. A notification "EchoCatch Lab running" appears.
2. Press **Home** and use the phone normally for 10 minutes. Then lock the screen and leave it for **30 minutes**.
3. Unlock and open EchoCatch Lab. Tap **Show last events**. Each line `mic_cycle` shows that the microphone opened in the background. Do not worry about reading the details.
4. Start music, put the Buds 4 in, and leave the service running for 10 more minutes. Take one earbud out and put it back. Then tap **Share events only** and paste it here. (If the message is still too long, tap **Show last events** and send a screenshot of the log instead.)

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

## Part 5. Record test clips for the name detector (about 20 minutes)

This is the one part that keeps audio, so read this first: the clips are short recordings of voices. They stay inside the Lab app on your phone until you tap **Share**, and **Delete all clips** removes them. Only record people who agree. The EchoCatch app itself never stores audio (ADR-0012).

1. Open **EchoCatch Lab**, scroll to **Step 5**, tap **Open the clip recorder**. If it asks for the microphone, allow it.
2. Under **Who and where** type the name to detect (your name as people say it), who is speaking ("me"), and one **other name** that sounds a little like yours. Pick the place and the distance.
3. Tap a **Record** button. After a 3 second countdown it records 8 seconds. For name clips, wait for **SAY IT NOW**, say the name once the way a friend would call you, then stay quiet. Keep the screen in view.
4. Read the line under the level bar. If it says the clip was silent, too loud or too quiet, tap **Delete this clip and record it again**.
5. Aim for about 30 clips: 6 or more name clips in a quiet room at different distances (0.5 m, 1 m, 2 m, 4 m or from another room), 6 with a TV or music on, a few outdoors; 6 other-name clips; 6 talking clips (talk normally without saying your name); 4 background clips (say nothing). Name clips from a second person (change **Who is speaking**) are the most valuable.
6. Tap **Share clips (zip)** and send the zip to me by attaching it in our chat. If the chat refuses a file that size, share about 15 clips at a time: share, then **Delete all clips**, then record more.

The zip contains the clips, a `clips.jsonl` file that says what each clip is (kind, place, distance, speaker tag, name), and `session.json` with your phone model. I keep clips outside the repository, which is public, and I will not publish them.

## Sending results back

After each part, tap **Share report (JSON, no audio)** at the bottom and send it to yourself (email or a notes app) or paste it into our chat. The report includes your phone model, Android version, the audio devices the phone sees, and the results of each test. It does not include audio, Bluetooth addresses, or your name. (Test clips in Part 5 are separate and do contain voices.)

Also write a few lines for me: what you heard in each test (for example "Spotify ducked, YouTube Music paused", "Buds 4 sounded like a phone call"), and anything surprising.

## Safety and tidy-up

- The volume-duck test lowers the music volume for about two seconds and then restores it. Press the volume buttons if something looks wrong.
- To stop everything: tap **Stop service** or **Stop** in the notification.
- To remove the app: uninstall **EchoCatch Lab** like any other app. It leaves nothing behind.
