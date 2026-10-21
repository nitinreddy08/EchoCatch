# Benchmark corpus tooling

Builds the audio used by the S4 detector screening ([docs/plan/04-spikes.md](../../docs/plan/04-spikes.md)). No audio, model or dataset is committed. Everything is fetched or generated into a data directory (`$ECHOCATCH_DATA`, default `tools/corpus/data`, ignored by git).

```
pip install -r tools/requirements-s4.txt        # use a virtual environment
python3 tools/corpus/fetch.py --pin             # models, voices, text, VAD model; verifies SHA-256 (fetch.py --help)
python3 tools/corpus/synth.py                   # name, phrase and sentence audio from 8 synthetic voices
python3 tools/corpus/build_scenes.py            # 8 s scenes with labels: scenes/*.wav and scenes/scenes.jsonl
python3 -I -m unittest discover -s tools/corpus/tests
```

What the scenes contain (all synthetic):
- **Positives:** one of 12 test names, spoken alone, before a phrase, or after a lead-in, by 8 voices (US, GB, and two "Hindi-voice" speakers reading English), mixed into babble, synthetic street noise, a competing talker or a quiet room at 20, 10, 5 and 0 dB SNR. SNR is the name's loudness over the background's.
- **Negatives:** speech without any target name, near-miss names (Mia, Zack, Danielle, Sophie, Pria, Argon), other names, and background only.

Limits to keep in mind: synthetic voices and noise are not real recordings, the respelled pronunciations in `names.json` are approximations, and the sentences come from old books. Results from these scenes are screening evidence. Acceptance needs real recordings (`docs/plan/06-quality-and-testing.md`).

`sources.json` records the licence of every input. Hosts like OpenSLR, Zenodo and Hugging Face were not reachable from the development sandbox, so real speech and noise corpora are not included yet.
