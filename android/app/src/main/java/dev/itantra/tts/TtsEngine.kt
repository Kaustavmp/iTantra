// iTantra — TtsEngine.kt
// TTS wrapper around sherpa-onnx (Piper / VITS-lite) Android bindings.
// Isolates all sherpa-onnx TTS API calls.

package dev.itantra.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import dev.itantra.core.SemanticPacket

private const val TAG = "TtsEngine"
private const val SAMPLE_RATE = 22050 // Piper models default to 22050 Hz output

/**
 * Wraps sherpa-onnx offline TTS (Piper / VITS-lite).
 *
 * Usage:
 *   val engine = TtsEngine(modelDir = "/data/local/tmp/piper-hi-IN")
 *   engine.synthesize(text = "madad chahiye", urgency = UrgencyLevel.NORMAL)
 *
 * Call [setUrgency] before [synthesize] for alert playback behaviour,
 * or pass urgency directly — the engine handles volume/tone automatically.
 *
 * See android/SETUP.md for model download and placement instructions.
 */
class TtsEngine(
    private val modelDir: String,
    private val audioManager: AudioManager? = null,
) {

    // TODO Day 1: Initialise sherpa-onnx OfflineTts here.
    // Reference: https://k2-fsa.github.io/sherpa/onnx/tts/android.html
    //
    // private val tts: OfflineTts by lazy {
    //     val config = OfflineTtsConfig(
    //         model = OfflineTtsModelConfig(
    //             vits = OfflineTtsVitsModelConfig(
    //                 model = "$modelDir/model.onnx",
    //                 tokens = "$modelDir/tokens.txt",
    //                 lexicon = "$modelDir/lexicon.txt",
    //             ),
    //             numThreads = 2,
    //         ),
    //         ruleFsts = "",
    //         maxNumSentences = 1,
    //     )
    //     OfflineTts(config)
    // }

    /**
     * Synthesize [text] and play it immediately.
     *
     * @param text          Text to speak (recognized on sender, already language-tagged).
     * @param urgency       Controls volume and interruptibility.
     * @param speakerIndex  Voice index for multi-speaker models (default 0).
     * @param speed         Playback speed ratio (1.0 = normal).
     */
    fun synthesize(
        text: String,
        urgency: SemanticPacket.UrgencyLevel = SemanticPacket.UrgencyLevel.NORMAL,
        speakerIndex: Int = 0,
        speed: Float = 1.0f,
    ) {
        Log.d(TAG, "Synthesizing: '$text' | urgency=$urgency")

        // TODO Day 1: Replace stub with real sherpa-onnx call:
        // val result = tts.generate(text = text, sid = speakerIndex, speed = speed)
        // playPcm(result.samples, result.sampleRate, urgency)

        // Stub: just log until sherpa is integrated
        Log.d(TAG, "[TTS stub] Would speak: '$text'")
    }

    /**
     * Play raw PCM float samples through AudioTrack.
     * Handles urgency mode: max volume, alert tone prefix, non-interruptible stream.
     */
    private fun playPcm(
        samples: FloatArray,
        sampleRate: Int,
        urgency: SemanticPacket.UrgencyLevel,
    ) {
        val streamType = if (urgency == SemanticPacket.UrgencyLevel.HIGH) {
            // Set system volume to max for HIGH urgency
            audioManager?.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
            AudioAttributes.USAGE_ALARM
        } else {
            AudioAttributes.USAGE_MEDIA
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(streamType)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.stop()
        track.release()
    }

    fun release() {
        // TODO Day 1: tts.release()
        Log.d(TAG, "TtsEngine released")
    }
}
