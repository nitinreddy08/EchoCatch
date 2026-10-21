#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Score name matching rules over saved transcripts (see transcribe.py).

Usage: match_eval.py --data DIR --transcripts FILE [--taus 1.0,0.85,0.7] [--out FILE.json]
Runs exact-text matching and phonetic matching, each with and without the respelled aliases.
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import metrics  # noqa: E402
import phonetic  # noqa: E402
from screen import summary_line  # noqa: E402


def exact_detections(words, names, with_aliases):
    targets = {}
    for entry in names:
        for spelling in [entry["name"]] + (entry.get("aliases", []) if with_aliases else []):
            targets[spelling.upper()] = entry["name"]
    text_words = [(t, w) for t, w in words]
    hits = []
    for index in range(len(text_words)):
        for count in (1, 2, 3):
            start = index - count + 1
            if start < 0:
                break
            phrase = " ".join(w for _, w in text_words[start:index + 1])
            if phrase in targets:
                hits.append((text_words[index][0], targets[phrase]))
    return sorted(set(hits))


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA"))
    parser.add_argument("--transcripts", required=True)
    parser.add_argument("--taus", default="1.0,0.85,0.7")
    parser.add_argument("--out")
    args = parser.parse_args(argv[1:])
    rows = [json.loads(line) for line in (Path(args.data) / "scenes" / "scenes.jsonl").read_text(encoding="utf-8").splitlines()]
    names = json.loads((HERE.parent / "corpus" / "names.json").read_text(encoding="utf-8"))["targets"]
    saved = json.loads(Path(args.transcripts).read_text(encoding="utf-8"))
    transcripts = saved["transcripts"]
    results = []
    variants = [("exact", None, False), ("exact", None, True)]
    variants += [("phonetic", float(tau), alias) for tau in args.taus.split(",") for alias in (False, True)]
    for kind, tau, alias in variants:
        detections = {}
        matcher = phonetic.PhoneticMatcher(names, alias, tau) if kind == "phonetic" else None
        for row in rows:
            words = [(t, w) for t, w in transcripts[row["id"]]]
            detections[row["id"]] = matcher.detect(words) if matcher else exact_detections(words, names, alias)
        result = metrics.score(rows, detections)
        result["config"] = {"engine": f"asr+{kind}", "boost": 0.0, "threshold": tau if tau is not None else 0.0, "aliases": alias}
        result["host_cpu_realtime_factor"] = saved["host_cpu_realtime_factor"]
        results.append(result)
        print(summary_line(result), flush=True)
    if args.out:
        Path(args.out).write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
