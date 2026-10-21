# 03 — Architecture

Status: Draft v0.1 · 2026-10-20 · decisions live in `docs/adr/`; this file describes the shape they produce.

## 1. Shape in one picture

```
 user / OS events                                   audio in                          audio out
 ───────────────                                    ────────                          ─────────
 UI start/stop ──┐                          ┌─ AudioSource ◄── phone mic / BT / LE / wired
 notification ───┤                          │      │ PCM chunks (≥100 ms)
 AudioDeviceCb ──┼─► ArmingPolicy ─ open/close ┘      ▼
 PlaybackCb ─────┘   (state machine)          native/pipeline (C++)
                         ▲                   ring buffer ─► resample 16k mono
                         │ state             ─► Stage 1: VAD
                         │                   ─► Stage 2: prefilter (only if S4 requires it)
                  ListeningService           ─► Stage 3: keyword verifier
                  (FGS, orchestrator)        ─► Decision (threshold, debounce)
                         ▲                          │ TRIGGER
                         └──────────────────────────┘
                                                     ▼
                                       AlertController: FocusController ─► CuePlayer ─► earphones
                                                     └─ cooldown, release focus, back to Listening
```

## 2. Modules

| Path | Language | Responsibility | Android deps |
|---|---|---|---|
| `app/` | Kotlin (Compose) | UI, onboarding, settings screens, manifest, manual DI wiring | yes |
| `core/model/` | Kotlin | Shared types, settings schema, event types | no |
| `core/audio/` | Kotlin (+ Oboe if ADR picks it) | `AudioSource` interface and implementations, `RouteMonitor` | yes |
| `core/detector/` | Kotlin | `Detector` API and JNI binding to the native pipeline | yes |
| `core/alert/` | Kotlin | `FocusController`, `CuePlayer` | yes |
| `core/policy/` | Kotlin | `ArmingPolicy` state machine (pure, table-tested) | no |
| `service/` | Kotlin | `ListeningService` foreground service, notification, orchestration | yes |
| `native/pipeline/` | C++17 | Ring buffer, resampler, VAD, keyword stages, decision | **no** (builds for host and Android) |
| `tools/bench/` | C++ | Host CLI: runs the pipeline on WAV files and reports accuracy, latency and CPU | no |
| `tools/corpus/` | Python or shell | Fetch, verify (checksums), mix datasets at target SNRs | no |
| `spikes/` | Kotlin | Throwaway M0 experiment apps; archived after M0 | yes |

**Why `native/pipeline` has no Android dependencies:** it compiles on a normal Linux host. That means every accuracy, latency, and CPU-time-per-audio-second test runs in CI and in the cloud sandbox without a phone. Only battery, Bluetooth, ducking, and OEM behaviour need hardware.

Module dependency rule: `app` → `service` → (`core/*`); `core/policy` and `core/model` depend on nothing Android; no `core/*` module depends on `service` or `app`.

**State of the module split (M1).** The Android code that will become `core/audio` and `service` currently lives in `:app`, in packages that follow the boundaries above (`audio`, `service`, `status`, `settings`, `platform`, `ui`). Splitting them into Gradle modules waits until a second consumer exists (the JNI binding in M2, the alert path in M3); until then a split would only add build cost. The pure Kotlin modules are already separate: `core/model` also holds `NameSettings` and `LevelMeter`, and `core/policy` also holds `PermissionStatus`, `SetupPlanner` and `ListeningStatus`, all unit-tested on the host.

## 3. Defaults chosen (challenge any of these with an ADR)

| Concern | Default |
|---|---|
| Language | Kotlin for the shell, C++17 for the hot path (ADR-0006) |
| UI | Jetpack Compose, Material 3 |
| Async | Kotlin coroutines for control only; the audio path never goes through coroutines |
| Settings | Jetpack DataStore |
| DI | Manual constructor injection wired in `app/` (small graph, fewer dependencies) |
| Build | Gradle 9.6 wrapper (Kotlin DSL, version catalog); Android Gradle Plugin 9.4 with built-in Kotlin; Kotlin 2.4; CMake for native |
| SDK levels | minSdk 26, compileSdk and targetSdk 36 (Play requires 36+ from 2026-08-31) |
| Local vs CI | Pure Kotlin/JVM modules (`core:model`, `core:policy`) and the native core build and test anywhere. Android modules need the Android SDK and are included only when one is found (`settings.gradle.kts`, override with `-Pechocatch.android=true|false`) |
| minSdk | 26 (Android 8.0), per ADR-0009 |
| ABIs | `arm64-v8a` and `armeabi-v7a` (devices), `x86_64` (emulator), per ADR-0009 |
| Native tests | GoogleTest |
| Kotlin tests | JUnit; Robolectric only where Android classes are unavoidable |
| Lint/format | Android Lint, detekt, ktlint, clang-format, clang-tidy |

## 4. Threading model

- **Audio thread** (one): owns capture and the native pipeline. No allocation, no locks, no logging in the steady state. If Oboe is chosen, this is the Oboe callback thread. If `AudioRecord` is chosen, it is one dedicated thread blocking on `read`.
- **Control plane:** coroutines on a single-threaded dispatcher inside `ListeningService`. It handles policy events, source open/close, and settings changes.
- **UI:** main thread. It reads state from a `StateFlow` and never touches audio.
- The audio thread talks to the control plane only through a lock-free event queue (TRIGGER, source-lost, error).

## 5. Pipeline contract (native)

- **Input:** PCM16 mono chunks from the source at its native rate. Each source asks the HAL for 16 kHz if possible; the native resampler converts otherwise.
- **Buffering:** a ring buffer of about 6 s (≈192 KB at 16 kHz, 16-bit). Audio is held in RAM only.
- **Stage 1, VAD:** works on 512-sample (32 ms) windows. On speech onset it marks the window start minus a 300 ms pre-roll, and ends the segment after a ~500 ms hangover.
- **Stage 2, prefilter:** *optional*. It exists only if S4 shows that VAD → Stage 3 exceeds the CPU or duty-cycle budget (ADR-0003).
- **Stage 3, keyword verifier:** runs only on VAD-gated segments, over the ring-buffer window, and emits a score per keyword.
- **Decision:** score ≥ threshold (from the sensitivity setting), plus a short debounce, emits TRIGGER. Cooldown lives in `AlertController`, not in the pipeline, so the pipeline stays stateless about alerts.
- **Keyword configuration:** a list of 1–3 keyword strings plus a threshold. How text becomes engine tokens is decided by ADR-0003 after S4.

## 6. Arming policy state machine (`core/policy`)

States:

```
Disarmed ──UserEnable──► ArmedIdle ◄──────── PolicyUnmet ──────────┐
   ▲                        │  PolicyMet (per arming mode)          │
   │ UserDisable (any)      ▼                                       │
   └──────────────────── Listening ──TRIGGER──► Alerting ──done──► Cooldown ──timeout──► (Listening | ArmedIdle)
                            │  source lost / error / capture silenced          
                            └────────────────► ArmedIdle (retry with backoff)
```

- **Disarmed:** no service, no notification.
- **ArmedIdle:** foreground service alive, **mic closed**, cheap listeners only (`AudioDeviceCallback`, `AudioPlaybackCallback`).
- **Listening:** mic route open and the pipeline running.
- **Alerting:** focus held, cue playing.
- **Cooldown:** capture continues, but TRIGGER events are ignored until the timer ends (ADR-0003, FR-5).

Inputs: `UserEnable`, `UserDisable`, `HeadsetConnected/Disconnected`, `PlaybackStarted/Stopped`, `RouteChanged`, `CaptureSilenced`, `SourceError`, `Trigger`, `AlertDone`, `CooldownElapsed`.

This module is pure Kotlin so it can be exhaustively table-tested without a device.

## 7. Alert path (`core/alert`)

1. On TRIGGER (outside cooldown), `FocusController` requests transient audio focus with the attributes and focus type chosen by ADR-0005.
2. `CuePlayer` plays a pre-loaded PCM cue from a pre-created low-latency `AudioTrack`. The cue is a short original sound (we author it ourselves, so no licensing issue), not text-to-speech (ADR-0008).
3. Safety bias: if focus is not granted within ~100 ms ⚠, play anyway. A missed alert costs more than a brief overlap.
4. On completion, abandon focus so the media resumes, then enter Cooldown.
5. Fallbacks for players that ignore focus (a temporary stream-volume duck) are evaluated in S3 and are **off by default**.

## 8. Audio source abstraction (`core/audio`, ADR-0002)

```kotlin
interface AudioSource {
    val id: SourceId                       // PHONE_MIC, BLUETOOTH_CLASSIC, BLE_AUDIO, WIRED
    fun open(): OpenResult                 // establishes the route; blocking, control thread only
    fun startCapture(sink: PcmSink)        // starts the dedicated capture thread
    fun stopCapture()
    fun close()                            // MUST release the route promptly (restores music quality)
}

interface PcmSink {
    fun onFrame(samples: ShortArray, count: Int)   // audio thread: no allocation, no locks, buffer reused
    fun onSourceError(error: SourceError)
}
```

As built in M1, `open` and `close` are plain blocking calls rather than `suspend` functions: the control plane is one dedicated thread, so nothing is gained by suspending, and a service can release the source from `onDestroy` without a coroutine. `SourceCapabilities` is added with the first headset source (M4). The phone-mic source uses the `MIC` audio source at 16 kHz in 100 ms hops; whether `VOICE_RECOGNITION` is better is an M2 measurement.

`RouteMonitor` watches `AudioDeviceCallback` and emits the set of available inputs. A `SourceSelector` picks one based on the user's preference, the stored `HeadsetProfile` for the connected headset (see §11), what S1 proved about quality and battery, and what is available. All of that is policy data, not code branches buried in `AudioRecord` calls.

## 9. Permissions and manifest (ADR-0007)

Declared: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`. The two that Android asks about (microphone, and notifications on Android 13+) are requested inside the app, one question at a time, after a plain-language disclosure; `SetupPlanner` decides the next step from live facts, so a permission switched off later brings its question back. A permission Android will no longer ask about is sent to the app settings page, which is the only part of setup that happens outside the app (as are Bluetooth pairing and any battery-manager steps). A CI check dumps the built APK's permissions and **fails if `INTERNET` or any permission outside the approved list appears**. Adding a permission requires editing the list in the same PR and writing its justification in `docs/plan/01-requirements.md`.

## 10. Failure behaviour

| Failure | Behaviour |
|---|---|
| Mic permission revoked at runtime | Stop, notify, return to Disarmed |
| Capture silenced (call, other recorder, an app with a visible UI) | Detected through `AudioRecordingCallback` / `isClientSilenced()` (P21a). Go to ArmedIdle, retry with backoff, no error spam. Never fight a call for the mic. |
| Source disappears (earbuds removed) | Close route, reselect source, else ArmedIdle |
| Native pipeline error | Log counter, restart pipeline once, then ArmedIdle |
| Focus lost mid-cue | Finish or stop cue, release focus, Cooldown |
| Process death | Service does not silently restart a mic service from the background (P2/P4). The user sees the state as Disarmed and can re-enable in one tap. A "stopped unexpectedly" notification is posted if allowed. |

## 11. Compatibility architecture (ADR-0009)

Goal: most Android versions (API 26+) and any non-Apple TWS. We cannot test everything, so the design guarantees a floor and detects the rest.

**Capability tiers for the microphone**

| Tier | Source | Works when | On failure |
|---|---|---|---|
| 0 (universal) | Phone mic, with the alert out of whatever output is active | Always, on every supported API level, with any earbuds | none; this is the floor |
| 1 | Headset mic over classic Bluetooth (HFP/SCO): legacy `startBluetoothSco` path below API 31, `setCommunicationDevice` from API 31 | The headset exposes a mic over HFP | fall back to Tier 0 |
| 2 | Headset mic over LE Audio via `setPreferredDevice` | Phone, OS, and headset all support LE Audio input | fall back to Tier 1, then Tier 0 |

**Rules**
- Feature detection only. No branching on phone or earbud model names. What differs between devices is expressed as capabilities (`SourceCapabilities`) and data (`HeadsetProfile`).
- **`HeadsetProfile`** (stored locally in DataStore, keyed by what Android reports about the output/input device such as type and product name): preferred source, measured sample rate, whether music survived the route, last test date. Created by the "Test my headset" wizard (FR-11) and used by `SourceSelector`.
- **API-level adapters:** code that differs by API level (Bluetooth route control, recording callbacks, notification and permission handling) sits behind small interfaces in `core/audio`, `core/alert`, and `service`, each with one implementation per behaviour range. Business logic never checks `Build.VERSION.SDK_INT` directly.
- **No proprietary dependencies** (no Play Services), so non-Google devices work.
- **Apple earbuds** are neither targeted nor excluded in code. They are generic Bluetooth devices.
- **Low-end devices:** the CPU budget (NFR-16) is measured on a low-end reference device, and models are chosen with that in mind.

## 12. What is explicitly deferred

- Speaker/own-voice rejection (needs a separate ADR and a model).
- On-device training or adaptation of keyword models.
- Quick Settings tile and widget start surfaces (S2 decides).
- Wear OS companion.
