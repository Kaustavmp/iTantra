# Test Sentences — Audio Recording Guide

## Purpose

This folder holds `.wav` audio files used to compute a real, quotable WER (Word Error Rate) number for your SIH demo. You need at least 15 files per language before Day 1 is over.

## File Naming Convention

```
<lang>_<category>_<index>.wav
```

| Segment | Values |
|---|---|
| `lang` | `hi` (Hindi), `en` (English), `gu` (Gujarati) ... |
| `category` | `normal`, `emerg`, `num`, `name`, `noisy` |
| `index` | `01`, `02`, ... |

Examples: `hi_emerg_01.wav`, `en_noisy_02.wav`

## Recording Instructions

### Option A — SoX (command line)
```bash
# Install: https://sox.sourceforge.net/
sox -d -r 16000 -c 1 -b 16 hi_emerg_01.wav trim 0 5
```
Speak within 5 seconds after running the command.

### Option B — ffmpeg (Windows)
```powershell
# List audio devices first:
ffmpeg -list_devices true -f dshow -i dummy

# Record (replace "Microphone Array" with your actual device name):
ffmpeg -f dshow -i audio="Microphone Array" -ar 16000 -ac 1 -acodec pcm_s16le -t 5 hi_emerg_01.wav
```

### Option C — Audacity
1. File → New
2. Edit → Preferences → Quality → Default Sample Rate = 16000 Hz, Format = 16-bit PCM
3. Tracks → Stereo Track → Downmix to Mono
4. Record → Export → WAV

## After Recording

Add a matching line to `transcripts.txt`:
```
hi_emerg_01.wav|aag lagi hai poorvi gate ke paas
```

The format is exactly: `filename|expected_text`

## Noisy Variants

For the `_noisy` files, run a fan or play crowd noise at ~60–70 dB in the background while recording the same sentences. This lets you measure WER degradation in realistic field conditions.

## How Many Do You Need?

- **Minimum for Day 1:** 5 files per language (enough to get a rough WER)  
- **Target for Day 3:** 15 files per language (enough for a statistically meaningful WER to quote)
- **Ideal:** 30 files total (15 Hindi + 15 English) covering all categories above

## Running the Benchmark

```bash
cd ..  # back to repo root
python benchmark_stt.py --audio_dir ./test_sentences --transcript_file ./test_sentences/transcripts.txt
```
