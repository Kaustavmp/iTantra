package dev.itantra.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import dev.itantra.core.UrgencyDetector
import java.io.ByteArrayOutputStream

private const val TAG = "AudioRecorder"
private const val SAMPLE_RATE = 16000

class AudioRecorder(
    private val onAmplitudeRms: (Float) -> Unit = {}
) {
    private var audioRecord: AudioRecord? = null
    private var isMonitoring = false
    private var isCapturing = false
    private var captureThread: Thread? = null
    private val pcmOutputStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (isMonitoring) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state not initialized")
                return
            }

            audioRecord?.startRecording()
            isMonitoring = true

            captureThread = Thread {
                val buffer = ShortArray(640) // 40ms chunk at 16kHz
                while (isMonitoring) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val rms = UrgencyDetector.computeRms(buffer, read)
                        onAmplitudeRms(rms)

                        synchronized(this) {
                            if (isCapturing) {
                                for (i in 0 until read) {
                                    val sample = buffer[i]
                                    pcmOutputStream.write(sample.toInt() and 0xFF)
                                    pcmOutputStream.write((sample.toInt() shr 8) and 0xFF)
                                }
                            }
                        }
                    }
                }
            }.apply { start() }

            Log.i(TAG, "Audio monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
        }
    }

    fun startCapture() {
        synchronized(this) {
            pcmOutputStream.reset()
            isCapturing = true
        }
        Log.i(TAG, "Audio capture started")
    }

    fun stopCapture(): FloatArray {
        val pcmBytes: ByteArray
        synchronized(this) {
            isCapturing = false
            pcmBytes = pcmOutputStream.toByteArray()
            pcmOutputStream.reset()
        }

        val sampleCount = pcmBytes.size / 2
        val floatSamples = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val shortVal = (high shl 8) or low
            floatSamples[i] = shortVal / 32768.0f
        }
        Log.i(TAG, "Audio capture stopped, captured ${floatSamples.size} samples")
        return floatSamples
    }

    fun stopMonitoring() {
        isMonitoring = false
        isCapturing = false
        captureThread?.interrupt()
        captureThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        Log.i(TAG, "Audio monitoring stopped")
    }
}
