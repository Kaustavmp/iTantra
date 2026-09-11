// iTantra — SttEngine.kt
// STT wrapper around sherpa-onnx Android bindings.
// Isolates all sherpa-onnx API calls so the rest of the app never imports sherpa directly.

package dev.itantra.stt

import android.util.Log
import dev.itantra.core.UrgencyDetector

private const val TAG = "SttEngine"

/**
 * Result of a single STT transcription.
 *
 * @param text          Recognized text.
 * @param lang          Detected or forced language code (e.g. "hi", "en").
 * @param confidence    Model confidence (0.0–1.0). May be 0 if model doesn't provide it.
 * @param latencyMs     Time from call to result in milliseconds.
 */
data class SttResult(
    val text: String,
    val lang: String,
    val confidence: Float,
    val latencyMs: Long,
)

/**
 * Wraps sherpa-onnx offline recognizer (Whisper-tiny INT8 or Vosk-small).
 *
 * Usage:
 *   val engine = SttEngine(modelDir = "/data/local/tmp/whisper-tiny-int8")
 *   engine.transcribe(pcmBuffer, sampleRate = 16000) { result ->
 *       // result.text is ready
 *   }
 *
 * Threading: transcribe() is synchronous and CPU-bound. Always call it from
 * a background coroutine (Dispatchers.Default or Dispatchers.IO).
 *
 * See android/SETUP.md for model download and placement instructions.
 */
class SttEngine(
    private val modelDir: String,
    private val forcedLanguage: String? = null, // null = auto-detect
) {

    // TODO Day 1: Initialise the sherpa-onnx OfflineRecognizer here.
    // Reference: https://k2-fsa.github.io/sherpa/onnx/android/index.html
    //
    // Example (pseudo-code, replace with real sherpa-onnx API):
    //
    // private val recognizer: OfflineRecognizer by lazy {
    //     val config = OfflineRecognizerConfig(
    //         featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
    //         modelConfig = OfflineModelConfig(
    //             whisper = OfflineWhisperModelConfig(
    //                 encoder = "$modelDir/encoder.int8.onnx",
    //                 decoder = "$modelDir/decoder.int8.onnx",
    //             ),
    //             tokens = "$modelDir/tokens.txt",
    //             numThreads = 2,
    //         ),
    //     )
    //     OfflineRecognizer(config)
    // }

    /**
     * Transcribe a PCM audio buffer.
     *
     * @param samples   Float32 PCM samples in range [-1.0, 1.0].
     * @param sampleRate  Must be 16000 for Whisper/Vosk models.
     * @return [SttResult] on success, null on failure.
     */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): SttResult? {
        val start = System.currentTimeMillis()

        // TODO Day 1: Replace stub with real sherpa-onnx call:
        // val stream = recognizer.createStream()
        // stream.acceptWaveform(samples, sampleRate)
        // recognizer.decode(stream)
        // val result = recognizer.getResult(stream)
        // val text = result.text
        // stream.release()

        // Stub for compilation before sherpa is integrated:
        val text = "[STT not yet connected — integrate sherpa-onnx here]"
        val lang = forcedLanguage ?: "en"
        val latency = System.currentTimeMillis() - start

        Log.d(TAG, "STT result: '$text' | lang=$lang | latency=${latency}ms")
        return SttResult(text = text, lang = lang, confidence = 0f, latencyMs = latency)
    }

    /**
     * Compute normalized RMS from a short-Int PCM buffer and detect urgency
     * before the full transcription is complete.
     * Used for early-warning amplitude signal.
     */
    fun computeAmplitude(buffer: ShortArray): Float =
        UrgencyDetector.computeRms(buffer)

    fun release() {
        // TODO Day 1: recognizer.release()
        Log.d(TAG, "SttEngine released")
    }
}
