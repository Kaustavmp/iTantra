package dev.itantra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itantra.core.SemanticPacket

enum class AppMode { LOOPBACK, SENDER, RECEIVER }

@Composable
fun TalkScreen(
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
    peerIp: String,
    onPeerIpChange: (String) -> Unit,
    amplitudeRms: Float,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    lastTranscribedText: String?,
    lastTranscribedUrgency: SemanticPacket.UrgencyLevel?,
    lastReceivedPacket: SemanticPacket?,
    onRunLoopbackTest: () -> Unit,
    onSendEmergencyTest: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "iTantra",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextLight
                )
                Text(
                    text = "Multilingual Semantic Relay",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "100% OFFLINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldAccent
                )
            }
        }

        // Permission Banner if missing
        if (!hasMicPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Microphone permission required",
                        color = MintPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "iTantra requires audio recording permission to process live STT locally on device.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = MintPrimary, contentColor = DarkBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant microphone access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Mode Selector (Loopback | Sender | Receiver)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Operating Mode", color = TextMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppMode.values().forEach { mode ->
                        val selected = appMode == mode
                        Button(
                            onClick = { onAppModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MintPrimary else Color.Transparent,
                                contentColor = if (selected) DarkBg else TextMuted
                            ),
                            border = if (!selected) androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder) else null,
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    AppMode.LOOPBACK -> "Loopback"
                                    AppMode.SENDER -> "Sender"
                                    AppMode.RECEIVER -> "Receiver"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (appMode == AppMode.SENDER) {
                    OutlinedTextField(
                        value = peerIp,
                        onValueChange = onPeerIpChange,
                        label = { Text("Receiver Phone IP Address", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MintPrimary,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Live Audio Amplitude Level Meter
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Live mic amplitude", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = "${(amplitudeRms * 100).toInt()}%",
                        color = if (amplitudeRms > 0.65f) AlertRed else EmeraldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF101B20))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(amplitudeRms.coerceIn(0.02f, 1.0f))
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (amplitudeRms > 0.65f) AlertRed else EmeraldAccent)
                    )
                }
            }
        }

        // Push-to-Talk Record Button
        Button(
            onClick = {
                if (isRecording) onStopRecording() else onStartRecording()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) AlertRed else MintPrimary,
                contentColor = if (isRecording) TextLight else DarkBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = if (isRecording) "Stop Speaking (Processing STT...)" else "Push to Speak",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // High Urgency Emergency Alert Banner
        AnimatedVisibility(visible = lastReceivedPacket?.urgency == SemanticPacket.UrgencyLevel.HIGH || lastTranscribedUrgency == SemanticPacket.UrgencyLevel.HIGH) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AlertRed)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("⚠️", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "ALERT MODE ACTIVE — HIGH URGENCY DISTRESS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Max alarm volume & alert chime triggered on receiver device",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Last Recognized Text Card (Local STT)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Last recognized (this phone)", color = TextMuted, fontSize = 12.sp)
                Text(
                    text = lastTranscribedText ?: "--",
                    color = TextLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (lastTranscribedUrgency != null) {
                    Text(
                        text = "Urgency: ${lastTranscribedUrgency.name}",
                        color = if (lastTranscribedUrgency == SemanticPacket.UrgencyLevel.HIGH) AlertRed else EmeraldAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Last Received Text Card (Peer Transport)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Last received (from peer socket)", color = TextMuted, fontSize = 12.sp)
                Text(
                    text = lastReceivedPacket?.text ?: "--",
                    color = TextLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (lastReceivedPacket != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Lang: ${lastReceivedPacket.lang.uppercase()}",
                            color = MintPrimary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Packet: ${lastReceivedPacket.sizeBytes()} B",
                            color = EmeraldAccent,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Urgency: ${lastReceivedPacket.urgency.name}",
                            color = if (lastReceivedPacket.urgency == SemanticPacket.UrgencyLevel.HIGH) AlertRed else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Shortcut Quick Test Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRunLoopbackTest,
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MintPrimary)
            ) {
                Text("Run Loopback Test", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onSendEmergencyTest,
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
            ) {
                Text("Send SOS Fire Test", fontSize = 11.sp)
            }
        }
    }
}
