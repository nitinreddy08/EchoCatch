# SPDX-License-Identifier: GPL-3.0-only
"""Match recogniser output to names by sound instead of spelling.

A recogniser writes rare names the way they sound ("SOPHIA" for Sofia, "CHIVONNE" for Siobhan), so exact-text matching
misses them. Here every word (and runs of up to three words joined together, for names the recogniser splits such as
"SEE OMARA") is reduced to a Metaphone key and compared with the name's key by edit distance.
"""
from __future__ import annotations

MAX_JOINED_WORDS = 3
DUPLICATE_WINDOW_S = 1.0


def metaphone_key(text: str) -> str:
    import jellyfish

    return jellyfish.metaphone("".join(ch for ch in text.upper() if ch.isalpha()))


def levenshtein(a: str, b: str) -> int:
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def similarity(key_a: str, key_b: str) -> float:
    longest = max(len(key_a), len(key_b))
    return 1.0 if longest == 0 else 1.0 - levenshtein(key_a, key_b) / longest


class PhoneticMatcher:
    def __init__(self, names: list[dict], with_aliases: bool, tau: float, min_key_length: int = 2):
        self.tau = tau
        self.targets: list[tuple[str, str]] = []
        for entry in names:
            spellings = [entry["name"]] + (entry.get("aliases", []) if with_aliases else [])
            for spelling in spellings:
                key = metaphone_key(spelling)
                if len(key) >= min_key_length:
                    self.targets.append((entry["name"], key))
        self.min_key_length = min_key_length

    def detect(self, words: list[tuple[float, str]]) -> list[tuple[float, str]]:
        """words: (time_seconds, word) in order. Returns (time, label) detections, one per label per second."""
        detections: list[tuple[float, str]] = []
        last_time: dict[str, float] = {}
        for end in range(len(words)):
            for count in range(1, MAX_JOINED_WORDS + 1):
                start = end - count + 1
                if start < 0:
                    break
                key = metaphone_key("".join(word for _, word in words[start:end + 1]))
                if len(key) < self.min_key_length:
                    continue
                time = words[end][0]
                for label, target_key in self.targets:
                    if similarity(key, target_key) >= self.tau and time - last_time.get(label, -1e9) > DUPLICATE_WINDOW_S:
                        detections.append((time, label))
                        last_time[label] = time
        return sorted(detections)
