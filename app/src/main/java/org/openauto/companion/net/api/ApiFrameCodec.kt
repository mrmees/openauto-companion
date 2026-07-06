package org.openauto.companion.net.api

import java.nio.ByteBuffer
import prodigy.api.v1.Api

object ApiFrameCodec {
    const val MAX_FRAME_SIZE_BYTES = 256 * 1024

    fun serialize(message: Api.ApiMessage): ByteArray {
        val bytes = message.toByteArray()
        require(bytes.size <= MAX_FRAME_SIZE_BYTES) { "Frame exceeds 256 KiB limit" }
        return bytes
    }

    fun parse(bytes: ByteArray): Api.ApiMessage {
        require(bytes.size <= MAX_FRAME_SIZE_BYTES) { "Frame exceeds 256 KiB limit" }
        return Api.ApiMessage.parseFrom(bytes)
    }

    fun encodeTcpFrame(message: Api.ApiMessage): ByteArray {
        val payload = serialize(message)
        return ByteBuffer.allocate(Int.SIZE_BYTES + payload.size)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    fun decodeTcpFrame(frame: ByteArray): Api.ApiMessage {
        require(frame.size >= Int.SIZE_BYTES) { "TCP frame is missing length prefix" }
        val length = ByteBuffer.wrap(frame, 0, Int.SIZE_BYTES).int
        require(length >= 0) { "TCP frame length is negative" }
        require(length <= MAX_FRAME_SIZE_BYTES) { "Frame exceeds 256 KiB limit" }
        require(frame.size - Int.SIZE_BYTES == length) { "TCP frame length does not match payload" }
        return parse(frame.copyOfRange(Int.SIZE_BYTES, frame.size))
    }
}
