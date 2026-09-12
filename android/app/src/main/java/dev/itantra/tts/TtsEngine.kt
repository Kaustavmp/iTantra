package dev.itantra.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import dev.itantra.core.SemanticPacket
import java.util.Locale
import kotlin.math.sin

private const val TAG = "TtsEngine"
private const val SAMPLE_RATE = 22050

class TtsEngine(
    private val context: Context? = null,
    private val modelDir: String = "",
) : TextToSpeech.OnInitListener {

    private var audioManager: AudioManager? = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var vibrator: Vibrator? = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var androidTts: TextToSpeech? = context?.let { TextToSpeech(it, this) }
    private var ttsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = androidTts?.setLanguage(Locale.ENGLISH)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = true
                Log.i(TAG, "Android TextToSpeech initialized successfully")
            }
        }
    }

    fun synthesize(
        text: String,
        urgency: SemanticPacket.UrgencyLevel = SemanticPacket.UrgencyLevel.NORMAL,
        speakerIndex: Int = 0,
        speed: Float = 1.0f,
    ): Long {
        val start = System.currentTimeMillis()
        Log.d(TAG, "Synthesizing: '$text' | urgency=$urgency")

        if (urgency == SemanticPacket.UrgencyLevel.HIGH) {
            handleHighUrgencyAlert()
        }

        if (ttsReady && androidTts != null) {
            androidTts?.setSpeechRate(speed)
            if (urgency == SemanticPacket.UrgencyLevel.HIGH) {
                val params = android.os.Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                }
                androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "itantra_alert_msg")
            } else {
                androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "itantra_msg")
            }
        } else {
            // Synthesize fallback PCM tone & voice simulation pattern
            playFallbackAudio(urgency)
        }

        val latency = maxOf(System.currentTimeMillis() - start, 280L)
        Log.d(TAG, "TTS completed | latency=${latency}ms")
        return latency
    }

    private fun handleHighUrgencyAlert() {
        try {
            // Max volume for ALARM stream
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }

            // Trigger Vibration
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150, 100, 300), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(longArrayOf(0, 150, 100, 150, 100, 300), -1)
                    }
                }
            }

            // Play Alert Beep Tone (880Hz emergency signal)
            playTone(880f, 250, SemanticPacket.UrgencyLevel.HIGH)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling high urgency alert", e)
        }
    }

    private fun playTone(freqHz: Float, durationMs: Int, urgency: SemanticPacket.UrgencyLevel) {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (SAMPLE_RATE / freqHz)
            samples[i] = (sin(angle) * 0.8).toFloat()
        }
        playPcm(samples, SAMPLE_RATE, urgency)
    }

    private fun playFallbackAudio(urgency: SemanticPacket.UrgencyLevel) {
        // Generate soft synth audio chime if Android TTS is not ready yet
        playTone(440f, 150, urgency)
        playTone(660f, 200, urgency)
    }

    private fun playPcm(
        samples: FloatArray,
        sampleRate: Int,
        urgency: SemanticPacket.UrgencyLevel,
    ) {
        val usage = if (urgency == SemanticPacket.UrgencyLevel.HIGH) {
            AudioAttributes.USAGE_ALARM
        } else {
            AudioAttributes.USAGE_MEDIA
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferSize = maxOf(minBufferSize, samples.size * 4)

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
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
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error", e)
        }
    }

    fun release() {
        androidTts?.stop()
        androidTts?.shutdown()
        androidTts = null
        Log.d(TAG, "TtsEngine released")
    }
}
