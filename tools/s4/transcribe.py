#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Transcribe every benchmark scene once and save the words with their times, so matching rules can be tried offline.

Usage: transcribe.py --data DIR --model DIR [--encoder-tag TAG] --out transcripts.json [--workers N]
"""
import argparse
import json
import os
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
import soundfile as sf

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import engines  # noqa: E402

_engine = None
_scene_dir = None


def _init(model, encoder_tag, scene_dir):
    global _engine, _scene_dir
    _engine = engines.SherpaAsrTranscriber(model, encoder_tag)
    _scene_dir = Path(scene_dir)


def _run(scene_id):
    samples, _ = sf.read(str(_scene_dir / f"{scene_id}.wav"), dtype="float32")
    before_cpu, before_audio = _engine.cpu_seconds, _engine.audio_seconds
    words = _engine.transcribe(np.ascontiguousarray(samples))
    return scene_id, words, _engine.cpu_seconds - before_cpu, _engine.audio_seconds - before_audio


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA"))
    parser.add_argument("--model", required=True)
    parser.add_argument("--encoder-tag", default="epoch-99-avg-1-chunk-16-left-128")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv[1:])
    scene_dir = Path(args.data) / "scenes"
    ids = [json.loads(line)["id"] for line in (scene_dir / "scenes.jsonl").read_text(encoding="utf-8").splitlines()]
    transcripts, cpu, audio_seconds = {}, 0.0, 0.0
    with ProcessPoolExecutor(max_workers=args.workers, initializer=_init, initargs=(args.model, args.encoder_tag, str(scene_dir))) as pool:
        for scene_id, words, cpu_s, audio_s in pool.map(_run, ids, chunksize=8):
            transcripts[scene_id] = words
            cpu += cpu_s
            audio_seconds += audio_s
    Path(args.out).write_text(json.dumps({"host_cpu_realtime_factor": cpu / audio_seconds, "transcripts": transcripts}) + "\n", encoding="utf-8")
    print(f"transcribed {len(ids)} scenes, host cpu realtime factor {cpu / audio_seconds:.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
