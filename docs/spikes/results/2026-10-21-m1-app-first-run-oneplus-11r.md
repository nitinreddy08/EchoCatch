# M1 app, first run on the owner's phone (OnePlus 11R, Android 16)

Date: 2026-10-21 · build: EchoCatch app 0.1.0-m1 from CI run 39559165548 · source: three screenshots and two messages from the owner. Nothing here was measured by an instrument; it is what the owner saw.

## What worked

- Setup inside the app finished: microphone and notification permissions both showed "Allowed" in Settings, and the name, nicknames and "how it sounds" spelling were saved (Home showed "Listening for: ...").
- The microphone service started from the Start button. The status showed "Listening", the notification appeared with a working-looking Stop button, and Android's green microphone dot was visible in the status bar.
- The level meter showed a non-silent reading (about -40 dB, peak -30 dB), so audio frames were arriving from the phone microphone.
- **Screen lock:** with the screen locked, the notification and the microphone dot stayed, and the owner's music (a browser playing through Bluetooth earbuds) kept playing. The owner reported listening continued while locked.

## What went wrong

- **Removing the app from recent apps stops listening.** The owner reported that swiping EchoCatch out of recent apps removed the notification and closed the microphone. This answers the S2/S5 question for this phone (ADR-0004's open point, and risks R3 and R19 in `docs/plan/07-risks.md`): on OnePlus devices the swipe ends the process, including a foreground service, which stock Android does not do. An app cannot restart itself after that, and ADR-0004 deliberately makes the service non-sticky.

## Not yet tested

- Whether locking the app in recent apps, or allowing background activity in its battery settings, prevents the stop (the owner was asked to try the lock first).
- Sound quality of music on the earbuds while listening (the phone microphone should not change it, but this was not checked).
- The Stop button, the "stopped" notification, and how the status screen reads after an unexpected stop.
- Anything on other phones.

## What changed because of it

- The status screen wording for an unexpected stop now names removal from recent apps as a common cause.
- Settings has a "Keep listening running" section (lock in recents, allow background activity) with a button to the battery settings; Home shows a short tip while listening. EchoCatch still does not request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (ADR-0011).
