package org.openauto.companion.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Socks5Server(
    private val port: Int = 1080,
    private val bindAddress: String = "0.0.0.0",
    private val username: String,
    private val password: String,
    private val connectivityManager: ConnectivityManager? = null
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val connectionCount = AtomicInteger(0)
    private val failedAuths = ConcurrentHashMap<String, Pair<Int, Long>>()
    private var cellularNetwork: Network? = null
    private var acceptThread: Thread? = null

    val isActive: Boolean get() = running.get()

    fun start() {
        if (running.getAndSet(true)) return

        connectivityManager?.let { cm ->
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cellularNetwork = network
                    Log.i(TAG, "Cellular network available for proxy egress")
                }
                override fun onLost(network: Network) {
                    if (cellularNetwork == network) cellularNetwork = null
                }
            })
        }

        serverSocket = ServerSocket()
        serverSocket!!.bind(InetSocketAddress(bindAddress, port))

        acceptThread = Thread({
            Log.i(TAG, "SOCKS5 server listening on $bindAddress:$port")
            while (running.get()) {
                try {
                    val client = serverSocket!!.accept()
                    if (connectionCount.get() >= MAX_CONNECTIONS) {
                        client.close()
                        continue
                    }
                    connectionCount.incrementAndGet()
                    executor.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (running.get()) Log.e(TAG, "Accept error", e)
                }
            }
        }, "socks5-accept")
        acceptThread!!.start()
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.join(2000)
        executor.shutdownNow()
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = IDLE_TIMEOUT_MS
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val clientIp = client.inetAddress.hostAddress ?: "unknown"

            // Check lockout
            failedAuths[clientIp]?.let { (count, until) ->
                if (count >= MAX_AUTH_FAILURES && System.currentTimeMillis() < until) {
                    client.close()
                    return
                }
            }

            // Greeting: client sends VER NMETHODS METHODS[NMETHODS]
            val ver = input.read()
            if (ver != 0x05) return
            val nmethods = input.read()
            if (nmethods < 1) return
            val methods = ByteArray(nmethods)
            var read = 0
            while (read < nmethods) {
                val n = input.read(methods, read, nmethods - read)
                if (n < 0) return
                read += n
            }

            // We require username/password auth (0x02)
            if (!methods.contains(0x02.toByte())) {
                output.write(byteArrayOf(0x05, 0xFF.toByte())) // no acceptable method
                output.flush()
                return
            }
            output.write(byteArrayOf(0x05, 0x02))
            output.flush()

            // Auth: RFC 1929
            val authBuf = ByteArray(513)
            val authLen = input.read(authBuf)
            if (authLen < 5) return
            val (user, pass) = parseAuthRequest(authBuf.copyOf(authLen))

            if (user != username || pass != password) {
                output.write(byteArrayOf(0x01, 0x01))
                output.flush()
                val entry = failedAuths.getOrDefault(clientIp, Pair(0, 0L))
                failedAuths[clientIp] = Pair(entry.first + 1, System.currentTimeMillis() + LOCKOUT_MS)
                return
            }
            output.write(byteArrayOf(0x01, 0x00))
            output.flush()

            // Request: VER CMD RSV ATYP DST.ADDR DST.PORT
            val reqHeader = ByteArray(4)
            if (input.read(reqHeader) < 4) return
            if (reqHeader[1] != 0x01.toByte()) {
                sendReply(output, 0x07)
                return
            }

            val (destHost, destPort) = parseDestination(input, reqHeader[3])
                ?: run { sendReply(output, 0x01); return }

            if (isBlockedAddress(destHost)) {
                sendReply(output, 0x02)
                return
            }

            val remote = Socket()
            cellularNetwork?.bindSocket(remote)

            try {
                remote.connect(InetSocketAddress(destHost, destPort), CONNECT_TIMEOUT_MS)
            } catch (e: Exception) {
                sendReply(output, 0x05)
                return
            }

            sendReply(output, 0x00)
            relay(client, remote)
        } catch (e: Exception) {
            Log.d(TAG, "Client handler error", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
            connectionCount.decrementAndGet()
        }
    }

    private fun parseDestination(input: InputStream, atyp: Byte): Pair<String, Int>? {
        return when (atyp) {
            0x01.toByte() -> {
                val addr = ByteArray(4)
                if (input.read(addr) < 4) return null
                val port = readPort(input) ?: return null
                InetAddress.getByAddress(addr).hostAddress!! to port
            }
            0x03.toByte() -> {
                val len = input.read()
                if (len < 1) return null
                val domain = ByteArray(len)
                if (input.read(domain) < len) return null
                val port = readPort(input) ?: return null
                String(domain) to port
            }
            0x04.toByte() -> {
                val addr = ByteArray(16)
                if (input.read(addr) < 16) return null
                val port = readPort(input) ?: return null
                InetAddress.getByAddress(addr).hostAddress!! to port
            }
            else -> null
        }
    }

    private fun readPort(input: InputStream): Int? {
        val hi = input.read()
        val lo = input.read()
        if (hi < 0 || lo < 0) return null
        return (hi shl 8) or lo
    }

    private fun sendReply(output: OutputStream, status: Byte) {
        output.write(byteArrayOf(
            0x05, status, 0x00, 0x01,
            0, 0, 0, 0,
            0, 0
        ))
        output.flush()
    }

    private fun relay(client: Socket, remote: Socket) {
        val t1 = Thread({
            try {
                client.getInputStream().copyTo(remote.getOutputStream())
            } catch (_: Exception) {}
            try { remote.shutdownOutput() } catch (_: Exception) {}
        }, "relay-c2r")

        val t2 = Thread({
            try {
                remote.getInputStream().copyTo(client.getOutputStream())
            } catch (_: Exception) {}
            try { client.shutdownOutput() } catch (_: Exception) {}
        }, "relay-r2c")

        t1.start(); t2.start()
        t1.join(); t2.join()
        try { remote.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "Socks5Server"
        private const val MAX_CONNECTIONS = 20
        private const val IDLE_TIMEOUT_MS = 120_000
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val MAX_AUTH_FAILURES = 3
        private const val LOCKOUT_MS = 30_000L

        fun isBlockedAddress(host: String): Boolean {
            return try {
                val addr = InetAddress.getByName(host)
                addr.isLoopbackAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isMulticastAddress
            } catch (_: Exception) { false }
        }

        fun parseAuthRequest(bytes: ByteArray): Pair<String, String> {
            val ulen = bytes[1].toInt() and 0xFF
            val user = String(bytes, 2, ulen)
            val plen = bytes[2 + ulen].toInt() and 0xFF
            val pass = String(bytes, 3 + ulen, plen)
            return user to pass
        }
    }
}
