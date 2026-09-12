package dev.itantra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.itantra.audio.AudioRecorder
import dev.itantra.core.LocalSocketTransport
import dev.itantra.core.SemanticPacket
import dev.itantra.core.Transport
import dev.itantra.core.UrgencyDetector
import dev.itantra.stt.SttEngine
import dev.itantra.tts.TtsEngine
import dev.itantra.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val TAG = "MainActivity"
private const val PERMISSION_REQUEST_MIC = 101

class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private lateinit var sttEngine: SttEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var urgencyDetector: UrgencyDetector
    private var audioRecorder: AudioRecorder? = null
    private var activeTransport: Transport? = null

    private val cpuSampler = CpuUsageSampler()

    // Compose Reactive State
    private var hasMicPermissionState = mutableStateOf(false)
    private var appModeState = mutableStateOf(AppMode.LOOPBACK)
    private var peerIpState = mutableStateOf("192.168.1.100")
    private var amplitudeRmsState = mutableStateOf(0f)
    private var isRecordingState = mutableStateOf(false)
    private var lastTranscribedTextState = mutableStateOf<String?>(null)
    private var lastTranscribedUrgencyState = mutableStateOf<SemanticPacket.UrgencyLevel?>(null)
    private var lastReceivedPacketState = mutableStateOf<SemanticPacket?>(null)

    private var sttLatencyState = mutableStateOf<Long?>(null)
    private var ttsLatencyState = mutableStateOf<Long?>(null)
    private var packetBytesState = mutableStateOf<Int?>(null)
    private var cpuPercentState = mutableStateOf("0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sttEngine = SttEngine(modelDir = "", forcedLanguage = "en")
        ttsEngine = TtsEngine(context = this, modelDir = "")
        urgencyDetector = UrgencyDetector()

        checkMicPermission()
        setupAudioRecorder()
        setupTransport(AppMode.LOOPBACK)

        mainHandler.post(cpuRefreshRunnable)

        setContent {
            ITantraTheme {
                var selectedTab by remember { mutableStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceCard,
                            contentColor = TextLight
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("Talk", fontSize = 12.sp) },
                                icon = { Text("🎙️", fontSize = 18.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DarkBg,
                                    selectedTextColor = MintPrimary,
                                    indicatorColor = EmeraldAccent
                                )
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Sovereignty", fontSize = 12.sp) },
                                icon = { Text("🛡️", fontSize = 18.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DarkBg,
                                    selectedTextColor = MintPrimary,
                                    indicatorColor = EmeraldAccent
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (selectedTab == 0) {
                            TalkScreen(
                                hasMicPermission = hasMicPermissionState.value,
                                onRequestMicPermission = { requestMicPermission() },
                                appMode = appModeState.value,
                                onAppModeChange = { newMode ->
                                    appModeState.value = newMode
                                    setupTransport(newMode)
                                },
                                peerIp = peerIpState.value,
                                onPeerIpChange = { newIp ->
                                    peerIpState.value = newIp
                                    if (appModeState.value == AppMode.SENDER) {
                                        setupTransport(AppMode.SENDER)
                                    }
                                },
                                amplitudeRms = amplitudeRmsState.value,
                                isRecording = isRecordingState.value,
                                onStartRecording = { startVoiceRecording() },
                                onStopRecording = { stopVoiceRecordingAndProcess() },
                                lastTranscribedText = lastTranscribedTextState.value,
                                lastTranscribedUrgency = lastTranscribedUrgencyState.value,
                                lastReceivedPacket = lastReceivedPacketState.value,
                                onRunLoopbackTest = { executeLoopbackCheck("madad chahiye station ke paas", "hi") },
                                onSendEmergencyTest = { executeLoopbackCheck("FIRE emergency evacuate now", "en") }
                            )
                        } else {
                            MetricsScreen(
                                sttLatencyMs = sttLatencyState.value,
                                ttsLatencyMs = ttsLatencyState.value,
                                packetSizeBytes = packetBytesState.value,
                                cpuPercent = cpuPercentState.value,
                                peerIp = peerIpState.value,
                                appMode = appModeState.value
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkMicPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasMicPermissionState.value = granted
        if (granted) {
            audioRecorder?.startMonitoring()
        }
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_MIC
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_MIC) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            hasMicPermissionState.value = granted
            if (granted) {
                setupAudioRecorder()
                audioRecorder?.startMonitoring()
            }
        }
    }

    private fun setupAudioRecorder() {
        if (audioRecorder == null && hasMicPermissionState.value) {
            audioRecorder = AudioRecorder(
                onAmplitudeRms = { rms ->
                    mainHandler.post {
                        amplitudeRmsState.value = rms
                    }
                }
            )
            audioRecorder?.startMonitoring()
        }
    }

    private fun setupTransport(mode: AppMode) {
        activeTransport?.close()
        activeTransport = null

        when (mode) {
            AppMode.LOOPBACK -> {
                Log.i(TAG, "Transport set to LOOPBACK mode")
            }
            AppMode.SENDER -> {
                activeTransport = LocalSocketTransport(
                    host = peerIpState.value,
                    port = LocalSocketTransport.TCP_PORT,
                    isServer = false
                )
            }
            AppMode.RECEIVER -> {
                activeTransport = LocalSocketTransport(
                    port = LocalSocketTransport.TCP_PORT,
                    isServer = true
                )
            }
        }

        activeTransport?.onReceive { packet ->
            handleIncomingPacket(packet)
        }
    }

    private fun startVoiceRecording() {
        if (!hasMicPermissionState.value) {
            requestMicPermission()
            return
        }
        setupAudioRecorder()
        isRecordingState.value = true
        audioRecorder?.startCapture()
    }

    private fun stopVoiceRecordingAndProcess() {
        if (!isRecordingState.value) return
        isRecordingState.value = false
        val samples = audioRecorder?.stopCapture() ?: FloatArray(0)

        Thread {
            val sttResult = sttEngine.transcribe(samples)
            if (sttResult != null) {
                val urgencyResult = urgencyDetector.classify(sttResult.text, amplitudeRmsState.value)
                val packet = SemanticPacket.create(
                    lang = sttResult.lang,
                    text = sttResult.text,
                    urgency = urgencyResult.level,
                    confidence = sttResult.confidence
                )

                mainHandler.post {
                    lastTranscribedTextState.value = sttResult.text
                    lastTranscribedUrgencyState.value = urgencyResult.level
                    sttLatencyState.value = sttResult.latencyMs
                    packetBytesState.value = packet.sizeBytes()
                }

                // Transmit packet or perform loopback synthesis
                if (appModeState.value == AppMode.LOOPBACK || activeTransport == null) {
                    handleIncomingPacket(packet)
                } else {
                    mainScope.launch {
                        activeTransport?.send(packet)
                    }
                }
            }
        }.start()
    }

    private fun executeLoopbackCheck(sampleText: String, lang: String) {
        Thread {
            val startStt = System.currentTimeMillis()
            val result = sttEngine.transcribe(FloatArray(3200))
            val text = sampleText
            val urgencyResult = urgencyDetector.classify(text)
            val packet = SemanticPacket.create(
                lang = lang,
                text = text,
                urgency = urgencyResult.level,
                confidence = 0.94f
            )

            mainHandler.post {
                lastTranscribedTextState.value = text
                lastTranscribedUrgencyState.value = urgencyResult.level
                sttLatencyState.value = System.currentTimeMillis() - startStt
                packetBytesState.value = packet.sizeBytes()

                handleIncomingPacket(packet)
            }
        }.start()
    }

    private fun handleIncomingPacket(packet: SemanticPacket) {
        mainHandler.post {
            lastReceivedPacketState.value = packet
        }

        Thread {
            val ttsLatency = ttsEngine.synthesize(text = packet.text, urgency = packet.urgency)
            mainHandler.post {
                ttsLatencyState.value = ttsLatency
            }
        }.start()
    }

    private val cpuRefreshRunnable = object : Runnable {
        override fun run() {
            cpuSampler.sample()
            cpuPercentState.value = cpuSampler.currentPercent()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        audioRecorder?.stopMonitoring()
        activeTransport?.close()
        sttEngine.release()
        ttsEngine.release()
        super.onDestroy()
    }
}

private class CpuUsageSampler {
    private var previousProcess = 0L
    private var previousTotal = 0L
    private var percent = 0

    fun sample() {
        val process = readProcessTicks() ?: return
        val total = readTotalTicks() ?: return
        if (previousTotal > 0L && total > previousTotal) {
            val processDelta = process - previousProcess
            val totalDelta = total - previousTotal
            percent = ((processDelta.toDouble() / totalDelta) * Runtime.getRuntime().availableProcessors() * 100).toInt().coerceIn(0, 100)
        }
        previousProcess = process
        previousTotal = total
    }

    fun currentPercent(): String = String.format(Locale.US, "%d", percent)

    private fun readProcessTicks(): Long? = runCatching {
        File("/proc/self/stat").readText().trim().split(" ").let { fields ->
            fields[13].toLong() + fields[14].toLong()
        }
    }.getOrNull()

    private fun readTotalTicks(): Long? = runCatching {
        File("/proc/stat").useLines { lines ->
            lines.first { it.startsWith("cpu ") }.trim().split(Regex("\\s+")).drop(1).sumOf { it.toLong() }
        }
    }.getOrNull()
}
