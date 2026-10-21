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


class SherpaAsrHotwords:
    """Small streaming speech recogniser (zipformer transducer) with hotword boosting for the names.

    Hotword boosting nudges the recogniser towards the typed name's BPE pieces; a detection is reported when the name
    (or one of its respelled aliases) first appears in the running transcript. A boost of 0 means plain recognition.
    """

    def __init__(self, model_dir: str, bpe_model: str, names: list[dict], with_aliases: bool, boost: float,
                 encoder_tag: str, chunk_seconds: float = 0.1, num_threads: int = 1, work_dir: str | None = None):
        import re
        import sentencepiece as spm
        import sherpa_onnx

        model = Path(model_dir)
        work = Path(work_dir or "/tmp")
        processor = spm.SentencePieceProcessor(model_file=bpe_model)
        vocab_path = work / f"bpe-{abs(hash(bpe_model))}.vocab"
        vocab_path.write_text(
            "".join(f"{processor.id_to_piece(i)}\t{processor.get_score(i)}\n" for i in range(processor.vocab_size())),
            encoding="utf-8",
        )
        self.labels: dict[str, str] = {}
        for entry in names:
            spellings = [entry["name"]] + (entry.get("aliases", []) if with_aliases else [])
            for spelling in spellings:
                self.labels[spelling.upper()] = entry["name"]
        self.patterns = {text: re.compile(r"(?<![A-Z'])" + re.escape(text) + r"(?![A-Z'])") for text in self.labels}
        hotwords_file = ""
        if boost > 0:
            hotwords_path = work / f"hotwords-{abs(hash((tuple(self.labels), boost)))}.txt"
            hotwords_path.write_text("".join(f"{text}\n" for text in self.labels), encoding="utf-8")
            hotwords_file = str(hotwords_path)
        self.recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
            tokens=str(model / "tokens.txt"),
            encoder=str(model / f"encoder-{encoder_tag}.int8.onnx"),
            decoder=str(model / f"decoder-{encoder_tag}.int8.onnx"),
            joiner=str(model / f"joiner-{encoder_tag}.int8.onnx"),
            num_threads=num_threads,
            decoding_method="modified_beam_search" if boost > 0 else "greedy_search",
            hotwords_file=hotwords_file,
            hotwords_score=boost if boost > 0 else 1.5,
            modeling_unit="bpe" if boost > 0 else "cjkchar",
            bpe_vocab=str(vocab_path) if boost > 0 else "",
        )
        self.chunk = int(chunk_seconds * SAMPLE_RATE)
        self.cpu_seconds = 0.0
        self.audio_seconds = 0.0

    def _scan(self, stream, fed_seconds: float, seen: dict, hits: list):
        while self.recognizer.is_ready(stream):
            self.recognizer.decode_stream(stream)
        text = self.recognizer.get_result(stream)
        text = text if isinstance(text, str) else getattr(text, "text", "")
        text = text.upper()
        for spelling, label in self.labels.items():
            count = len(self.patterns[spelling].findall(text))
            if count > seen.get(spelling, 0):
                hits.append((fed_seconds, label))
            seen[spelling] = count

    def detect(self, samples: np.ndarray) -> list[tuple[float, str]]:
        started = time.process_time()
        stream = self.recognizer.create_stream()
        hits: list[tuple[float, str]] = []
        seen: dict[str, int] = {}
        for start in range(0, len(samples), self.chunk):
            end = min(len(samples), start + self.chunk)
            stream.accept_waveform(SAMPLE_RATE, samples[start:end])
            self._scan(stream, end / SAMPLE_RATE, seen, hits)
        stream.accept_waveform(SAMPLE_RATE, np.zeros(int(0.66 * SAMPLE_RATE), dtype=np.float32))
        stream.input_finished()
        self._scan(stream, len(samples) / SAMPLE_RATE + 0.66, seen, hits)
        self.cpu_seconds += time.process_time() - started
        self.audio_seconds += len(samples) / SAMPLE_RATE
        return hits


class SherpaAsrTranscriber:
    """Plain streaming recognition: returns the words with the time at which each word's final form first appeared."""

    def __init__(self, model_dir: str, encoder_tag: str, chunk_seconds: float = 0.1, num_threads: int = 1):
        import sherpa_onnx

        model = Path(model_dir)
        self.recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
            tokens=str(model / "tokens.txt"),
            encoder=str(model / f"encoder-{encoder_tag}.int8.onnx"),
            decoder=str(model / f"decoder-{encoder_tag}.int8.onnx"),
            joiner=str(model / f"joiner-{encoder_tag}.int8.onnx"),
            num_threads=num_threads,
            decoding_method="greedy_search",
        )
        self.chunk = int(chunk_seconds * SAMPLE_RATE)
        self.cpu_seconds = 0.0
        self.audio_seconds = 0.0

    def _text(self, stream) -> str:
        while self.recognizer.is_ready(stream):
            self.recognizer.decode_stream(stream)
        result = self.recognizer.get_result(stream)
        return (result if isinstance(result, str) else getattr(result, "text", "")).upper().strip()

    @staticmethod
    def _update(words: list[list], text: str, time_s: float):
        current = text.split()
        for index, word in enumerate(current):
            if index >= len(words):
                words.append([time_s, word])
            elif words[index][1] != word:
                words[index] = [time_s, word]
        del words[len(current):]

    def transcribe(self, samples: np.ndarray) -> list[tuple[float, str]]:
        started = time.process_time()
        stream = self.recognizer.create_stream()
        words: list[list] = []
        for start in range(0, len(samples), self.chunk):
            end = min(len(samples), start + self.chunk)
            stream.accept_waveform(SAMPLE_RATE, samples[start:end])
            self._update(words, self._text(stream), end / SAMPLE_RATE)
        stream.accept_waveform(SAMPLE_RATE, np.zeros(int(0.66 * SAMPLE_RATE), dtype=np.float32))
        stream.input_finished()
        self._update(words, self._text(stream), len(samples) / SAMPLE_RATE + 0.66)
        self.cpu_seconds += time.process_time() - started
        self.audio_seconds += len(samples) / SAMPLE_RATE
        return [(t, w) for t, w in words]
