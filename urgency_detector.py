"""
iTantra — Urgency Detector (Python Prototype)
=============================================
This module detects whether a transcribed utterance contains emergency content.
It mirrors the logic that will be implemented in UrgencyDetector.kt for Android.

Two signals are combined:
  1. Keyword match against a multilingual emergency vocabulary
  2. Audio amplitude (RMS energy) relative to a configurable threshold

Usage:
    # Standalone demo
    python urgency_detector.py --demo

    # From another module
    from urgency_detector import UrgencyDetector
    detector = UrgencyDetector()
    result = detector.classify(text="fire near the gate", amplitude_rms=0.8)
    print(result)  # UrgencyResult(level='high', triggers=['keyword:FIRE', 'amplitude'])
"""

import re
import math
import argparse
from dataclasses import dataclass, field
from typing import List, Optional


# ---------------------------------------------------------------------------
# Multilingual emergency keyword list
# ---------------------------------------------------------------------------
EMERGENCY_KEYWORDS = {
    # English
    "fire", "help", "sos", "emergency", "danger", "mayday", "alert",
    "evacuate", "evacuation", "alarm", "rescue", "critical", "urgent",
    "attack", "explosion", "flood", "crash", "injured", "medic",
    # Hindi (transliterated)
    "aag", "bachao", "madad", "khatra", "sankat", "haadsa",
    "aapda", "bhaago", "chot", "bezosh", "nikalो",
    # Hindi (Devanagari — matches if STT returns native script)
    "आग", "बचाओ", "मदद", "खतरा", "संकट", "हादसा",
    "आपदा", "भागो", "चोट", "बेहोश",
}

# Compiled once for speed
_KEYWORD_PATTERN = re.compile(
    r"\b(" + "|".join(re.escape(kw) for kw in EMERGENCY_KEYWORDS) + r")\b",
    flags=re.IGNORECASE | re.UNICODE,
)

# Amplitude above this fraction of max (1.0) → urgency signal
DEFAULT_AMPLITUDE_THRESHOLD = 0.65

# If multiple keywords found, treat as high urgency regardless of amplitude
MULTI_KEYWORD_COUNT = 2


@dataclass
class UrgencyResult:
    level: str  # "normal" | "high"
    triggers: List[str] = field(default_factory=list)
    matched_keywords: List[str] = field(default_factory=list)
    confidence: float = 0.0  # 0.0 = definitely normal, 1.0 = definitely high

    def to_dict(self) -> dict:
        return {
            "level": self.level,
            "triggers": self.triggers,
            "matched_keywords": self.matched_keywords,
            "confidence": round(self.confidence, 3),
        }


class UrgencyDetector:
    """
    Classifies an utterance as normal or high-urgency.

    Parameters
    ----------
    amplitude_threshold : float
        Normalized RMS amplitude (0.0–1.0) above which the audio is
        considered "shouted / distressed". Default: 0.65.
    keyword_pattern : re.Pattern
        Custom compiled regex. Defaults to the built-in EMERGENCY_KEYWORDS pattern.
    """

    def __init__(
        self,
        amplitude_threshold: float = DEFAULT_AMPLITUDE_THRESHOLD,
        keyword_pattern: re.Pattern = _KEYWORD_PATTERN,
    ):
        self.amplitude_threshold = amplitude_threshold
        self._kw_pattern = keyword_pattern

    def classify(
        self,
        text: str,
        amplitude_rms: Optional[float] = None,
    ) -> UrgencyResult:
        """
        Classify an utterance.

        Parameters
        ----------
        text : str
            Transcribed text from STT.
        amplitude_rms : float, optional
            Normalized RMS energy of the audio chunk (0.0–1.0).
            Pass None if not available (keyword-only detection).

        Returns
        -------
        UrgencyResult
        """
        triggers = []
        matched_kws = []

        # --- Signal 1: keyword detection ---
        kw_matches = self._kw_pattern.findall(text)
        if kw_matches:
            matched_kws = [kw.lower() for kw in kw_matches]
            triggers.append(f"keyword:{','.join(matched_kws).upper()}")

        # --- Signal 2: amplitude ---
        amplitude_triggered = False
        if amplitude_rms is not None and amplitude_rms >= self.amplitude_threshold:
            triggers.append("amplitude")
            amplitude_triggered = True

        # --- Decision logic ---
        high = False
        confidence = 0.0

        if len(kw_matches) >= MULTI_KEYWORD_COUNT:
            # Multiple keywords → always high, regardless of amplitude
            high = True
            confidence = 0.95
        elif kw_matches and amplitude_triggered:
            # Keyword + shouting → high
            high = True
            confidence = 0.90
        elif kw_matches:
            # Keyword alone → high (keyword is a strong signal)
            high = True
            confidence = 0.75
        elif amplitude_triggered:
            # Shouting alone → ambiguous; mark high but lower confidence
            high = True
            confidence = 0.45

        return UrgencyResult(
            level="high" if high else "normal",
            triggers=triggers,
            matched_keywords=matched_kws,
            confidence=confidence,
        )

    @staticmethod
    def compute_rms(pcm_samples: List[float]) -> float:
        """
        Compute normalized RMS energy from a list of PCM samples.
        Samples should be in range [-1.0, 1.0].
        """
        if not pcm_samples:
            return 0.0
        mean_sq = sum(s * s for s in pcm_samples) / len(pcm_samples)
        return math.sqrt(mean_sq)


# ---------------------------------------------------------------------------
# Demo / self-test
# ---------------------------------------------------------------------------
_DEMO_CASES = [
    # (text, amplitude_rms, expected_level)
    ("meeting is postponed to tomorrow", 0.3, "normal"),
    ("please report to sector seventeen", 0.4, "normal"),
    ("there is a fire near the eastern gate", 0.5, "high"),
    ("help help someone is injured", 0.6, "high"),
    ("FIRE emergency evacuate now", 0.9, "high"),
    ("madad chahiye station ke paas", 0.4, "high"),
    ("aag lagi hai", 0.7, "high"),
    ("contact Dr Raghavendra at the control room", 0.3, "normal"),
    # Shouting a non-emergency phrase
    ("the report is ready for review", 0.85, "high"),  # amplitude-only trigger
]


def run_demo():
    detector = UrgencyDetector()
    print("\niTantra — Urgency Detector Demo")
    print("=" * 70)
    print(f"{'Text':<45} {'RMS':>5}  {'Level':<8}  {'Triggers'}")
    print("-" * 70)
    passed = 0
    for text, amp, expected in _DEMO_CASES:
        result = detector.classify(text=text, amplitude_rms=amp)
        ok = "[PASS]" if result.level == expected else "[FAIL]"
        if result.level == expected:
            passed += 1
        print(
            f"{text[:44]:<44} {amp:>5.2f}  {result.level:<8}  "
            f"{','.join(result.triggers) or 'none'}  {ok}"
        )
    print("-" * 70)
    print(f"Passed: {passed}/{len(_DEMO_CASES)}\n")


def main():
    parser = argparse.ArgumentParser(description="iTantra urgency detector")
    parser.add_argument("--demo", action="store_true", help="Run built-in demo cases")
    parser.add_argument("--text", type=str, help="Classify a single text string")
    parser.add_argument("--amplitude", type=float, default=None,
                        help="Normalized RMS amplitude (0.0–1.0)")
    args = parser.parse_args()

    if args.demo:
        run_demo()
        return

    if args.text:
        detector = UrgencyDetector()
        result = detector.classify(text=args.text, amplitude_rms=args.amplitude)
        import json
        print(json.dumps(result.to_dict(), indent=2, ensure_ascii=False))
        return

    parser.print_help()


if __name__ == "__main__":
    main()
