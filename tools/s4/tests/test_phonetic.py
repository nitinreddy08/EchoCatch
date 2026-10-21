# SPDX-License-Identifier: GPL-3.0-only
import sys
import unittest
from pathlib import Path

try:
    import jellyfish  # noqa: F401
except ImportError:  # run where the benchmark requirements are installed
    raise unittest.SkipTest("jellyfish is not installed")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import phonetic  # noqa: E402

NAMES = [
    {"name": "Sofia", "aliases": []},
    {"name": "Siobhan", "aliases": ["SHIVAWN"]},
    {"name": "Xiomara", "aliases": ["SEE OH MARA"]},
]


class PhoneticTest(unittest.TestCase):
    def test_levenshtein_and_similarity(self):
        self.assertEqual(phonetic.levenshtein("KITTEN", "SITTING"), 3)
        self.assertEqual(phonetic.similarity("SF", "SF"), 1.0)
        self.assertAlmostEqual(phonetic.similarity("PRY", "PR"), 2 / 3)
        self.assertEqual(phonetic.similarity("", ""), 1.0)

    def test_respelled_word_matches_by_sound(self):
        matcher = phonetic.PhoneticMatcher(NAMES, with_aliases=False, tau=1.0)
        self.assertEqual(matcher.detect([(1.0, "HELLO"), (1.4, "SOPHIA")]), [(1.4, "Sofia")])

    def test_unrelated_words_do_not_match(self):
        matcher = phonetic.PhoneticMatcher(NAMES, with_aliases=True, tau=0.85)
        self.assertEqual(matcher.detect([(0.5, "THE"), (0.9, "WEATHER"), (1.3, "TODAY")]), [])

    def test_alias_helps_only_when_enabled(self):
        words = [(2.0, "CHIVONNE")]
        self.assertEqual(phonetic.PhoneticMatcher(NAMES, with_aliases=True, tau=1.0).detect(words), [(2.0, "Siobhan")])
        self.assertEqual(phonetic.PhoneticMatcher(NAMES, with_aliases=False, tau=1.0).detect(words), [])

    def test_split_words_are_joined(self):
        matcher = phonetic.PhoneticMatcher(NAMES, with_aliases=True, tau=1.0)
        detections = matcher.detect([(1.0, "SEE"), (1.2, "OH"), (1.5, "MARA")])
        self.assertIn((1.5, "Xiomara"), detections)

    def test_repeat_within_a_second_is_reported_once(self):
        matcher = phonetic.PhoneticMatcher(NAMES, with_aliases=False, tau=1.0)
        detections = matcher.detect([(1.0, "SOPHIA"), (1.5, "SOFIA"), (3.0, "SOPHIA")])
        self.assertEqual([t for t, _ in detections], [1.0, 3.0])


if __name__ == "__main__":
    unittest.main()
