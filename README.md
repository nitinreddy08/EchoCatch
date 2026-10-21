# EchoCatch

A free, open-source Android app that listens to your surroundings while you wear Bluetooth earphones, and lets you know when someone nearby calls your name. It briefly lowers or pauses your music or podcast and plays a short alert in your ears.

- **Offline.** Everything runs on the device. The app is built without the `INTERNET` permission.
- **Private.** Audio is held in memory only for a few seconds and is never stored or sent anywhere.
- **Efficient.** The microphone is opened only when it is useful, and heavy processing runs only when someone is speaking.
- **Broad support (goal).** Android 8.0 and newer, and any non-Apple TWS earbuds. The phone's own mic always works as a baseline. The earbuds' mic is used where the earbuds and phone allow it.

> **Status: early development. The app does not detect names yet.** The current build walks through setup inside the app (an explanation, the microphone and notification questions, your name), then starts a microphone service with a notification, a Stop button and a level meter, so the foundations can be tested. Detecting your name and pausing your music are the next milestones. See [docs/README.md](docs/README.md) for the plan, architecture decisions, and roadmap.
>
> **Trying the debug build.** Open the latest successful run of the [CI workflow](https://github.com/nitinreddy08/EchoCatch/actions/workflows/ci.yml) on GitHub (sign in), download the `app-debug-apk` artifact at the bottom of the run page, unzip it and install `app-debug.apk` (Android asks to allow installs from the app you opened it with). CI builds, lints and checks its permissions; it has not yet been run on a phone by the maintainers, so expect rough edges. Please report what you see.

> **Not a safety device.** EchoCatch may miss a call or alert falsely. Do not rely on it where missing a sound could put you at risk.

License: [GPL-3.0-only](LICENSE).
