#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Build benchmark scenes (8 s clips with ground-truth labels) from the synthesized bank.

Usage: build_scenes.py [--data DIR] [--reps N] [--negatives N] [--seed N]

Positive scenes: a target name (alone, followed by a phrase, or after a lead-in) mixed into a background at a stated
SNR, where SNR is name loudness over background loudness. Negative scenes: speech without any target name, near-miss
and other names, and background only. Output: <data>/scenes/<id>.wav and <data>/scenes/scenes.jsonl.
These are synthetic stand-ins for real recordings (docs/plan/06-quality-and-testing.md); results from them are
screening evidence, not acceptance evidence.
"""
import argparse
import json
import os
import random
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import audio  # noqa: E402

SR = audio.SAMPLE_RATE
SCENE_SECONDS = 8.0
GAP_SECONDS = 0.12
BACKGROUNDS = ["babble", "street", "tv"]
SNRS = [20, 10, 5, 0]
QUIET_SNR = 30
FORMS = ["isolated", "name_first", "name_last"]


class Bank:
    def __init__(self, bank_dir: Path, config: dict):
        self.dir = bank_dir
        self.config = config
        self.voices = [v["id"] for v in config["voices"]]
        self.groups = {v["id"]: v["group"] for v in config["voices"]}
        self._cache: dict[str, np.ndarray] = {}
        self.speech: dict[str, list[str]] = {
            voice: sorted(str(p.relative_to(bank_dir)) for p in (bank_dir / "speech" / voice).glob("*.wav"))
            for voice in self.voices
        }

    def load(self, relative: str) -> np.ndarray:
        if relative not in self._cache:
            samples, rate = sf.read(str(self.dir / relative), dtype="float32")
            assert rate == SR, f"{relative} is {rate} Hz"
            self._cache[relative] = samples
        return self._cache[relative]

    def name_clip(self, voice: str, name: str) -> np.ndarray:
        clip, _, _ = audio.trim_silence(self.load(f"names/{voice}/{name}.wav"))
        return clip


def concat(parts):
    return np.concatenate(parts).astype(np.float32)


def make_foreground(bank: Bank, rng: random.Random, voice: str, name: str, form: str):
    clip = bank.name_clip(voice, name)
    gap = np.zeros(int(GAP_SECONDS * SR), dtype=np.float32)
    config = bank.config
    if form == "isolated":
        parts, start = [clip], 0
    elif form == "name_first":
        follow, _, _ = audio.trim_silence(bank.load(f"phrases/{voice}/follow_{rng.randrange(len(config['follow_ons']))}.wav"))
        parts, start = [clip, gap, follow], 0
    else:
        lead, _, _ = audio.trim_silence(bank.load(f"phrases/{voice}/lead_{rng.randrange(len(config['lead_ins']))}.wav"))
        parts, start = [lead, gap, clip], len(lead) + len(gap)
    return concat(parts), start / SR, (start + len(clip)) / SR, audio.rms(clip)


def make_background(bank: Bank, kind: str, n: int, rng: random.Random, np_rng: np.random.Generator, avoid_voice: str):
    others = [v for v in bank.voices if v != avoid_voice]
    if kind == "babble":
        pool = [bank.load(f) for v in others for f in bank.speech[v]]
        return audio.babble(pool, n, np_rng, talkers=6)
    if kind == "tv":
        voice = rng.choice(others)
        files = list(bank.speech[voice])
        rng.shuffle(files)
        pieces, length = [], 0
        for f in files:
            pieces.append(bank.load(f))
            pieces.append(np.zeros(int(0.25 * SR), dtype=np.float32))
            length += len(pieces[-2]) + len(pieces[-1])
            if length >= n:
                break
        x = concat(pieces)[:n]
        return (x / (audio.rms(x) + 1e-9)).astype(np.float32)
    if kind == "street":
        return audio.street_noise(n, SR, np_rng)
    return audio.quiet_room(n, SR, np_rng)


def assemble(foreground, reference_rms, background, snr_db, rng, level_dbfs=-25.0):
    total = int(SCENE_SECONDS * SR)
    latest = max(1.0, SCENE_SECONDS - len(foreground) / SR - 0.4)
    onset = int(rng.uniform(0.8, latest) * SR)
    scene = audio.scale_background_for_snr(reference_rms, background, snr_db).astype(np.float32).copy()
    end = min(total, onset + len(foreground))
    scene[onset:end] += foreground[: end - onset]
    return audio.normalize_level(scene, level_dbfs), onset / SR


def plan(reps: int, negatives: int, targets, near_miss, other_names):
    jobs = []
    for target in targets:
        for rep in range(reps):
            for bg in BACKGROUNDS:
                for snr in SNRS:
                    jobs.append({"kind": "positive", "name": target["name"], "bg": bg, "snr_db": snr})
            jobs.append({"kind": "positive", "name": target["name"], "bg": "quiet", "snr_db": QUIET_SNR})
    rng = random.Random(7)
    kinds = ["speech_only"] * 4 + ["near_miss"] * 2 + ["other_name"] * 2 + ["background_only"] * 2
    for i in range(negatives):
        kind = kinds[i % len(kinds)]
        job = {"kind": kind, "bg": rng.choice(BACKGROUNDS + ["quiet"]), "snr_db": rng.choice(SNRS)}
        if kind == "near_miss":
            entry = near_miss[i // len(kinds) % len(near_miss)]
            job.update(name=entry["name"], confusable_with=entry["confusable_with"])
        elif kind == "other_name":
            job.update(name=other_names[i // len(kinds) % len(other_names)])
        jobs.append(job)
    return jobs


def build(data_dir: Path, reps: int, negatives: int, seed: int, out_dir: Path):
    config = json.loads((HERE / "names.json").read_text(encoding="utf-8"))
    bank = Bank(data_dir / "bank", config)
    say = {e["name"]: e["say"] for e in config["targets"] + config["near_miss"]}
    jobs = plan(reps, negatives, config["targets"], config["near_miss"], config["other_names"])
    out_dir.mkdir(parents=True, exist_ok=True)
    total = int(SCENE_SECONDS * SR)
    rows = []
    for index, job in enumerate(jobs):
        rng = random.Random(seed * 100003 + index)
        np_rng = np.random.default_rng([seed, index])
        voice = rng.choice(bank.voices)
        row = dict(job, id=f"s{index:05d}", voice=voice, voice_group=bank.groups[voice], duration_s=SCENE_SECONDS)
        if job["kind"] in ("positive", "near_miss", "other_name"):
            form = rng.choice(FORMS)
            fg, start, end, name_rms = make_foreground(bank, rng, voice, job["name"], form)
            bg = make_background(bank, job["bg"], total, rng, np_rng, voice)
            snr = job["snr_db"]
            scene, onset = assemble(fg, name_rms, bg, snr, rng)
            row.update(form=form, say=say.get(job["name"], job["name"]), name_start_s=round(onset + start, 3), name_end_s=round(onset + end, 3))
        elif job["kind"] == "speech_only":
            files = list(bank.speech[voice])
            rng.shuffle(files)
            pieces = [bank.load(files[0])]
            if len(pieces[0]) < 4.5 * SR:
                pieces += [np.zeros(int(0.3 * SR), dtype=np.float32), bank.load(files[1])]
            fg = concat(pieces)[: int(7.0 * SR)]
            bg = make_background(bank, job["bg"], total, rng, np_rng, voice)
            scene, _ = assemble(fg, audio.rms(fg), bg, job["snr_db"], rng)
            row.update(form="speech", name_start_s=None, name_end_s=None)
        else:  # background_only
            bg = make_background(bank, job["bg"], total, rng, np_rng, voice)
            scene = audio.normalize_level(bg, rng.uniform(-45.0, -25.0))
            row.update(form="background", name_start_s=None, name_end_s=None)
        sf.write(str(out_dir / f"{row['id']}.wav"), np.clip(scene, -1, 1), SR, subtype="PCM_16")
        rows.append(row)
    with (out_dir / "scenes.jsonl").open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row) + "\n")
    return rows


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA", str(HERE / "data")))
    parser.add_argument("--reps", type=int, default=3)
    parser.add_argument("--negatives", type=int, default=1500)
    parser.add_argument("--seed", type=int, default=20261021)
    args = parser.parse_args(argv[1:])
    data_dir = Path(args.data)
    rows = build(data_dir, args.reps, args.negatives, args.seed, data_dir / "scenes")
    kinds = {}
    for row in rows:
        kinds[row["kind"]] = kinds.get(row["kind"], 0) + 1
    print(f"built {len(rows)} scenes ({len(rows) * SCENE_SECONDS / 3600:.2f} h): {kinds}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
