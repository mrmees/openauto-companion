package org.openauto.companion.net

import android.net.Network
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class PiConnection(
    private val host: String = "10.0.0.1",
    private val port: Int = 9876,
    private val sharedSecret: String,
    private val wifiNetwork: Network? = null
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    var lastFailureReason: String? = null
        private set
    var sessionKey: ByteArray? = null
        private set
    var isAuthenticated = false
        private set

    fun connect(): Boolean {
        disconnect()
        return try {
            val s = createSocket()
            socket = s
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = 10000
            writer = PrintWriter(s.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(s.getInputStream()))

            // Read challenge
            val challengeLine = reader?.readLine()
                ?: return fail("No challenge received (socket closed by peer)")
            val challenge = parseJson(challengeLine, "challenge")
                ?: return fail("Challenge payload was not valid JSON")
            val challengeType = challenge.optString("type")
            if (challengeType != "challenge") {
                return fail("Unexpected first message type='$challengeType'")
            }

            val nonce = challenge.optString("nonce")
            if (nonce.isBlank()) return fail("Challenge nonce missing")

            // Send hello
            val hello = Protocol.buildHello(sharedSecret, nonce,
                listOf("time", "gps", "battery", "socks5"))
            writer?.println(hello.toString())

            // Read ack
            val ackLine = reader?.readLine()
                ?: return fail("No hello_ack received (socket closed by peer)")
            val ack = parseJson(ackLine, "hello_ack")
                ?: return fail("hello_ack payload was not valid JSON")
            val ackType = ack.optString("type")
            if (ackType.isNotEmpty() && ackType != "hello_ack") {
                return fail("Unexpected ack type='$ackType'")
            }
            if (!ack.optBoolean("accepted", false)) {
                return fail("Pi rejected hello (accepted=false)")
            }

            // Store session key
            val keyHex = ack.optString("session_key")
            sessionKey = decodeHexKey(keyHex)
                ?: return fail("Invalid session_key format in ack")
            isAuthenticated = true
            lastFailureReason = null
            Log.i(TAG, "Connected and authenticated successfully (host=$host, port=$port)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed with exception", e)
            fail("Exception during connect: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun sendStatus(status: JSONObject) {
        writer?.println(status.toString())
    }

    fun disconnect() {
        isAuthenticated = false
        sessionKey = null
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null; writer = null; reader = null
    }

    fun isConnected(): Boolean = socket?.isConnected == true && !socket!!.isClosed

    private fun fail(reason: String): Boolean {
        lastFailureReason = reason
        Log.w(TAG, "Connect failed: $reason")
        disconnect()
        return false
    }

    private fun parseJson(raw: String, label: String): JSONObject? {
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid $label JSON: $raw", e)
            null
        }
    }

    private fun createSocket(): Socket {
        if (wifiNetwork == null) return Socket()

        return try {
            // Preferred path: force socket over the matched Wi-Fi network.
            Log.i(TAG, "Creating socket bound to WiFi network")
            wifiNetwork.socketFactory.createSocket() as Socket
        } catch (e: Exception) {
            // Some devices/ROMs deny bind-to-network from app UID (EPERM).
            // Fallback keeps AA companion usable while preserving SOCKS5 egress
            // behavior via ConnectivityManager in Socks5Server.
            if (shouldFallbackToUnboundSocket(e)) {
                Log.w(TAG, "WiFi-bound socket denied; falling back to unbound socket", e)
                Socket()
            } else {
                throw e
            }
        }
    }

    companion object {
        private const val TAG = "PiConnection"
    }
}

internal fun decodeHexKey(hex: String): ByteArray? {
    if (hex.isBlank() || (hex.length % 2 != 0)) return null
    return try {
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    } catch (_: Exception) {
        null
    }
}

internal fun shouldFallbackToUnboundSocket(error: Throwable?): Boolean {
    var current = error
    while (current != null) {
        val msg = current.message.orEmpty()
        if ("EPERM" in msg || "Operation not permitted" in msg) return true
        current = current.cause
    }
    return false
}
