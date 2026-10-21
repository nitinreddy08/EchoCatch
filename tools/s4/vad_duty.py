#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Measure how much of the audio a voice-activity gate would let through (the "duty cycle", NFR-6).

Usage: vad_duty.py --data DIR --vad-model silero_vad.onnx [--out FILE.json]

The gate opens at speech onset with a pre-roll and stays open for a hangover (ADR-0003). The duty cycle is the share
of audio inside open gates; "name covered" is the share of positive scenes where at least half of the spoken name falls
inside an open gate. Synthetic scenes only: indicative, not a measurement of real environments.
"""
import argparse
import json
import os
import sys
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
import soundfile as sf

SAMPLE_RATE = 16000
WINDOW = 512
PRE_ROLL_S = 0.3
HANGOVER_S = 0.5


def make_detector(model_path: str, threshold: float):
    import sherpa_onnx

    config = sherpa_onnx.VadModelConfig()
    config.silero_vad.model = model_path
    config.silero_vad.threshold = threshold
    config.silero_vad.min_silence_duration = 0.1
    config.silero_vad.min_speech_duration = 0.1
    config.silero_vad.window_size = WINDOW
    config.sample_rate = SAMPLE_RATE
    return sherpa_onnx.VoiceActivityDetector(config, buffer_size_in_seconds=30)


def speech_flags(detector, samples: np.ndarray) -> np.ndarray:
    detector.reset()
    flags = []
    for start in range(0, len(samples) - WINDOW + 1, WINDOW):
        detector.accept_waveform(samples[start:start + WINDOW])
        flags.append(1 if detector.is_speech_detected() else 0)
        while not detector.empty():
            detector.pop()
    return np.array(flags, dtype=np.int8)


def gate_mask(flags: np.ndarray) -> np.ndarray:
    """Open the gate PRE_ROLL_S before each speech window and keep it open HANGOVER_S after."""
    step = WINDOW / SAMPLE_RATE
    pre, hang = int(round(PRE_ROLL_S / step)), int(round(HANGOVER_S / step))
    mask = np.zeros(len(flags), dtype=bool)
    for i in np.flatnonzero(flags):
        mask[max(0, i - pre): min(len(flags), i + hang + 1)] = True
    return mask


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA"))
    parser.add_argument("--vad-model", required=True)
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--out")
    args = parser.parse_args(argv[1:])

    scene_dir = Path(args.data) / "scenes"
    rows = [json.loads(line) for line in (scene_dir / "scenes.jsonl").read_text(encoding="utf-8").splitlines()]
    detector = make_detector(args.vad_model, args.threshold)
    step = WINDOW / SAMPLE_RATE
    duty = defaultdict(list)
    covered = defaultdict(list)
    started, audio_seconds = time.process_time(), 0.0
    for row in rows:
        samples, _ = sf.read(str(scene_dir / f"{row['id']}.wav"), dtype="float32")
        flags = speech_flags(detector, np.ascontiguousarray(samples))
        mask = gate_mask(flags)
        audio_seconds += len(samples) / SAMPLE_RATE
        group = f"{row['kind']}/{row['bg']}"
        duty[group].append(float(mask.mean()))
        if row["kind"] == "positive":
            lo, hi = int(row["name_start_s"] / step), max(int(row["name_end_s"] / step), int(row["name_start_s"] / step) + 1)
            inside = mask[lo:hi]
            covered[f"snr {row['snr_db']} dB"].append(bool(inside.size and inside.mean() >= 0.5))
    result = {
        "gate": {"pre_roll_s": PRE_ROLL_S, "hangover_s": HANGOVER_S, "threshold": args.threshold},
        "duty_cycle_by_scene_kind_and_background": {k: round(float(np.mean(v)), 3) for k, v in sorted(duty.items())},
        "name_covered_by_gate_by_snr": {k: round(float(np.mean(v)), 3) for k, v in sorted(covered.items())},
        "vad_host_cpu_realtime_factor": (time.process_time() - started) / audio_seconds,
    }
    print(json.dumps(result, indent=2))
    if args.out:
        Path(args.out).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
