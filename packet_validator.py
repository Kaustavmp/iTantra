"""
iTantra — Semantic Packet Validator
=====================================
Validates a SemanticPacket dict or JSON string against the packet_schema.json
specification, and prints a size breakdown for the demo slide.

Usage:
    python packet_validator.py                   # runs built-in examples
    python packet_validator.py '{"msg_id":"ab","lang":"hi","text":"help","urgency":"high","ts":123}'
"""

import json
import sys
import os
from typing import Union


SCHEMA_PATH = os.path.join(os.path.dirname(__file__), "packet_schema.json")

# Supported language codes (from schema)
SUPPORTED_LANGS = {"hi", "en", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn"}
SUPPORTED_URGENCY = {"normal", "high"}


class PacketValidationError(Exception):
    pass


def validate_packet(packet: dict) -> None:
    """
    Validate a SemanticPacket dict.
    Raises PacketValidationError with a descriptive message on any violation.
    """
    required_fields = ["msg_id", "lang", "text", "urgency", "ts"]
    for field in required_fields:
        if field not in packet:
            raise PacketValidationError(f"Missing required field: '{field}'")

    if not isinstance(packet["msg_id"], str) or not packet["msg_id"]:
        raise PacketValidationError("'msg_id' must be a non-empty string")

    if packet["lang"] not in SUPPORTED_LANGS:
        raise PacketValidationError(
            f"'lang' must be one of {sorted(SUPPORTED_LANGS)}, got: '{packet['lang']}'"
        )

    if not isinstance(packet["text"], str) or not packet["text"].strip():
        raise PacketValidationError("'text' must be a non-empty string")

    if packet["urgency"] not in SUPPORTED_URGENCY:
        raise PacketValidationError(
            f"'urgency' must be 'normal' or 'high', got: '{packet['urgency']}'"
        )

    if not isinstance(packet["ts"], (int, float)) or packet["ts"] <= 0:
        raise PacketValidationError("'ts' must be a positive integer (Unix ms timestamp)")

    if "confidence" in packet:
        c = packet["confidence"]
        if not isinstance(c, (int, float)) or not (0.0 <= c <= 1.0):
            raise PacketValidationError(
                f"'confidence' must be a float in [0.0, 1.0], got: {c}"
            )


def packet_size_report(packet: dict) -> dict:
    """
    Compute packet size and generate a comparison for the demo slide.
    Returns a dict with sizes in bytes.
    """
    json_bytes = len(json.dumps(packet, ensure_ascii=False).encode("utf-8"))
    raw_pcm_3sec = 16000 * 2 * 3  # 16kHz, 16-bit, mono, 3s = 96,000 bytes
    opus_low = 4500
    opus_high = 9000

    return {
        "packet_bytes": json_bytes,
        "raw_pcm_3sec_bytes": raw_pcm_3sec,
        "opus_equivalent_range": f"{opus_low}–{opus_high}",
        "compression_vs_raw": f"{raw_pcm_3sec // json_bytes}×",
        "compression_vs_opus_low": f"{opus_low // json_bytes}×",
    }


def print_report(packet: dict, label: str = "Packet"):
    print(f"\n{'='*55}")
    print(f"  {label}")
    print(f"{'='*55}")
    print(f"  JSON: {json.dumps(packet, ensure_ascii=False)}")
    try:
        validate_packet(packet)
        print("  Validation: [VALID]")
    except PacketValidationError as e:
        print(f"  Validation: [INVALID] -- {e}")
        return

    report = packet_size_report(packet)
    print(f"\n  Size Breakdown (for Demo Slide):")
    print(f"  +----------------------------------------+")
    print(f"  | This packet          : {report['packet_bytes']:>6} bytes      |")
    print(f"  | Opus equivalent      : {report['opus_equivalent_range']:>11} bytes |")
    print(f"  | Raw PCM (3s, 16kHz)  : {report['raw_pcm_3sec_bytes']:>6} bytes      |")
    print(f"  | Savings vs raw PCM   : {report['compression_vs_raw']:>12}        |")
    print(f"  +----------------------------------------+")


# ---------------------------------------------------------------------------
# Built-in example packets
# ---------------------------------------------------------------------------
_EXAMPLES = [
    {
        "label": "Normal utterance (Hindi)",
        "packet": {
            "msg_id": "a1b2c3d4",
            "lang": "hi",
            "text": "madad chahiye station ke paas",
            "urgency": "normal",
            "confidence": 0.91,
            "ts": 1725620000123,
        },
    },
    {
        "label": "Emergency utterance (English)",
        "packet": {
            "msg_id": "e9f8g7h6",
            "lang": "en",
            "text": "Fire near the eastern gate help needed immediately",
            "urgency": "high",
            "confidence": 0.88,
            "ts": 1725620000456,
        },
    },
    {
        "label": "INVALID — missing ts field",
        "packet": {
            "msg_id": "z1z2z3z4",
            "lang": "en",
            "text": "this packet is missing a timestamp",
            "urgency": "normal",
        },
    },
    {
        "label": "INVALID — bad lang code",
        "packet": {
            "msg_id": "bad00001",
            "lang": "xx",
            "text": "unknown language",
            "urgency": "normal",
            "ts": 1725620000789,
        },
    },
]


def main():
    if len(sys.argv) > 1:
        # Validate a JSON string passed on the command line
        raw = " ".join(sys.argv[1:])
        try:
            packet = json.loads(raw)
        except json.JSONDecodeError as e:
            print(f"Invalid JSON: {e}")
            sys.exit(1)
        print_report(packet, label="CLI Input")
    else:
        print("\niTantra — Packet Validator (built-in examples)")
        for ex in _EXAMPLES:
            print_report(ex["packet"], label=ex["label"])
    print()


if __name__ == "__main__":
    main()
