# ADR-0006: Build the audio and detection hot path as a native C++ core under a thin Kotlin shell

- **Status:** Accepted
- **Date:** 2026-10-20
- **Deciders:** project owner, with Claude as architect
- **Evidence:** platform facts P19, P20, P23, P24 (the libraries are native); S4 and S6 will confirm the cost model

## Context

The steady-state loop (capture → ring buffer → resample → VAD → keyword verifier) runs continuously, so allocation, garbage collection, and thread hops directly cost battery and latency. The main libraries (ONNX Runtime, sherpa-onnx) are native. We also want accuracy, latency, and CPU-per-audio-second tests that run in CI and in the cloud sandbox without a phone.

## Options considered

1. **All Kotlin**, calling the libraries through their JNI bindings. Simplest, but the loop would live in managed code, and the pipeline would be hard to run on a host.
2. **Native C++ pipeline, no Android dependencies**, with a thin JNI binding and a Kotlin shell for UI, service, policy, and alert control.
3. **Rust core.** Comparable benefits, but adds a second toolchain next to the C++ libraries we must link anyway.

## Decision

Option 2. `native/pipeline` is C++17 with no Android headers, so it builds on a Linux host for `tools/bench` and unit tests, and for Android ABIs for the app. The steady-state path allocates nothing and takes no locks. The audio thread talks to the control plane through a lock-free event queue. Optimization beyond the libraries' own bindings is done only where a profile shows a need.

## Consequences

- Two toolchains (Gradle and CMake) and a JNI boundary to maintain and test (contract tests in M2-T9).
- Host-testable accuracy and performance is the biggest gain for AI-agent development, since an agent can run the full benchmark in the sandbox.
- Native crashes are harder to debug than Kotlin exceptions, so the pipeline needs defensive checks and a restart-once policy (`03-architecture.md` §10).
- We will know this was wrong if the JNI boundary dominates the cost, or if the native code does little beyond calling a library.
