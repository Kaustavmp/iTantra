// iTantra — Transport.kt
// Pluggable transport abstraction.
// The AI pipeline (STT/TTS) never talks to sockets directly.
// It hands a SemanticPacket to whichever Transport is active.
// This is what lets you answer "switch to Bluetooth" live without touching AI code.

package dev.itantra.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "Transport"

// ---------------------------------------------------------------------------
// Interface — the only contract the rest of the app sees
// ---------------------------------------------------------------------------

interface Transport {
    /** Send a packet (may block briefly; call from a coroutine). */
    suspend fun send(packet: SemanticPacket)

    /** Register a callback invoked on every received packet. */
    fun onReceive(callback: (SemanticPacket) -> Unit)

    /** Release all resources (sockets, threads, etc.). */
    fun close()
}

// ---------------------------------------------------------------------------
// LocalSocketTransport — TCP over shared local Wi-Fi (Day 2 implementation)
// ---------------------------------------------------------------------------

/**
 * Raw TCP client/server over a shared local Wi-Fi network.
 *
 * Phone A (sender) instantiates as CLIENT: [LocalSocketTransport(host, port, isServer=false)]
 * Phone B (receiver) instantiates as SERVER: [LocalSocketTransport(port=TCP_PORT, isServer=true)]
 *
 * Packets are sent as newline-terminated JSON strings.
 */
class LocalSocketTransport(
    private val host: String = "192.168.1.100", // Phone A's IP — set in UI
    private val port: Int = TCP_PORT,
    private val isServer: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : Transport {

    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var writer: PrintWriter? = null
    private var receiveCallback: ((SemanticPacket) -> Unit)? = null

    init {
        if (isServer) startServer() else connectToServer()
    }

    private fun startServer() = scope.launch {
        try {
            serverSocket = ServerSocket(port)
            Log.i(TAG, "LocalSocketTransport: server listening on port $port")
            val client = serverSocket!!.accept()
            socket = client
            Log.i(TAG, "LocalSocketTransport: client connected from ${client.inetAddress}")
            listenForPackets(client)
        } catch (e: Exception) {
            Log.e(TAG, "Server error", e)
        }
    }

    private fun connectToServer() = scope.launch {
        try {
            Log.i(TAG, "LocalSocketTransport: connecting to $host:$port")
            val s = Socket(host, port)
            socket = s
            writer = PrintWriter(s.getOutputStream(), true)
            Log.i(TAG, "LocalSocketTransport: connected")
            listenForPackets(s)
        } catch (e: Exception) {
            Log.e(TAG, "Client connect error", e)
        }
    }

    private fun listenForPackets(s: Socket) = scope.launch {
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val packet = SemanticPacket.fromJson(line!!)
                if (packet != null) {
                    receiveCallback?.invoke(packet)
                } else {
                    Log.w(TAG, "Received unparseable packet: $line")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receive loop error", e)
        }
    }

    override suspend fun send(packet: SemanticPacket) = withContext<Unit>(Dispatchers.IO) {
        try {
            val w = writer ?: run {
                // Server side: set writer lazily after connection is accepted
                val s = socket
                if (s != null && !s.isClosed) {
                    writer = PrintWriter(s.getOutputStream(), true)
                    writer
                } else null
            }
            w?.println(packet.toJson()) ?: Log.w(TAG, "Cannot send: no connection yet")
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
        }
    }

    override fun onReceive(callback: (SemanticPacket) -> Unit) {
        receiveCallback = callback
    }

    override fun close() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
    }

    companion object {
        const val TCP_PORT = 54321
    }
}

// ---------------------------------------------------------------------------
// WifiDirectTransport — Day 3 stretch goal placeholder
// ---------------------------------------------------------------------------

/**
 * Wi-Fi Direct (P2P) transport.
 * Uses Android's WifiP2pManager API.
 * Fully pluggable: the rest of the app never changes when you swap this in.
 *
 * TODO: Implement in Day 3 if LocalSocketTransport is solid.
 */
class WifiDirectTransport : Transport {
    override suspend fun send(packet: SemanticPacket) {
        TODO("Day 3: implement using WifiP2pManager + group owner socket")
    }

    override fun onReceive(callback: (SemanticPacket) -> Unit) {
        TODO("Day 3: register receive callback in WifiP2p data channel")
    }

    override fun close() { /* clean up P2P group */ }
}

// ---------------------------------------------------------------------------
// BluetoothTransport — stretch goal placeholder
// ---------------------------------------------------------------------------

/**
 * Bluetooth Classic RFCOMM transport.
 * Pluggable: swap in via DI without touching STT/TTS code.
 *
 * TODO: Implement after WifiDirectTransport if time allows.
 */
class BluetoothTransport : Transport {
    override suspend fun send(packet: SemanticPacket) {
        TODO("Stretch: implement using BluetoothAdapter + RFCOMM socket")
    }

    override fun onReceive(callback: (SemanticPacket) -> Unit) {
        TODO("Stretch: register receive callback on Bluetooth input stream")
    }

    override fun close() { /* close RFCOMM socket */ }
}
