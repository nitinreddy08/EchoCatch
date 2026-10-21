# SPDX-License-Identifier: GPL-3.0-only
import sys
import unittest
from pathlib import Path

try:
    import numpy as np
except ImportError:  # the CI script job has no numpy; these tests run where numpy is installed
    raise unittest.SkipTest("numpy is not installed")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audio  # noqa: E402


def tone(freq, seconds, rate):
    t = np.arange(int(seconds * rate)) / rate
    return np.sin(2 * np.pi * freq * t).astype(np.float32)


def peak_frequency(x, rate):
    spectrum = np.abs(np.fft.rfft(x * np.hanning(len(x))))
    return np.fft.rfftfreq(len(x), 1.0 / rate)[int(np.argmax(spectrum))]


class AudioTest(unittest.TestCase):
    def test_resample_keeps_frequency_and_length(self):
        for source in (24000, 48000, 44100, 8000):
            out = audio.resample(tone(1000, 1.0, source), source, 16000)
            self.assertAlmostEqual(len(out) / 16000, 1.0, delta=0.01, msg=f"length from {source}")
            self.assertAlmostEqual(peak_frequency(out, 16000), 1000, delta=10, msg=f"peak from {source}")

    def test_resample_keeps_level(self):
        out = audio.resample(tone(1000, 1.0, 24000), 24000, 16000)
        self.assertAlmostEqual(audio.rms(out[2000:-2000]), 1 / np.sqrt(2), delta=0.02)

    def test_trim_silence_finds_the_burst(self):
        x = np.zeros(16000 * 2, dtype=np.float32)
        x[16000:16000 + 8000] = tone(500, 0.5, 16000) * 0.5
        clip, start, end = audio.trim_silence(x)
        self.assertAlmostEqual(start / 16000, 1.0, delta=0.05)
        self.assertAlmostEqual(end / 16000, 1.5, delta=0.05)
        self.assertEqual(len(clip), end - start)

    def test_scale_background_hits_the_requested_snr(self):
        rng = np.random.default_rng(1)
        noise = audio.pink_noise(32000, rng)
        for snr in (20, 10, 5, 0, -5):
            scaled = audio.scale_background_for_snr(0.1, noise, snr)
            self.assertAlmostEqual(20 * np.log10(0.1 / audio.rms(scaled)), snr, delta=0.01)

    def test_pink_noise_falls_about_three_db_per_octave(self):
        rng = np.random.default_rng(2)
        x = audio.pink_noise(2 ** 18, rng)
        spectrum = np.abs(np.fft.rfft(x)) ** 2
        freqs = np.fft.rfftfreq(len(x), 1 / 16000)

        def band(lo, hi):
            return spectrum[(freqs >= lo) & (freqs < hi)].mean()

        drop_db = 10 * np.log10(band(500, 1000) / band(2000, 4000))
        self.assertAlmostEqual(drop_db, 6.0, delta=1.0)  # two octaves, 3 dB each

    def test_normalize_level_sets_rms_and_limits_peaks(self):
        x = tone(300, 1.0, 16000) * 0.01
        out = audio.normalize_level(x, -25.0)
        self.assertAlmostEqual(audio.dbfs(audio.rms(out)), -25.0, delta=0.1)
        spiky = np.zeros(16000, dtype=np.float32)
        spiky[100] = 1.0
        self.assertLessEqual(float(np.max(np.abs(audio.normalize_level(spiky, -10.0)))), 0.95 + 1e-6)

    def test_babble_and_noise_are_unit_rms_and_deterministic(self):
        rng_a, rng_b = np.random.default_rng(3), np.random.default_rng(3)
        clips = [tone(f, 3.0, 16000) for f in (200, 300, 440)]
        a = audio.babble(clips, 16000, rng_a)
        b = audio.babble(clips, 16000, rng_b)
        self.assertTrue(np.array_equal(a, b))
        self.assertAlmostEqual(audio.rms(a), 1.0, delta=0.01)
        rng = np.random.default_rng(4)
        for maker in (audio.street_noise, audio.quiet_room):
            self.assertAlmostEqual(audio.rms(maker(16000, 16000, rng)), 1.0, delta=0.01)


if __name__ == "__main__":
    unittest.main()
