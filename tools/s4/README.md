# S4 detector screening

Runs keyword engines over the scenes from `tools/corpus` and reports recall by SNR, false alarms per hour, detection latency and host CPU cost.

```
python3 tools/s4/screen.py --data $ECHOCATCH_DATA --model $ECHOCATCH_DATA/kws-zipformer-gigaspeech-3.3M --sweep [--aliases]
python3 tools/s4/vad_duty.py --data $ECHOCATCH_DATA --vad-model $ECHOCATCH_DATA/silero-vad/silero_vad.onnx
python3 -I -m unittest discover -s tools/s4/tests
```

- `metrics.py` defines hits, false alarms, near-miss accepts and latency (unit-tested).
- `engines.py` wraps the sherpa-onnx keyword spotter; names become BPE pieces, so any typed name works.
- `vad_duty.py` measures how much audio a voice-activity gate lets through (NFR-6).

This Python harness screens candidates quickly. The C++ benchmark (`tools/bench`) takes over once an engine is chosen, to gate regressions in CI (docs/plan/05-roadmap.md, M2-T5 and M2-T10).
