# SPDX-License-Identifier: GPL-3.0-only
"""Keyword engines for the S4 screening. Each engine turns audio into a list of (time_seconds, label) detections.

Time is the amount of audio fed when the engine reported the keyword, so it includes the engine's own look-ahead and
the chunk size, which is what a real streaming pipeline would experience.
"""
from __future__ import annotations

import time
from pathlib import Path

import numpy as np

SAMPLE_RATE = 16000


def bpe_keyword_lines(model_dir: Path, names: list[dict], with_aliases: bool) -> list[str]:
    """Keyword file lines for sherpa-onnx: BPE pieces of the typed (and optionally respelled) name, labelled with the name."""
    import sentencepiece as spm

    processor = spm.SentencePieceProcessor(model_file=str(model_dir / "bpe.model"))
    vocabulary = {line.split()[0] for line in (model_dir / "tokens.txt").read_text(encoding="utf-8").splitlines() if line.strip()}
    lines = []
    for entry in names:
        spellings = [entry["name"].upper()] + ([a.upper() for a in entry.get("aliases", [])] if with_aliases else [])
        for spelling in spellings:
            pieces = processor.encode(spelling, out_type=str)
            unknown = [p for p in pieces if p not in vocabulary]
            if unknown:
                raise ValueError(f"{spelling}: pieces not in the model vocabulary: {unknown}")
            lines.append(" ".join(pieces) + f" @{entry['name']}")
    return lines


class SherpaKws:
    """sherpa-onnx streaming keyword spotter (zipformer transducer), open vocabulary through BPE tokenisation."""

    def __init__(self, model_dir: str, names: list[dict], with_aliases: bool, boost: float, threshold: float,
                 chunk_seconds: float = 0.1, num_threads: int = 1, work_dir: str | None = None):
        import sherpa_onnx

        model = Path(model_dir)
        lines = bpe_keyword_lines(model, names, with_aliases)
        keywords_path = Path(work_dir or "/tmp") / f"keywords-{abs(hash(tuple(lines)))}.txt"
        keywords_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        suffix = "epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        self.spotter = sherpa_onnx.KeywordSpotter(
            tokens=str(model / "tokens.txt"),
            encoder=str(model / f"encoder-{suffix}"),
            decoder=str(model / f"decoder-{suffix}"),
            joiner=str(model / f"joiner-{suffix}"),
            keywords_file=str(keywords_path),
            num_threads=num_threads,
            keywords_score=boost,
            keywords_threshold=threshold,
        )
        self.chunk = int(chunk_seconds * SAMPLE_RATE)
        self.cpu_seconds = 0.0
        self.audio_seconds = 0.0

    def _drain(self, stream, fed_seconds: float, hits: list):
        while self.spotter.is_ready(stream):
            self.spotter.decode_stream(stream)
            result = self.spotter.get_result(stream)
            if result:
                hits.append((fed_seconds, result))
                self.spotter.reset_stream(stream)

    def detect(self, samples: np.ndarray) -> list[tuple[float, str]]:
        started = time.process_time()
        stream = self.spotter.create_stream()
        hits: list[tuple[float, str]] = []
        for start in range(0, len(samples), self.chunk):
            end = min(len(samples), start + self.chunk)
            stream.accept_waveform(SAMPLE_RATE, samples[start:end])
            self._drain(stream, end / SAMPLE_RATE, hits)
        stream.accept_waveform(SAMPLE_RATE, np.zeros(int(0.66 * SAMPLE_RATE), dtype=np.float32))
        stream.input_finished()
        self._drain(stream, len(samples) / SAMPLE_RATE + 0.66, hits)
        self.cpu_seconds += time.process_time() - started
        self.audio_seconds += len(samples) / SAMPLE_RATE
        return hits
