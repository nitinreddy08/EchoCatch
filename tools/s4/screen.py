#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Run a keyword engine over the benchmark scenes and report recall, false alarms and latency.

Usage:
  screen.py --data DIR --model DIR [--aliases] [--boost 1.0] [--threshold 0.25] [--limit N] [--out FILE.json]
  screen.py --data DIR --model DIR --sweep [--aliases]          # grid of boost and threshold values

Scenes come from tools/corpus/build_scenes.py. Results on synthetic scenes are screening evidence only.
"""
import argparse
import json
import os
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
import soundfile as sf

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import engines  # noqa: E402
import metrics  # noqa: E402

_engine = None
_scene_dir = None


def _init(model, names, with_aliases, boost, threshold, scene_dir):
    global _engine, _scene_dir
    _scene_dir = Path(scene_dir)
    _engine = engines.SherpaKws(model, names, with_aliases, boost, threshold, work_dir=tempfile.gettempdir())


def _run(scene_id):
    samples, rate = sf.read(str(_scene_dir / f"{scene_id}.wav"), dtype="float32")
    assert rate == engines.SAMPLE_RATE
    before_cpu, before_audio = _engine.cpu_seconds, _engine.audio_seconds
    hits = _engine.detect(np.ascontiguousarray(samples))
    return scene_id, hits, _engine.cpu_seconds - before_cpu, _engine.audio_seconds - before_audio


def evaluate(model, names, rows, scene_dir, with_aliases, boost, threshold, workers):
    ids = [r["id"] for r in rows]
    detections, cpu, audio_seconds = {}, 0.0, 0.0
    with ProcessPoolExecutor(max_workers=workers, initializer=_init,
                             initargs=(model, names, with_aliases, boost, threshold, str(scene_dir))) as pool:
        for scene_id, hits, cpu_s, audio_s in pool.map(_run, ids, chunksize=8):
            detections[scene_id] = hits
            cpu += cpu_s
            audio_seconds += audio_s
    result = metrics.score(rows, detections)
    result["config"] = {"boost": boost, "threshold": threshold, "aliases": with_aliases}
    result["host_cpu_realtime_factor"] = cpu / audio_seconds if audio_seconds else None
    return result


def _fmt(value, spec):
    return "n/a" if value is None else format(value, spec)


def summary_line(result):
    p = result["positives"]
    by_snr = result["recall_by_snr_db"]
    snr = " ".join(f"{k}dB:{v['recall']:.2f}" for k, v in by_snr.items())
    neg = result["negatives"]
    return (f"boost={result['config']['boost']:<4} thr={result['config']['threshold']:<5} alias={int(result['config']['aliases'])} "
            f"recall={_fmt(p and p['recall'], '.3f')} [{snr}] FA/h={_fmt(neg['false_alarms_per_hour_all_names'], '.2f')} "
            f"nearmiss={neg['near_miss_accepts']}/{neg['near_miss_scenes']} "
            f"t50={_fmt(result['t_detect_s']['p50'], '.2f')} "
            f"rtf={_fmt(result['host_cpu_realtime_factor'], '.3f')}")


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA"))
    parser.add_argument("--model", required=True)
    parser.add_argument("--aliases", action="store_true")
    parser.add_argument("--boost", type=float, default=1.0)
    parser.add_argument("--threshold", type=float, default=0.25)
    parser.add_argument("--sweep", action="store_true")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--out")
    args = parser.parse_args(argv[1:])

    data = Path(args.data)
    scene_dir = data / "scenes"
    rows = [json.loads(line) for line in (scene_dir / "scenes.jsonl").read_text(encoding="utf-8").splitlines()]
    if args.limit:
        rows = rows[: args.limit]
    names = json.loads((HERE.parent / "corpus" / "names.json").read_text(encoding="utf-8"))["targets"]

    grid = [(args.boost, args.threshold)]
    if args.sweep:
        grid = [(b, t) for b in (1.0, 2.0, 3.0) for t in (0.1, 0.25, 0.4)]
    results = []
    for boost, threshold in grid:
        result = evaluate(args.model, names, rows, scene_dir, args.aliases, boost, threshold, args.workers)
        results.append(result)
        print(summary_line(result), flush=True)
    if args.out:
        Path(args.out).write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
