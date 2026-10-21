#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Synthesize the audio bank for the S4 benchmark with the Kokoro voices (test audio only, never shipped).

Usage: synth.py [--data DIR] [--speech-per-voice N] [--seed N] [--threads N]

Writes, under <data>/bank/ (16 kHz mono 16-bit WAV, restartable):
  names/<voice>/<name>.wav        every target, near-miss and other name, per voice
  phrases/<voice>/lead_<i>.wav    lead-in words, and follow_<i>.wav continuations (none contain a name)
  speech/<voice>/<k>.wav          sentences from public-domain books, for talkers, babble and negative scenes
  speech/index.jsonl              text of every speech file
  manifest.json                   what was generated and with which settings
"""
import argparse
import json
import os
import random
import re
import sys
import zipfile
from pathlib import Path

import numpy as np
import soundfile as sf

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import audio  # noqa: E402

BOOKS = ["carroll-alice.txt", "austen-emma.txt", "chesterton-brown.txt", "burgess-busterbrown.txt", "bible-kjv.txt"]


def load_names():
    return json.loads((HERE / "names.json").read_text(encoding="utf-8"))


def all_name_words(config) -> set[str]:
    words = set()
    for entry in config["targets"] + config["near_miss"]:
        words.update(w.lower() for w in re.findall(r"[A-Za-z]+", entry["name"] + " " + entry["say"]))
    words.update(n.lower() for n in config["other_names"])
    return words


def sentences_from_books(data_dir: Path, forbidden: set[str], rng: random.Random, count: int) -> list[str]:
    archive = data_dir / "downloads" / "gutenberg.zip"
    pool: list[str] = []
    with zipfile.ZipFile(archive) as books:
        names = {Path(n).name: n for n in books.namelist()}
        for book in BOOKS:
            if book not in names:
                continue
            text = books.read(names[book]).decode("latin-1")
            text = re.sub(r"\s+", " ", text)
            for sentence in re.split(r"(?<=[.!?])\s+", text):
                words = re.findall(r"[A-Za-z']+", sentence)
                if not 6 <= len(words) <= 16:
                    continue
                if re.search(r"[0-9_\[\]()\":;]", sentence) or sentence.isupper():
                    continue
                if any(w.lower() in forbidden for w in words):
                    continue
                pool.append(sentence.strip())
    rng.shuffle(pool)
    return pool[:count]


def make_tts(model_dir: Path, threads: int):
    import sherpa_onnx

    lexicon = ",".join([str(model_dir / "lexicon-us-en.txt"), str(model_dir / "lexicon-gb-en.txt")])
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=str(model_dir / "model.onnx"),
                voices=str(model_dir / "voices.bin"),
                tokens=str(model_dir / "tokens.txt"),
                data_dir=str(model_dir / "espeak-ng-data"),
                dict_dir=str(model_dir / "dict"),
                lexicon=lexicon,
                lang="en-us",
            ),
            num_threads=threads,
            provider="cpu",
        )
    )
    return sherpa_onnx.OfflineTts(config)


def speak(tts, text: str, sid: int, speed: float) -> np.ndarray:
    generated = tts.generate(text, sid=sid, speed=speed)
    samples = np.asarray(generated.samples, dtype=np.float32)
    return audio.resample(samples, generated.sample_rate, audio.SAMPLE_RATE)


def save(path: Path, samples: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(path), np.clip(samples, -1.0, 1.0), audio.SAMPLE_RATE, subtype="PCM_16")


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA", str(HERE / "data")))
    parser.add_argument("--speech-per-voice", type=int, default=45)
    parser.add_argument("--seed", type=int, default=20261021)
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args(argv[1:])

    data_dir = Path(args.data)
    bank = data_dir / "bank"
    config = load_names()
    rng = random.Random(args.seed)
    tts = make_tts(data_dir / "kokoro-multi-lang-v1_0", args.threads)
    forbidden = all_name_words(config)

    name_entries = (
        [(e["name"], e["say"]) for e in config["targets"]]
        + [(e["name"], e["say"]) for e in config["near_miss"]]
        + [(n, n) for n in config["other_names"]]
    )
    texts = sentences_from_books(data_dir, forbidden, rng, args.speech_per_voice * len(config["voices"]))
    if len(texts) < args.speech_per_voice * len(config["voices"]):
        print(f"warning: only {len(texts)} usable sentences found")

    index = []
    generated = {"names": 0, "phrases": 0, "speech": 0}
    for voice_number, voice in enumerate(config["voices"]):
        sid, voice_id = voice["sid"], voice["id"]
        for name, say in name_entries:
            target = bank / "names" / voice_id / f"{name}.wav"
            if not target.exists():
                save(target, speak(tts, say, sid, 1.0))
                generated["names"] += 1
        for i, phrase in enumerate(config["lead_ins"]):
            target = bank / "phrases" / voice_id / f"lead_{i}.wav"
            if not target.exists():
                save(target, speak(tts, phrase, sid, 1.0))
                generated["phrases"] += 1
        for i, phrase in enumerate(config["follow_ons"]):
            target = bank / "phrases" / voice_id / f"follow_{i}.wav"
            if not target.exists():
                save(target, speak(tts, phrase, sid, 1.0))
                generated["phrases"] += 1
        for k in range(args.speech_per_voice):
            text = texts[(voice_number * args.speech_per_voice + k) % len(texts)]
            speed = round(rng.uniform(0.9, 1.15), 2)
            target = bank / "speech" / voice_id / f"{k:03d}.wav"
            if not target.exists():
                save(target, speak(tts, text, sid, speed))
                generated["speech"] += 1
            index.append({"file": f"speech/{voice_id}/{k:03d}.wav", "voice": voice_id, "text": text, "speed": speed})
        print(f"voice {voice_id} done", flush=True)

    (bank / "speech").mkdir(parents=True, exist_ok=True)
    with (bank / "speech" / "index.jsonl").open("w", encoding="utf-8") as handle:
        for row in index:
            handle.write(json.dumps(row) + "\n")
    (bank / "manifest.json").write_text(
        json.dumps(
            {
                "tts": "kokoro-multi-lang-v1_0 (sherpa-onnx), language en-us",
                "seed": args.seed,
                "voices": [v["id"] for v in config["voices"]],
                "generated_this_run": generated,
                "speech_per_voice": args.speech_per_voice,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print("bank ready:", generated)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
