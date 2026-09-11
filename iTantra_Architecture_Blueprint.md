# iTantra — Architecture & 4-Day Build Blueprint
### Offline Multilingual Semantic Voice Transceiver — SIH26173 (ISRO / Dept. of Space)

---

## 0. The One-Line Positioning

> **We don't compress the sound. We understand it, send the meaning, and rebuild the voice at the other end.**

Everyone else will pitch "voice compression." You pitch **semantic relay**: speech → text/meaning → tiny packet → speech. This is the framing to repeat in every slide and every judge answer.

---

## 1. Hardware Reality Check (read this before writing code)

| Machine | Role | What runs here |
|---|---|---|
| **Your PC: RTX 3050 4GB + R5** | Development & benchmarking rig | Rapid testing of STT/TTS model candidates, quantization experiments, dataset prep, training any fine-tunes |
| **Android phone (demo device)** | Deployment target | Final CPU-only inference — **no discrete GPU on a phone**. Models must run via ONNX Runtime Mobile, TFLite, or whisper.cpp/sherpa-onnx Android bindings |

**Implication:** your GPU's job is to let you iterate fast and pick the *smallest model that still hits your accuracy target* — not to run the final demo. Every model you shortlist must have a credible CPU-on-phone story. This is exactly why Whisper-tiny/base + Vosk + Piper/VITS-lite are the right family: all sub-300MB, all proven on ARM CPUs.

---

## 2. Success Targets (mapped to official SIH weighting)

| Judged criterion | Weight | Your concrete target | How you'll prove it live |
|---|---|---|---|
| **Accuracy** | 40% | WER ≤ 15% (Hindi/English clear speech) | On-screen WER counter vs. a pre-recorded test set |
| **Efficiency** | 20% | Model ≤ 300MB, RAM ≤ 500MB, CPU ≤ 50% | Live device monitor overlay in the app |
| **Latency** | 20% | End-to-end (speak → hear) ≤ 1.5 sec | On-screen stopwatch, capture-to-playback |
| **Remaining/overall** | 20% | Offline proof + language coverage + UX polish | Network-off demo, alert mode, clean UI |

Do not guess these numbers on stage — measure them with the benchmark script in Section 9 and quote the real ones.

---

## 3. System Architecture

```
┌─────────────────────────────── PHONE A (Sender) ───────────────────────────────┐
│                                                                                  │
│   🎙️ Mic (16kHz mono)                                                          │
│         │                                                                       │
│         ▼                                                                       │
│   ┌─────────────┐     energy threshold + silence timeout                       │
│   │   VAD /     │     chunks continuous audio into utterances                  │
│   │  Chunking   │                                                              │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   ┌─────────────┐                                                              │
│   │  Offline    │     Whisper-tiny/base (INT8) or Vosk-small                   │
│   │    STT      │     → text + language tag + confidence                      │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   ┌─────────────┐                                                              │
│   │  Urgency    │     keyword match (FIRE/HELP/SOS/...) + amplitude/duration    │
│   │  Detector   │     → urgency: normal | high                                 │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   ┌─────────────┐                                                              │
│   │  Semantic   │     {msg_id, lang, text, urgency, ts} → <300 bytes           │
│   │   Packet    │                                                              │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   ┌─────────────┐                                                              │
│   │  Transport  │     Local Socket (primary demo) → Wi-Fi Direct → Bluetooth   │
│   │   Layer     │     (pluggable interface — swap without touching AI code)    │
│   └──────┬──────┘                                                              │
└──────────┼───────────────────────────────────────────────────────────────────┘
           │
           ▼  📡 constrained / local link (throttled in software to prove low-bitrate claim)
┌──────────┼───────────────────────────────── PHONE B (Receiver) ────────────────┐
│          ▼                                                                      │
│   ┌─────────────┐                                                              │
│   │   Packet    │     parse + validate schema                                  │
│   │  Receiver   │                                                              │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   ┌─────────────┐                                                              │
│   │  Offline    │     Piper/VITS-lite or Indic-TTS (per lang tag)              │
│   │    TTS      │                                                              │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   ┌─────────────┐     if urgency == high:                                     │
│   │  Playback + │       max volume, non-interruptible, alert tone, vibration   │
│   │ Alert Logic │     else: normal playback                                    │
│   └──────┬──────┘                                                              │
│          ▼                                                                      │
│   🔊 Speaker                                                                   │
│                                                                                  │
│   ┌────────────────────────────────────────────────────────────────────┐      │
│   │  METRICS OVERLAY (both phones)                                     │      │
│   │  Latency: 0.84s | Packet: 142 bytes | WER: 8.2% | Internet: OFF    │      │
│   └────────────────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Critical design rule:** the Transport Layer is an *interface*, not a hard dependency. STT/TTS code never talks to sockets directly — it hands a packet to a `Transport` abstraction. This is what lets you answer "now switch to Bluetooth" live without touching your AI code.

```kotlin
interface Transport {
    fun send(packet: SemanticPacket)
    fun onReceive(callback: (SemanticPacket) -> Unit)
}

class LocalSocketTransport : Transport { /* Day 2 implementation */ }
class WifiDirectTransport : Transport { /* Day 3 if time permits */ }
class BluetoothTransport : Transport { /* stretch goal */ }
```

---

## 4. Semantic Packet Schema

This is your "neural transceiver" abstraction — the artifact you show judges to prove the bitrate claim.

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

Typical size: **120–180 bytes**. Compare this on-screen against:

| Payload | Size |
|---|---|
| Raw PCM (16kHz, 16-bit, mono, 3 sec) | ~96,000 bytes |
| Opus-compressed equivalent | ~4,500–9,000 bytes |
| **iTantra semantic packet** | **~150 bytes** |

Show all three numbers side by side in your demo — don't just claim "low bitrate," display the comparison table live. This is the single most judge-proof moment in the whole pitch.

---

## 5. Model Selection Matrix (for phone-CPU deployment)

| Component | Model | Size | Why |
|---|---|---|---|
| **STT (primary)** | Whisper-tiny (INT8, via whisper.cpp/sherpa-onnx) | ~75MB | Best accuracy-to-size ratio for clear speech; runs on CPU |
| **STT (fallback/fast)** | Vosk-small (Hindi + English models) | ~50MB each | Lower accuracy but near-instant, good for short commands/alerts |
| **TTS** | Piper (VITS-lite) via sherpa-onnx | ~20–60MB per voice | Purpose-built for offline mobile, natural-enough for demo |
| **VAD** | Energy-threshold + silence timeout (custom, no ML needed) | 0 MB | Simpler = fewer failure modes; sherpa-onnx VAD as upgrade if time allows |

**Do NOT start with:** IndicConformer-600M, Whisper-small/medium, or any model >300MB. These are server-grade, not phone-grade. If your accuracy is too low with tiny/base, the fix is better VAD chunking and a curated test set — not a bigger model that won't run on the demo phone anyway.

**On your 3050 4GB — validate before you commit:** run Whisper-tiny AND Whisper-base through the benchmark script in Section 9, on both GPU (fast iteration) and forced-CPU mode (realistic phone proxy). Pick the smallest model that clears your 15% WER target in **CPU mode**, since that's what the phone will actually run.

---

## 6. Tech Stack

| Layer | Choice |
|---|---|
| Android language | Kotlin |
| Audio I/O | `AudioRecord` (capture) + `AudioTrack` (playback), 16kHz mono |
| STT/TTS runtime | sherpa-onnx (bundles ONNX Runtime, has ready Android bindings for both Whisper and Piper) |
| Networking (demo) | Raw TCP socket over shared local Wi-Fi (simplest to get working in 4 days) |
| Networking (stretch) | Android `WifiP2pManager` (Wi-Fi Direct), Bluetooth Classic as fallback |
| UI | Jetpack Compose — keep it to 2 screens (Talk screen + Sovereignty/Metrics screen) |
| Packet format | JSON (org.json — no extra dependency needed) |
| Dev-machine prototyping | Python + `faster-whisper` (CTranslate2, INT8) — mirrors sherpa-onnx behavior closely enough for early validation |

---

## 7. Day-by-Day Build Plan

### Day 1 — Prove the core loop works at all (single device)
- [ ] Run `benchmark_stt.py` (Section 9) on your PC — record WER/latency for Whisper-tiny vs base, GPU vs CPU-forced
- [ ] Set up Android project skeleton, request mic permission
- [ ] Integrate sherpa-onnx: mic → VAD → STT → text shown on screen
- [ ] Integrate sherpa-onnx TTS: typed/recognized text → speaker
- [ ] **Milestone:** one phone, speak → see text → hear it spoken back. No networking yet.

### Day 2 — Two-device loop
- [ ] Implement `SemanticPacket` data class + JSON serialization
- [ ] Implement `LocalSocketTransport` (simple TCP client/server over shared Wi-Fi)
- [ ] Wire: Phone A mic → STT → packet → socket → Phone B → TTS → speaker
- [ ] Add on-screen latency stopwatch (timestamp at capture start, timestamp at playback start)
- [ ] **Milestone:** two phones, speaker → listener loop working end-to-end, latency visible

### Day 3 — Robustness + the demo moments
- [ ] Add urgency keyword detection (FIRE, HELP, SOS, EMERGENCY, DANGER) → high-priority packet
- [ ] Alert playback: max volume, vibration, non-interruptible, distinct tone prefix
- [ ] Add packet-size + raw-PCM-equivalent comparison readout (Section 4 table, live)
- [ ] Add second language (Hindi + English minimum)
- [ ] Build the "Sovereignty" screen: Internet OFF indicator, external calls = 0 counter
- [ ] **Rehearse the network-unplug moment** — literally turn off Wi-Fi/mobile data, run the full loop, confirm it still works

### Day 4 — Polish, benchmark, rehearse
- [ ] Run your test sentence set (Section 8) end-to-end, log real WER/latency numbers — put them on a slide
- [ ] Record a clean backup demo video (in case live demo has issues)
- [ ] Prepare the 3-payload comparison slide (raw PCM vs Opus vs your packet)
- [ ] Rehearse Q&A: "why not WhatsApp/SMS/Opus/Bhashini" (you already have strong answers from earlier analysis)
- [ ] If everything above is solid and time remains: third language, Bluetooth fallback

**Rule for all 4 days:** if something isn't working by the end of its allotted day, cut scope — don't carry debt forward. A rock-solid 2-language demo beats a shaky 5-language one.

---

## 8. Test Sentence Set (build this on Day 1, use it Day 1–4)

Create ~15 sentences per language, in these categories, and keep them in a text file you reuse for every benchmark run:

```
Normal:      "Meeting is postponed to tomorrow evening."
Emergency:   "There is a fire near the eastern gate, help needed immediately."
Numbers:     "Please report to sector seventeen, gate number four."
Names:       "Contact Dr. Raghavendra at the control room."
Noisy:       (same sentences recorded with background noise/fan on)
```

This set is what gives you a real, quotable WER number instead of a guess — and it's the same set you use to show the judge a live accuracy readout.

---

## 9. Benchmark Script (run this TODAY on your PC)

See `benchmark_stt.py` and `requirements.txt` — this validates model choice against your actual hardware before you touch Android. It tests both GPU (fast dev loop) and CPU-forced mode (phone proxy), and prints WER, latency, and RTF (real-time factor) for each.

---

## 10. What NOT to Build

Cut these immediately if anyone on the team suggests them — they don't move any of the four judged criteria and will eat days you don't have:

- ❌ Full 10-language support before Day 4 (2 languages, rock-solid, beats 10 shaky ones)
- ❌ Wi-Fi Direct on Day 1–2 (local socket proves the concept; add P2P transport only if Day 3 has slack)
- ❌ Cloud/Bhashini fallback as part of the judged path (keep it out of the demo entirely — the PS wants offline, don't muddy that story)
- ❌ Training/fine-tuning a custom STT model (pretrained + quantization is the right call in 4 days)
- ❌ Fancy UI animations before the core loop is reliable

---

## 11. Judge Q&A — Quick Reference

| Question | Your answer |
|---|---|
| "Why not just use WhatsApp?" | WhatsApp needs internet and sends full audio. We need neither — fully offline, device-to-device, and the payload is meaning, not sound. |
| "Why not SMS?" | SMS solves bandwidth but kills the voice experience — no urgency, no hands-free use in an emergency. We keep voice at both ends. |
| "Opus already compresses speech well." | Agreed — and we're not competing with it. Opus compresses the waveform; we skip the waveform entirely. Different category, not a better version of the same idea. |
| "What if STT gets it wrong?" | We show confidence score on-screen; low-confidence utterances can trigger a "please repeat" prompt. Emergency keywords get extra weight in detection. |
| "Show it on Bluetooth instead of Wi-Fi." | Transport layer is abstracted behind one interface — this is a config swap, not a rewrite. (Only claim this if you actually built the interface this way — see Section 3.) |

---

## 12. The Killer Demo Moment (rehearse this exact sequence)

1. Show both phones — Wi-Fi and mobile data visibly OFF.
2. Speak a normal sentence in Hindi on Phone A → hear it spoken back on Phone B in ~1 second.
3. Point at the screen: **"142 bytes transmitted. Raw audio would have been 96,000 bytes."**
4. Shout "FIRE" into Phone A → Phone B jumps to max volume, vibrates, plays a distinct alert tone, cannot be silenced by the normal controls.
5. Say the line: *"We're not trying to transmit the sound. We're transmitting the meaning — and rebuilding the voice on the other end."*

That's the 45 seconds that wins the room.

---

## 13. Privacy & Security Model

iTantra's offline architecture has a strong privacy story — but only if you're deliberate about it. Use this section to answer any judge question about data handling.

### What leaves the device pair?

**Nothing.** All computation is local. The only data on the wire is the SemanticPacket JSON (~150 bytes of transcribed text + metadata).

### What is stored?

- No audio is written to disk (AudioRecord buffer → immediate processing → discard)
- No packet log is persisted by default (the metrics overlay is in-memory only)
- `msg_id` is a random UUID fragment — it cannot be traced to a user

### What to add for production hardening (not needed for SIH demo)

| Threat | Mitigation |
|---|---|
| Packet interception on shared Wi-Fi | AES-256-GCM payload encryption, ECDH key exchange on first connect |
| Replay attacks | `ts` + `msg_id` deduplication window (reject packets > 5 sec old) |
| Rogue receiver | Pre-shared pairing PIN or QR code device pairing |
| Persistent logging risk | Explicit opt-in logging only; clear on app exit |

**Demo answer if asked:** _"By design, the only thing that crosses the network is a text string — less than 200 bytes. No audio is ever stored or transmitted. We don't even have a server to breach."_

---

## 14. Repository Structure

```
iTantra/
├── README.md                          ← Project overview + quickstart (GitHub landing page)
├── iTantra_Architecture_Blueprint.md  ← This file — full 4-day technical blueprint
├── requirements.txt                   ← Python dev dependencies
├── benchmark_stt.py                   ← STT model validation (GPU + CPU modes)
├── urgency_detector.py                ← Urgency keyword/amplitude detector (Python prototype)
├── packet_validator.py                ← SemanticPacket schema validator + size report
├── packet_schema.json                 ← Canonical JSON schema for SemanticPacket
├── LICENSE                            ← MIT
├── .gitignore                         ← Python + Android + IDE ignores
├── test_sentences/
│   ├── README.md                      ← Recording instructions
│   └── transcripts.txt                ← 30 ground-truth sentences (Hindi + English)
├── android/
│   ├── SETUP.md                       ← Model downloads + Gradle setup
│   └── app/src/main/java/dev/itantra/
│       ├── core/
│       │   ├── SemanticPacket.kt      ← Data class + JSON serialization + size()
│       │   ├── UrgencyDetector.kt     ← Keyword + amplitude urgency classifier
│       │   └── Transport.kt           ← Interface + LocalSocket + WifiDirect + BT stubs
│       ├── stt/SttEngine.kt           ← sherpa-onnx STT wrapper (Day 1 TODOs marked)
│       ├── tts/TtsEngine.kt           ← sherpa-onnx Piper TTS wrapper (Day 1 TODOs marked)
│       └── ui/
│           ├── TalkScreen.kt          ← Main UI (Jetpack Compose) — TODO Day 1
│           └── MetricsScreen.kt       ← Sovereignty/metrics overlay — TODO Day 3
└── docs/
    └── assets/                        ← Diagrams, screenshots for README
```

---

## 15. Exact Dependency Versions (pin these, don't float)

### Python (requirements.txt)

| Package | Version | Why pinned |
|---|---|---|
| `faster-whisper` | `1.0.3` | Stable CTranslate2 integration; API stable |
| `ctranslate2` | `>=4.0` | Minimum for INT8 quantization support |
| `soundfile` | `>=0.12.1` | WAV I/O |
| `numpy` | `>=1.24.0` | Float32 array ops for RMS |

### Android (app/build.gradle.kts)

| Library | Version | Notes |
|---|---|---|
| `sherpa-onnx-android` | `1.10.x` | Via JitPack; check latest release |
| `compose-bom` | `2024.04.01` | Pin BOM, not individual artifacts |
| `activity-compose` | `1.9.0` | Required for Compose Activity |
| `lifecycle-viewmodel-compose` | `2.7.0` | State management |
| `kotlinx-coroutines-android` | `1.7.3` | Background STT/TTS execution |
| `minSdk` | `26` (Android 8.0) | Lowest SDK with `AudioRecord` callbacks needed |
| `targetSdk` | `34` | Android 14 |
| Kotlin | `1.9.x` | Must match Compose compiler |

---

## 16. Multilingual Expansion Plan (Day 4 / Stretch)

The architecture is language-agnostic — Whisper-tiny supports 99 languages out of the box. Adding a new language requires:

1. **STT:** No model change needed (Whisper auto-detects). Add language code to `packet_schema.json` enum.
2. **TTS:** Download the corresponding Piper voice model (20–60 MB each).
3. **Urgency keywords:** Add transliterated + native-script terms to `EMERGENCY_KEYWORDS` in `UrgencyDetector.kt` and `urgency_detector.py`.
4. **UI:** Add language selector chip in TalkScreen.

### Priority order for SIH

| Priority | Language | Code | Piper voice available? | Urgency keywords to add |
|---|---|---|---|---|
| 1 | Hindi | `hi` | ✅ hi-IN | आग, बचाओ, मदद, खतरा |
| 2 | English | `en` | ✅ en-US | fire, help, sos, emergency |
| 3 | Gujarati | `gu` | ✅ gu-IN | આગ, મદદ, ખતરો |
| 4 | Marathi | `mr` | ✅ mr-IN | आग, मदत, धोका |
| 5 | Bengali | `bn` | ✅ bn-IN | আগুন, সাহায্য, বিপদ |

**Rule:** Don't add a new language until the previous one passes the 15% WER test. Breadth beats depth only after the core is solid.

---

## 17. CI/CD Notes (Optional, but looks good to judges)

If time allows on Day 4, add a GitHub Actions workflow:

```yaml
# .github/workflows/python-tests.yml
name: Python Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: "3.11" }
      - run: pip install -r requirements.txt
      - run: pytest --tb=short
      - run: python urgency_detector.py --demo
      - run: python packet_validator.py
```

This gives you a green ✅ badge on the GitHub README — a small but impressive signal of engineering maturity for SIH judges browsing the repo.

---

## 18. Common Failure Modes & Mitigations

Prepare for these on demo day — each has a 30-second recovery path.

| Failure | Symptom | Recovery |
|---|---|---|
| STT not responding | No text appears after speaking | Tap **Restart STT** button (force re-init the sherpa recognizer) |
| Socket connection lost | Packet counter frozen | Both phones reconnect automatically (implement auto-reconnect in LocalSocketTransport) |
| TTS silent | Packet received but no audio | Check Android volume — HIGH urgency overrides; for NORMAL, check media stream volume |
| WER too high on demo | Judges see high error rate | Switch to **Vosk fallback** mode (short command phrases, near-perfect on clear speech) |
| Phone overheating | Latency spikes above 1.5s | Reduce sherpa `numThreads` to 1; use smaller model |
| Demo phone runs out of storage | App won't install | Pre-install + verify total APK+model size ≤ 500MB; clear phone storage before event |

**The golden rule:** always have the backup demo video ready. If live demo fails, pivot to the video within 10 seconds — don't debug on stage.
