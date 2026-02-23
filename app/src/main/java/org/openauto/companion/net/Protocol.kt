package org.openauto.companion.net

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Protocol {
    fun computeHmac(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    fun buildHello(secret: String, nonce: String, capabilities: List<String>): JSONObject {
        val token = computeHmac(secret.toByteArray(), nonce.toByteArray())
        return JSONObject().apply {
            put("type", "hello")
            put("version", 1)
            put("token", token)
            put("capabilities", JSONArray(capabilities))
        }
    }

    fun buildStatus(
        seq: Int,
        sessionKey: ByteArray,
        timeMs: Long,
        timezone: String,
        gpsLat: Double, gpsLon: Double,
        gpsAccuracy: Double, gpsSpeed: Double,
        gpsBearing: Double, gpsAgeMs: Int,
        batteryLevel: Int, batteryCharging: Boolean,
        socks5Port: Int, socks5Active: Boolean
    ): JSONObject {
        val payload = JSONObject().apply {
            put("type", "status")
            put("seq", seq)
            put("sent_mono_ms", SystemClock.elapsedRealtime())
            put("time_ms", timeMs)
            put("timezone", timezone)
            put("gps", JSONObject().apply {
                put("lat", gpsLat)
                put("lon", gpsLon)
                put("accuracy", gpsAccuracy)
                put("speed", gpsSpeed)
                put("bearing", gpsBearing)
                put("age_ms", gpsAgeMs)
            })
            put("battery", JSONObject().apply {
                put("level", batteryLevel)
                put("charging", batteryCharging)
            })
            put("socks5", JSONObject().apply {
                put("port", socks5Port)
                put("active", socks5Active)
            })
        }

        // Compute MAC over payload
        val payloadStr = payload.toString()
        val mac = computeHmac(sessionKey, payloadStr.toByteArray())
        payload.put("mac", mac)

        return payload
    }

    fun verifyMac(msg: JSONObject, sessionKey: ByteArray): Boolean {
        val mac = msg.optString("mac", "")
        if (mac.isEmpty()) return false
        val copy = JSONObject(msg.toString())
        copy.remove("mac")
        val expected = computeHmac(sessionKey, copy.toString().toByteArray())
        return mac == expected
    }
}
