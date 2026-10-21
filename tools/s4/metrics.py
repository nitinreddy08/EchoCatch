# SPDX-License-Identifier: GPL-3.0-only
"""Scoring for the S4 detector screening (definitions in docs/plan/01-requirements.md and 06-quality-and-testing.md).

A scene row has: id, kind (positive, near_miss, other_name, speech_only, background_only), name, name_start_s,
name_end_s, snr_db, bg, form, voice_group, duration_s and, for near_miss, confusable_with.
Detections for a scene are a list of (time_seconds, label).

Definitions
- Hit: a detection labelled with the target name inside [name_start - EARLY_S, name_end + LATE_S].
- T_detect: time of the first hit minus name_end (negative means detected before the name ended).
- False alarm: any detection in a negative scene, and for positive scenes any detection with another label.
- Near-miss accept: a detection of the confusable target name in a near_miss scene.
"""
from __future__ import annotations

from collections import defaultdict

EARLY_S = 0.3
LATE_S = 1.5
NEGATIVE_KINDS = {"speech_only", "background_only", "other_name", "near_miss"}


def _sort_key(value):
    """Numbers sort numerically (SNR 0, 5, 10, 20, 30), everything else alphabetically after them."""
    return (0, float(value), "") if isinstance(value, (int, float)) else (1, 0.0, str(value))


def percentile(values: list[float], q: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round(q * (len(ordered) - 1))))
    return ordered[index]


def first_hit(row: dict, detections: list[tuple[float, str]]):
    lo, hi = row["name_start_s"] - EARLY_S, row["name_end_s"] + LATE_S
    hits = [(t, label) for t, label in detections if lo <= t <= hi and label.lower() == row["name"].lower()]
    return min(hits) if hits else None


def score(rows: list[dict], detections: dict[str, list[tuple[float, str]]]) -> dict:
    positives = [r for r in rows if r["kind"] == "positive"]
    negatives = [r for r in rows if r["kind"] in NEGATIVE_KINDS]

    def recall_of(subset):
        if not subset:
            return None
        hits = sum(1 for r in subset if first_hit(r, detections.get(r["id"], [])) is not None)
        return {"n": len(subset), "hits": hits, "recall": hits / len(subset)}

    result: dict = {"positives": recall_of(positives)}
    for key in ("snr_db", "bg", "form", "voice_group", "name"):
        groups = defaultdict(list)
        for r in positives:
            groups[r[key]].append(r)
        result[f"recall_by_{key}"] = {str(k): recall_of(v) for k, v in sorted(groups.items(), key=lambda kv: _sort_key(kv[0]))}

    latencies = []
    wrong_label_in_positive = 0
    for r in positives:
        found = first_hit(r, detections.get(r["id"], []))
        if found:
            latencies.append(found[0] - r["name_end_s"])
        lo, hi = r["name_start_s"] - EARLY_S, r["name_end_s"] + LATE_S
        wrong_label_in_positive += sum(1 for t, label in detections.get(r["id"], []) if lo <= t <= hi and label.lower() != r["name"].lower())
    result["t_detect_s"] = {
        "n": len(latencies), "p50": percentile(latencies, 0.5), "p95": percentile(latencies, 0.95)
    }

    negative_hours = sum(r["duration_s"] for r in negatives) / 3600.0
    false_alarms = defaultdict(int)
    near_miss_accepts = 0
    near_miss_scenes = 0
    for r in negatives:
        found = detections.get(r["id"], [])
        false_alarms[r["kind"]] += len(found)
        if r["kind"] == "near_miss":
            near_miss_scenes += 1
            if any(label.lower() == r["confusable_with"].lower() for _, label in found):
                near_miss_accepts += 1
    total_false_alarms = sum(false_alarms.values())
    result["negatives"] = {
        "scenes": len(negatives),
        "hours": negative_hours,
        "false_alarms": total_false_alarms,
        "false_alarms_per_hour_all_names": total_false_alarms / negative_hours if negative_hours else None,
        "by_kind": dict(false_alarms),
        "near_miss_scenes": near_miss_scenes,
        "near_miss_accepts": near_miss_accepts,
    }
    result["wrong_label_detections_in_positive_window"] = wrong_label_in_positive
    return result
