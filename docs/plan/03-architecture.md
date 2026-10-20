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

## 3. Defaults chosen (challenge any of these with an ADR)

| Concern | Default |
|---|---|
| Language | Kotlin for the shell, C++17 for the hot path (ADR-0006) |
| UI | Jetpack Compose, Material 3 |
| Async | Kotlin coroutines for control only; the audio path never goes through coroutines |
| Settings | Jetpack DataStore |
| DI | Manual constructor injection wired in `app/` (small graph, fewer dependencies) |
| Build | Gradle (Kotlin DSL, version catalog); CMake for native |
| ABIs | `arm64-v8a` (device) and `x86_64` (emulator); add `armeabi-v7a` only if the audience needs it |
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
    val capabilities: SourceCapabilities   // native rate, expected quality, known side effects
    suspend fun open(): OpenResult         // establishes the route
    fun startCapture(sink: PcmSink)
    fun stopCapture()
    suspend fun close()                    // MUST release the route promptly (restores music quality)
}
```

`RouteMonitor` watches `AudioDeviceCallback` and emits the set of available inputs. A `SourceSelector` picks one based on the user's preference, what S1 proved about quality and battery, and what is available. All of that is policy data, not code branches buried in `AudioRecord` calls.

## 9. Permissions and manifest (ADR-0007)

Declared: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`. A CI check dumps the built APK's permissions and **fails if `INTERNET` or any permission outside the approved list appears**. Adding a permission requires editing the list in the same PR and writing its justification in `docs/plan/01-requirements.md`.

## 10. Failure behaviour

| Failure | Behaviour |
|---|---|
| Mic permission revoked at runtime | Stop, notify, return to Disarmed |
| Capture silenced (call, other recorder) | Go to ArmedIdle, retry with backoff, no error spam |
| Source disappears (earbuds removed) | Close route, reselect source, else ArmedIdle |
| Native pipeline error | Log counter, restart pipeline once, then ArmedIdle |
| Focus lost mid-cue | Finish or stop cue, release focus, Cooldown |
| Process death | Service does not silently restart a mic service from the background (P2/P4). The user sees the state as Disarmed and can re-enable in one tap. A "stopped unexpectedly" notification is posted if allowed. |

## 11. What is explicitly deferred

- Speaker/own-voice rejection (needs a separate ADR and a model).
- On-device training or adaptation of keyword models.
- Quick Settings tile and widget start surfaces (S2 decides).
- Wear OS companion.
