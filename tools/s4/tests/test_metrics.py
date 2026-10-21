# SPDX-License-Identifier: GPL-3.0-only
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import metrics  # noqa: E402


def positive(i, name="Priya", start=2.0, end=2.6, snr=10, bg="babble", form="isolated", group="us-english"):
    return {"id": i, "kind": "positive", "name": name, "name_start_s": start, "name_end_s": end, "snr_db": snr,
            "bg": bg, "form": form, "voice_group": group, "duration_s": 8.0}


def negative(i, kind="speech_only", name=None, confusable=None):
    row = {"id": i, "kind": kind, "name": name, "name_start_s": None, "name_end_s": None, "duration_s": 8.0}
    if confusable:
        row["confusable_with"] = confusable
    return row


class MetricsTest(unittest.TestCase):
    def test_hit_inside_the_window_counts_and_gives_latency(self):
        rows = [positive("a")]
        result = metrics.score(rows, {"a": [(2.9, "PRIYA")]})
        self.assertEqual(result["positives"]["hits"], 1)
        self.assertAlmostEqual(result["t_detect_s"]["p50"], 0.3, places=6)

    def test_late_or_wrong_label_is_not_a_hit(self):
        rows = [positive("a"), positive("b")]
        result = metrics.score(rows, {"a": [(5.0, "Priya")], "b": [(2.8, "Maya")]})
        self.assertEqual(result["positives"]["hits"], 0)
        self.assertEqual(result["wrong_label_detections_in_positive_window"], 1)

    def test_recall_is_grouped_by_snr(self):
        rows = [positive("a", snr=20), positive("b", snr=20), positive("c", snr=0), positive("d", snr=0)]
        detections = {"a": [(2.7, "Priya")], "b": [(2.7, "Priya")], "c": [(2.7, "Priya")]}
        result = metrics.score(rows, detections)
        self.assertEqual(result["recall_by_snr_db"]["20"]["recall"], 1.0)
        self.assertEqual(result["recall_by_snr_db"]["0"]["recall"], 0.5)

    def test_false_alarms_per_hour_uses_negative_scenes_only(self):
        rows = [negative(f"n{i}") for i in range(450)] + [positive("p")]  # 450 x 8 s = 1 hour
        detections = {"n1": [(1.0, "Alex")], "n2": [(3.0, "Jack"), (6.0, "Jack")], "p": [(7.5, "Alex")]}
        result = metrics.score(rows, detections)
        self.assertAlmostEqual(result["negatives"]["hours"], 1.0)
        self.assertEqual(result["negatives"]["false_alarms"], 3)
        self.assertAlmostEqual(result["negatives"]["false_alarms_per_hour_all_names"], 3.0)

    def test_near_miss_accept_counts_only_the_confusable_name(self):
        rows = [negative("a", "near_miss", "Mia", "Maya"), negative("b", "near_miss", "Zack", "Jack"), negative("c", "near_miss", "Pria", "Priya")]
        detections = {"a": [(3.0, "Maya")], "b": [(3.0, "Alex")]}
        result = metrics.score(rows, detections)
        self.assertEqual(result["negatives"]["near_miss_scenes"], 3)
        self.assertEqual(result["negatives"]["near_miss_accepts"], 1)

    def test_empty_inputs_do_not_crash(self):
        result = metrics.score([], {})
        self.assertIsNone(result["positives"])
        self.assertIsNone(result["negatives"]["false_alarms_per_hour_all_names"])

    def test_percentile(self):
        self.assertIsNone(metrics.percentile([], 0.5))
        self.assertEqual(metrics.percentile([1, 2, 3, 4, 5], 0.5), 3)
        self.assertEqual(metrics.percentile([1, 2, 3, 4, 5], 0.95), 5)


if __name__ == "__main__":
    unittest.main()
