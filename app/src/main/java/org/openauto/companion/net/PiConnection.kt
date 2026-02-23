package org.openauto.companion.net

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
    private val sharedSecret: String
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    var sessionKey: ByteArray? = null
        private set
    var isAuthenticated = false
        private set

    fun connect(): Boolean {
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = 10000
            socket = s
            writer = PrintWriter(s.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(s.getInputStream()))

            // Read challenge
            val challengeLine = reader?.readLine() ?: return false
            val challenge = JSONObject(challengeLine)
            if (challenge.getString("type") != "challenge") return false

            val nonce = challenge.getString("nonce")

            // Send hello
            val hello = Protocol.buildHello(sharedSecret, nonce,
                listOf("time", "gps", "battery", "socks5"))
            writer?.println(hello.toString())

            // Read ack
            val ackLine = reader?.readLine() ?: return false
            val ack = JSONObject(ackLine)
            if (!ack.optBoolean("accepted", false)) return false

            // Store session key
            val keyHex = ack.getString("session_key")
            sessionKey = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            isAuthenticated = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            disconnect()
            false
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

    companion object {
        private const val TAG = "PiConnection"
    }
}
