# Android Project Setup Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK API 26+ (minSdk)
- JDK 17
- An Android phone with USB debugging enabled

---

## 1. Open the Project

1. Open Android Studio
2. `File → Open` → select the `android/` directory (this folder)
3. Wait for Gradle sync to complete
4. Resolve any missing SDK prompts via `Tools → SDK Manager`

---

## 2. Download AI Models

You need to download model files and place them in the `app/src/main/assets/` directory before building.

### STT: Whisper-tiny (INT8) via sherpa-onnx

```bash
# Download pre-packaged model
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.int8.tar.bz2
tar xf sherpa-onnx-whisper-tiny.int8.tar.bz2
```

Copy the extracted folder to:
```
android/app/src/main/assets/whisper-tiny-int8/
    ├── encoder.int8.onnx
    ├── decoder.int8.onnx
    └── tokens.txt
```

### STT Fallback: Vosk-small (Hindi)

```bash
wget https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip
unzip vosk-model-small-hi-0.22.zip
```

Copy to `android/app/src/main/assets/vosk-hi/`

### STT Fallback: Vosk-small (English)

```bash
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
```

Copy to `android/app/src/main/assets/vosk-en/`

### TTS: Piper (Hindi)

```bash
# sherpa-onnx pre-packaged Piper voices
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi-IN-cmu-arctic-xlow.tar.bz2
tar xf vits-piper-hi-IN-cmu-arctic-xlow.tar.bz2
```

Copy to `android/app/src/main/assets/piper-hi/`

### TTS: Piper (English)

```bash
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-low.tar.bz2
tar xf vits-piper-en_US-amy-low.tar.bz2
```

Copy to `android/app/src/main/assets/piper-en/`

---

## 3. Add sherpa-onnx Dependency When Integrating Models

In `app/build.gradle.kts`, add:

```kotlin
dependencies {
    // sherpa-onnx — offline STT + TTS runtime for Android
    implementation("com.github.k2-fsa:sherpa-onnx-android:1.10.20@aar")
    // org.json — packet serialization (already in Android SDK, no dep needed)
    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

Also add to `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        // ...existing repos...
        maven("https://jitpack.io")
    }
}
```

---

## 4. Permissions (AndroidManifest.xml)

The following permissions must be declared:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.INTERNET"/>              <!-- TCP socket -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>    <!-- Wi-Fi Direct -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.BLUETOOTH"/>            <!-- BT stretch -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"
    android:minSdkVersion="31"/>
```

---

## 5. Build & Run

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or use `Run` (▶) in Android Studio with a connected device.

---

### Live Metrics Overlay

The first screen includes a device metrics panel for the demo loop. It shows:

- STT and TTS model status
- Last STT/TTS inference latency
- Semantic packet size in bytes
- Process CPU usage sampled from `/proc`
- Offline status

Use **Run STT check** and **Run TTS check** to exercise the current engine hooks. The
same timings are written to Logcat with the `SttEngine` and `TtsEngine` tags:

```bash
adb logcat -s SttEngine TtsEngine
```

The current screen labels the engines as stubs until the sherpa-onnx model
initializers in `SttEngine.kt` and `TtsEngine.kt` are enabled. The dependency is
intentionally omitted from the current scaffold because version `1.10.20` is not
available from the configured public repositories; add the actual AAR/repository
provided by the sherpa-onnx release when enabling those initializers.

## 6. Verify the Core Loop (Day 1 Milestone)

After installing:
1. Open iTantra on the phone
2. Grant microphone permission when prompted
3. Tap **Speak** and say a Hindi or English phrase
4. The recognized text should appear on screen
5. Tap **Speak Back** — you should hear the TTS output through the speaker

If either step fails, check logcat:
```bash
adb logcat -s SttEngine TtsEngine Transport
```

---

## Model Size Reference

| Asset | Size |
|---|---|
| whisper-tiny-int8 | ~75 MB |
| vosk-hi | ~52 MB |
| vosk-en | ~39 MB |
| piper-hi | ~28 MB |
| piper-en | ~23 MB |
| **Total** | **~217 MB** ✅ (under 300MB target) |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `UnsatisfiedLinkError: libsherpa-onnx` | Check ABI filters in build.gradle (`arm64-v8a` for modern phones) |
| Mic not recording | Confirm `RECORD_AUDIO` permission granted at runtime |
| Socket connection refused | Both phones must be on the same Wi-Fi network; check IP address in TransportConfig |
| TTS no audio | Check `AudioTrack` min buffer size — some devices need a larger buffer |
| OOM on model load | Reduce `numThreads` in SttEngine/TtsEngine to 1 |
