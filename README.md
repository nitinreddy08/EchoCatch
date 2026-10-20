# EchoCatch

A free, open-source Android app that listens to your surroundings while you wear Bluetooth earphones, and lets you know when someone nearby calls your name. It briefly lowers or pauses your music or podcast and plays a short alert in your ears.

- **Offline.** Everything runs on the device. The app is built without the `INTERNET` permission.
- **Private.** Audio is held in memory only for a few seconds and is never stored or sent anywhere.
- **Efficient.** The microphone is opened only when it is useful, and heavy processing runs only when someone is speaking.
- **Broad support (goal).** Android 8.0 and newer, and any non-Apple TWS earbuds. The phone's own mic always works as a baseline. The earbuds' mic is used where the earbuds and phone allow it.

> **Status: planning.** There is no app to install yet. See [docs/README.md](docs/README.md) for the plan, architecture decisions, and roadmap.

> **Not a safety device.** EchoCatch may miss a call or alert falsely. Do not rely on it where missing a sound could put you at risk.

License: to be decided (see [open decisions](docs/plan/09-open-decisions.md)).
