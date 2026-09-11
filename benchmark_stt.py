"""
iTantra — STT Model Benchmark Script
=====================================
Run this on your dev machine (RTX 3050 4GB + R5) BEFORE writing any Android code.

It answers the one question that decides your model choice:
    "Which model gives acceptable WER at acceptable latency,
     when forced to run the way a phone's CPU actually would?"

Usage:
    pip install -r requirements.txt
    python benchmark_stt.py --audio_dir ./test_sentences --transcript_file ./transcripts.txt

Folder layout expected:
    test_sentences/
        hi_01.wav
        hi_02.wav
        en_01.wav
        ...
    transcripts.txt   (one line per file, format: filename|expected_text)

If you don't have recordings yet, use --mic to record test sentences live
(see --mic flag below), then fill transcripts.txt with what was actually said.
"""

import argparse
import time
import os
import sys

try:
    from faster_whisper import WhisperModel
except ImportError:
    print("Missing dependency. Run: pip install -r requirements.txt")
    sys.exit(1)


def word_error_rate(reference: str, hypothesis: str) -> float:
    """Simple WER via Levenshtein distance on word sequences."""
    ref_words = reference.lower().split()
    hyp_words = hypothesis.lower().split()

    d = [[0] * (len(hyp_words) + 1) for _ in range(len(ref_words) + 1)]
    for i in range(len(ref_words) + 1):
        d[i][0] = i
    for j in range(len(hyp_words) + 1):
        d[0][j] = j

    for i in range(1, len(ref_words) + 1):
        for j in range(1, len(hyp_words) + 1):
            if ref_words[i - 1] == hyp_words[j - 1]:
                d[i][j] = d[i - 1][j - 1]
            else:
                d[i][j] = 1 + min(d[i - 1][j], d[i][j - 1], d[i - 1][j - 1])

    if len(ref_words) == 0:
        return 0.0
    return d[len(ref_words)][len(hyp_words)] / len(ref_words)


def load_transcripts(path: str) -> dict:
    transcripts = {}
    if not os.path.exists(path):
        print(f"Warning: transcript file '{path}' not found. WER will not be computed.")
        return transcripts
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or "|" not in line:
                continue
            fname, text = line.split("|", 1)
            transcripts[fname.strip()] = text.strip()
    return transcripts


def run_benchmark(model_size: str, device: str, compute_type: str,
                   audio_dir: str, transcripts: dict):
    print(f"\n{'=' * 60}")
    print(f"Model: {model_size}  |  Device: {device}  |  Compute: {compute_type}")
    print(f"{'=' * 60}")

    load_start = time.time()
    model = WhisperModel(model_size, device=device, compute_type=compute_type)
    load_time = time.time() - load_start
    print(f"Model load time: {load_time:.2f}s")

    audio_files = sorted(
        f for f in os.listdir(audio_dir)
        if f.lower().endswith((".wav", ".mp3", ".flac"))
    )

    if not audio_files:
        print(f"No audio files found in {audio_dir}")
        return

    latencies = []
    wers = []

    for fname in audio_files:
        path = os.path.join(audio_dir, fname)
        t0 = time.time()
        segments, info = model.transcribe(path, language=None, beam_size=1)
        hypothesis = " ".join(seg.text for seg in segments).strip()
        elapsed = time.time() - t0
        latencies.append(elapsed)

        result_line = f"  {fname:20s} | {elapsed:.3f}s | lang={info.language} | \"{hypothesis}\""

        if fname in transcripts:
            wer = word_error_rate(transcripts[fname], hypothesis)
            wers.append(wer)
            result_line += f" | WER={wer * 100:.1f}%"

        print(result_line)

    print(f"\n--- Summary: {model_size} / {device} / {compute_type} ---")
    print(f"Avg latency per utterance: {sum(latencies) / len(latencies):.3f}s")
    print(f"Min / Max latency:         {min(latencies):.3f}s / {max(latencies):.3f}s")
    if wers:
        print(f"Avg WER:                   {sum(wers) / len(wers) * 100:.1f}%")
        target_met = (sum(wers) / len(wers)) <= 0.15
        print(f"Meets 15% WER target:      {'YES ✅' if target_met else 'NO ❌'}")
    print()


def main():
    parser = argparse.ArgumentParser(description="iTantra STT benchmark")
    parser.add_argument("--audio_dir", default="./test_sentences",
                         help="Folder containing test .wav files")
    parser.add_argument("--transcript_file", default="./transcripts.txt",
                         help="filename|expected_text, one per line")
    parser.add_argument("--models", nargs="+", default=["tiny", "base"],
                         help="Whisper model sizes to test")
    args = parser.parse_args()

    if not os.path.isdir(args.audio_dir):
        print(f"Audio directory '{args.audio_dir}' not found.")
        print("Create it and add a few .wav test recordings (16kHz mono works best), then re-run.")
        sys.exit(1)

    transcripts = load_transcripts(args.transcript_file)

    print("iTantra STT Benchmark")
    print("Testing each model on GPU (fast dev loop) AND forced CPU (phone-realistic proxy)")

    for model_size in args.models:
        # GPU pass — fast iteration, NOT representative of final phone deployment
        try:
            run_benchmark(model_size, device="cuda", compute_type="float16",
                          audio_dir=args.audio_dir, transcripts=transcripts)
        except Exception as e:
            print(f"GPU run failed for {model_size} ({e}) — skipping, will still run CPU pass.")

        # CPU pass — THIS is your phone-realistic number. Trust this one for decisions.
        run_benchmark(model_size, device="cpu", compute_type="int8",
                      audio_dir=args.audio_dir, transcripts=transcripts)

    print("=" * 60)
    print("DECISION RULE:")
    print("Pick the smallest model whose CPU/int8 WER clears your 15% target.")
    print("That CPU/int8 number is your best proxy for what the demo phone will do.")
    print("=" * 60)


if __name__ == "__main__":
    main()
