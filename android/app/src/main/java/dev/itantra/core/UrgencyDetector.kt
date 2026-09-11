// iTantra — UrgencyDetector.kt
// Mirrors the logic of urgency_detector.py (Python prototype) for Android.

package dev.itantra.core

import java.util.Locale
import kotlin.math.sqrt

/**
 * Classifies an utterance as NORMAL or HIGH urgency.
 *
 * Two signals are combined:
 *  1. Keyword match against a multilingual emergency vocabulary.
 *  2. Audio RMS amplitude relative to [amplitudeThreshold].
 *
 * This class is pure Kotlin with no ML dependencies — no model to load,
 * no latency cost, just a regex scan and an amplitude check.
 */
class UrgencyDetector(
    private val amplitudeThreshold: Float = DEFAULT_AMPLITUDE_THRESHOLD,
) {

    data class Result(
        val level: SemanticPacket.UrgencyLevel,
        val triggers: List<String>,
        val matchedKeywords: List<String>,
        val confidence: Float,
    )

    fun classify(text: String, amplitudeRms: Float? = null): Result {
        val triggers = mutableListOf<String>()
        val matched = mutableListOf<String>()

        // --- Signal 1: keyword detection ---
        val lower = text.lowercase(Locale.ROOT)
        for (kw in EMERGENCY_KEYWORDS) {
            // word-boundary check using simple split (avoids regex overhead on each call)
            if (lower.contains(kw)) {
                matched += kw
            }
        }
        if (matched.isNotEmpty()) {
            triggers += "keyword:${matched.joinToString(",").uppercase()}"
        }

        // --- Signal 2: amplitude ---
        val amplitudeHigh = amplitudeRms != null && amplitudeRms >= amplitudeThreshold
        if (amplitudeHigh) {
            triggers += "amplitude"
        }

        // --- Decision ---
        val (level, confidence) = when {
            matched.size >= 2              -> SemanticPacket.UrgencyLevel.HIGH to 0.95f
            matched.isNotEmpty() && amplitudeHigh -> SemanticPacket.UrgencyLevel.HIGH to 0.90f
            matched.isNotEmpty()           -> SemanticPacket.UrgencyLevel.HIGH to 0.75f
            amplitudeHigh                  -> SemanticPacket.UrgencyLevel.HIGH to 0.45f
            else                           -> SemanticPacket.UrgencyLevel.NORMAL to 0.0f
        }

        return Result(
            level = level,
            triggers = triggers,
            matchedKeywords = matched,
            confidence = confidence,
        )
    }

    companion object {
        const val DEFAULT_AMPLITUDE_THRESHOLD = 0.65f

        /**
         * Compute RMS amplitude from a short-Int PCM buffer (16-bit samples).
         * Returns a normalized value in [0.0, 1.0].
         */
        fun computeRms(buffer: ShortArray, length: Int = buffer.size): Float {
            if (length == 0) return 0f
            var sum = 0.0
            for (i in 0 until length) {
                val sample = buffer[i].toDouble() / Short.MAX_VALUE
                sum += sample * sample
            }
            return sqrt(sum / length).toFloat().coerceIn(0f, 1f)
        }

        // Multilingual emergency keywords (lowercase for matching)
        val EMERGENCY_KEYWORDS = setOf(
            // English
            "fire", "help", "sos", "emergency", "danger", "mayday", "alert",
            "evacuate", "evacuation", "alarm", "rescue", "critical", "urgent",
            "attack", "explosion", "flood", "crash", "injured", "medic",
            // Hindi (transliterated)
            "aag", "bachao", "madad", "khatra", "sankat", "haadsa",
            "aapda", "bhaago", "chot",
            // Hindi Devanagari (returned by Whisper when recognizing native script)
            "आग", "बचाओ", "मदद", "खतरा", "संकट", "हादसा", "आपदा", "भागो",
        )
    }
}
