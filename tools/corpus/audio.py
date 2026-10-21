# SPDX-License-Identifier: GPL-3.0-only
"""Small, dependency-light audio helpers for building benchmark scenes (numpy only, soundfile for file I/O)."""
from __future__ import annotations

import math

import numpy as np

SAMPLE_RATE = 16000


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(x, dtype=np.float64)))) if len(x) else 0.0


def dbfs(x: float) -> float:
    return -120.0 if x <= 1e-9 else 20.0 * math.log10(x)


def resample(x: np.ndarray, source_rate: int, target_rate: int = SAMPLE_RATE) -> np.ndarray:
    """Polyphase-style rational resampling with a windowed-sinc low-pass filter."""
    if source_rate == target_rate:
        return x.astype(np.float32, copy=False)
    g = math.gcd(source_rate, target_rate)
    up, down = target_rate // g, source_rate // g
    taps_per_side = 16 * max(up, down)
    cutoff = 0.5 / max(up, down)  # relative to the upsampled rate
    n = np.arange(-taps_per_side, taps_per_side + 1)
    h = np.sinc(2 * cutoff * n) * 2 * cutoff * np.blackman(len(n)) * up
    upsampled = np.zeros(len(x) * up, dtype=np.float64)
    upsampled[::up] = x
    filtered = np.convolve(upsampled, h, mode="same")
    return filtered[::down].astype(np.float32)


def trim_silence(x: np.ndarray, sample_rate: int = SAMPLE_RATE, floor_db: float = -35.0, pad_ms: float = 15.0):
    """Cut leading and trailing quiet frames. Returns (clip, start, end) in samples of the input."""
    frame = max(1, int(sample_rate * 0.01))
    count = len(x) // frame
    if count == 0:
        return x, 0, len(x)
    levels = np.array([rms(x[i * frame:(i + 1) * frame]) for i in range(count)])
    threshold = levels.max() * 10 ** (floor_db / 20.0)
    active = np.where(levels >= threshold)[0]
    if len(active) == 0:
        return x, 0, len(x)
    pad = int(sample_rate * pad_ms / 1000)
    start = max(0, active[0] * frame - pad)
    end = min(len(x), (active[-1] + 1) * frame + pad)
    return x[start:end], start, end


def pink_noise(n: int, rng: np.random.Generator) -> np.ndarray:
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n)
    freqs[0] = freqs[1]
    out = np.fft.irfft(spectrum / np.sqrt(freqs), n)
    return (out / (rms(out) + 1e-12)).astype(np.float32)


def street_noise(n: int, sample_rate: int, rng: np.random.Generator) -> np.ndarray:
    """Synthetic traffic-like noise: low-passed pink noise with slow swells (passing vehicles) and a little hiss."""
    base = pink_noise(n, rng)
    spectrum = np.fft.rfft(base)
    freqs = np.fft.rfftfreq(n, 1.0 / sample_rate)
    spectrum *= 1.0 / (1.0 + (freqs / 600.0) ** 2)
    low = np.fft.irfft(spectrum, n)
    t = np.arange(n) / sample_rate
    envelope = np.ones(n)
    for _ in range(rng.integers(1, 4)):
        center = rng.uniform(0, n / sample_rate)
        width = rng.uniform(0.6, 1.8)
        envelope += rng.uniform(1.0, 3.0) * np.exp(-0.5 * ((t - center) / width) ** 2)
    out = low * envelope + 0.05 * rng.standard_normal(n) * rms(low)
    return (out / (rms(out) + 1e-12)).astype(np.float32)


def quiet_room(n: int, sample_rate: int, rng: np.random.Generator) -> np.ndarray:
    t = np.arange(n) / sample_rate
    hum = 0.3 * np.sin(2 * math.pi * 50 * t) + 0.1 * np.sin(2 * math.pi * 100 * t)
    out = pink_noise(n, rng) + hum + 0.2 * rng.standard_normal(n)
    return (out / (rms(out) + 1e-12)).astype(np.float32)


def babble(clips: list[np.ndarray], n: int, rng: np.random.Generator, talkers: int = 6) -> np.ndarray:
    """Sum of several talkers (each equalised) cropped to n samples. Needs clips that are long enough to loop."""
    out = np.zeros(n, dtype=np.float64)
    for _ in range(talkers):
        clip = clips[rng.integers(len(clips))]
        if len(clip) < n:
            clip = np.tile(clip, int(math.ceil(n / len(clip))))
        start = rng.integers(0, len(clip) - n + 1)
        segment = clip[start:start + n].astype(np.float64)
        out += segment / (rms(segment) + 1e-9)
    return (out / (rms(out) + 1e-12)).astype(np.float32)


def scale_background_for_snr(foreground_rms: float, background: np.ndarray, snr_db: float) -> np.ndarray:
    """Return the background scaled so that 20*log10(foreground_rms / background_rms) equals snr_db."""
    target = foreground_rms / (10 ** (snr_db / 20.0))
    return background * (target / (rms(background) + 1e-12))


def normalize_level(x: np.ndarray, target_dbfs: float = -25.0, peak_limit: float = 0.95) -> np.ndarray:
    gain = 10 ** (target_dbfs / 20.0) / (rms(x) + 1e-12)
    out = x * gain
    peak = np.max(np.abs(out)) if len(out) else 0.0
    if peak > peak_limit:
        out *= peak_limit / peak
    return out.astype(np.float32)
