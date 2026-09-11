# iTantra — Offline Multilingual Semantic Voice Transceiver

**SIH Problem Statement 26173 · ISRO / Dept. of Space**

> _"We don't compress the sound. We understand it, send the meaning, and rebuild the voice at the other end."_

[![Platform](https://img.shields.io/badge/platform-Android-brightgreen?logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin%20%7C%20Python-blue?logo=kotlin)](https://kotlinlang.org)
[![STT](https://img.shields.io/badge/STT-Whisper--tiny%20%7C%20Vosk-orange)](https://github.com/openai/whisper)
[![TTS](https://img.shields.io/badge/TTS-Piper%20%2F%20VITS--lite-purple)](https://github.com/rhasspy/piper)
[![Offline](https://img.shields.io/badge/offline-100%25-red)]()
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

---

## What Is iTantra?

iTantra is a **fully offline, device-to-device voice communication system** that works without any internet connection, cloud API, or server infrastructure.

Instead of transmitting audio, it transmits **meaning**:

```
Speech → STT (offline) → Semantic Packet (~150 bytes) → TTS (offline) → Speech
```

| Payload | Size |
|---|---|
| Raw PCM audio (3 sec, 16kHz) | ~96,000 bytes |
| Opus-compressed equivalent | ~4,500–9,000 bytes |
| **iTantra semantic packet** | **~150 bytes** |

This 640× reduction in payload size is what makes offline, low-bitrate voice relay possible on hardware as constrained as a mid-range Android phone.

---

## Architecture

```
┌──────────────── PHONE A (Sender) ────────────────┐
│  Mic → VAD → STT → Urgency Detector              │
│                          ↓                        │
│            SemanticPacket (~150 bytes)             │
│                          ↓                        │
│         Transport (Socket / Wi-Fi Direct / BT)    │
└──────────────────────────┼───────────────────────┘
                           ↓  constrained local link
┌──────────────── PHONE B (Receiver) ──────────────┐
│         Transport → Packet Receiver               │
│                          ↓                        │
│              TTS → Alert Logic → Speaker          │
│                                                    │
│  METRICS: latency | packet bytes | WER | OFFLINE  │
└──────────────────────────────────────────────────┘
```

The **Transport Layer** is a Kotlin `interface` — swap `LocalSocketTransport` → `WifiDirectTransport` → `BluetoothTransport` without touching any AI code.

---

## Quick Start

### Prerequisites

- Python 3.9+ (dev machine benchmarking)
- Android Studio Hedgehog or later
- Android device, API 26+ (deployment)
- NVIDIA GPU optional (for faster benchmarking iteration; CPU works too)

### 1. Clone & Install

```bash
git clone https://github.com/Kaustavmp/iTantra.git
cd iTantra
pip install -r requirements.txt
```

### 2. Run the STT Benchmark (do this before touching Android)

```bash
mkdir test_sentences
# place .wav files in test_sentences/  (16kHz mono recommended)
# fill transcripts.txt: one line per file, format: filename|expected_text
python benchmark_stt.py --audio_dir ./test_sentences --transcript_file ./transcripts.txt
```

Expected output:

```
============================================================
Model: tiny  |  Device: cpu  |  Compute: int8
============================================================
Avg latency per utterance: 0.402s
Avg WER:                   4.2%
Meets 15% WER target:      YES ✅
```

### 3. Validate a Semantic Packet

```bash
python packet_validator.py
```

### 4. Run Urgency Detector Demo

```bash
python urgency_detector.py --demo
```

### 5. Android App

Open the `android/` directory in Android Studio, sync Gradle, and run on a connected device.
See [`android/SETUP.md`](android/SETUP.md) for sherpa-onnx model download instructions.

---

## Repository Structure

```
iTantra/
├── README.md                          ← You are here
├── iTantra_Architecture_Blueprint.md  ← Full 4-day technical blueprint
├── requirements.txt                   ← Python dev dependencies
├── benchmark_stt.py                   ← STT benchmarking (GPU + CPU)
├── urgency_detector.py                ← Urgency keyword detector (Python prototype)
├── packet_validator.py                ← Validates SemanticPacket against JSON schema
├── packet_schema.json                 ← Canonical packet schema
├── .gitignore
├── test_sentences/
│   ├── transcripts.txt                ← Ground-truth for WER computation
│   └── README.md                      ← How to record test audio
├── android/
│   ├── SETUP.md                       ← Android project setup + model downloads
│   └── app/src/main/java/dev/itantra/
│       ├── core/
│       │   ├── SemanticPacket.kt
│       │   ├── UrgencyDetector.kt
│       │   └── Transport.kt
│       ├── stt/SttEngine.kt
│       ├── tts/TtsEngine.kt
│       └── ui/
│           ├── TalkScreen.kt
│           └── MetricsScreen.kt
└── docs/
```

---

## Success Targets (SIH Judging Criteria)

| Criterion | Weight | Target | Live Proof |
|---|---|---|---|
| **Accuracy** | 40% | WER ≤ 15% (Hindi + English) | On-screen WER counter vs pre-recorded test set |
| **Efficiency** | 20% | Model ≤ 300MB, RAM ≤ 500MB, CPU ≤ 50% | Live device monitor overlay |
| **Latency** | 20% | End-to-end ≤ 1.5 sec | On-screen stopwatch, capture-to-playback |
| **Offline / UX** | 20% | 100% offline, 2+ languages, alert mode | Network-off demo |

---

## Model Stack

| Component | Model | Size | Runtime |
|---|---|---|---|
| STT (primary) | Whisper-tiny (INT8) | ~75 MB | sherpa-onnx Android |
| STT (fallback) | Vosk-small (hi + en) | ~50 MB each | Vosk Android |
| TTS | Piper / VITS-lite | ~20–60 MB/voice | sherpa-onnx Android |
| VAD | Energy threshold + silence timeout | 0 MB | Custom Kotlin |

---

## Semantic Packet Format

```json
{
  "msg_id": "a1b2c3d4",
  "lang": "hi",
  "text": "madad chahiye station ke paas",
  "urgency": "high",
  "confidence": 0.91,
  "ts": 1725620000123
}
```

Typical size: **120–180 bytes**. Full schema: [`packet_schema.json`](packet_schema.json)

---

## Emergency / Urgency Detection

The urgency detector triggers on:
- **Keyword match**: FIRE, HELP, SOS, EMERGENCY, DANGER, MAYDAY, BACHAO, AAGI, MADAD, ALERT
- **Amplitude**: audio energy above configurable threshold (shouting)
- **Duration**: short, clipped utterances typical of distress

When `urgency == "high"`:
- Receiver plays at max volume, non-interruptible
- Distinct alert tone prefix played before message
- Device vibration pattern activated

---

## Language Support

| Language | Code | STT | TTS | Status |
|---|---|---|---|---|
| Hindi | `hi` | Whisper-tiny + Vosk | Piper hi-IN | ✅ Day 1–2 |
| English | `en` | Whisper-tiny + Vosk | Piper en-US | ✅ Day 1–2 |
| Gujarati | `gu` | Whisper-tiny | Piper gu-IN | 🔜 Day 4 |
| Marathi | `mr` | Whisper-tiny | Piper mr-IN | 🔜 Day 4 |
| Bengali | `bn` | Whisper-tiny | Piper bn-IN | 🔜 Stretch |

---

## The Killer Demo Sequence

1. Both phones visible — Wi-Fi and mobile data **OFF**
2. Speak in Hindi on Phone A → heard on Phone B in ~1 second
3. Point at screen: **"142 bytes transmitted. Raw audio would be 96,000 bytes."**
4. Shout **"FIRE"** → Phone B: max volume, vibration, alert tone, non-silenceable
5. _"We're not transmitting the sound. We're transmitting the meaning — and rebuilding the voice on the other end."_

---

## Judge Q&A

| Question | Answer |
|---|---|
| "Why not WhatsApp?" | Needs internet + sends full audio. We need neither. |
| "Why not SMS?" | No voice experience, no urgency, not hands-free in an emergency. |
| "Opus compresses well." | Opus compresses waveforms. We skip the waveform entirely. Different category. |
| "What if STT gets it wrong?" | Confidence score on screen; low confidence → "please repeat" prompt. |
| "Show on Bluetooth." | Transport is an abstracted interface — config swap, not a rewrite. |

---

## Privacy & Security

- **Zero cloud dependency** — no audio or text ever leaves the device pair
- Packets contain transcribed text only — no raw audio stored or transmitted
- `msg_id` is a random UUID fragment — no user identity embedded
- For production hardening: AES-256 payload encryption + HMAC packet integrity check

---

## Contributing

1. Fork the repo
2. `git checkout -b feature/your-feature`
3. Commit your changes with clear messages referencing the blueprint section
4. Open a PR

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*Built for Smart India Hackathon 2026 · ISRO / Dept. of Space · SIH26173*
