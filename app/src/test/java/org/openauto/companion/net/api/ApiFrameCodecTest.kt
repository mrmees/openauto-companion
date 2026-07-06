package org.openauto.companion.net.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import prodigy.api.v1.Api
import prodigy.api.v1.Common

class ApiFrameCodecTest {
    @Test
    fun serializeAndParse_roundTripsOneApiMessage() {
        val message = Api.ApiMessage.newBuilder()
            .setRequestId(42)
            .setPing(Common.Ping.getDefaultInstance())
            .build()

        val bytes = ApiFrameCodec.serialize(message)
        val parsed = ApiFrameCodec.parse(bytes)

        assertEquals(42L, parsed.requestId)
        assertEquals(Api.ApiMessage.PayloadCase.PING, parsed.payloadCase)
    }

    @Test
    fun encodeTcpFrame_prefixesPayloadLengthBigEndian() {
        val message = Api.ApiMessage.newBuilder()
            .setRequestId(7)
            .setPong(Common.Pong.getDefaultInstance())
            .build()
        val payload = message.toByteArray()

        val frame = ApiFrameCodec.encodeTcpFrame(message)

        assertArrayEquals(
            byteArrayOf(
                ((payload.size ushr 24) and 0xff).toByte(),
                ((payload.size ushr 16) and 0xff).toByte(),
                ((payload.size ushr 8) and 0xff).toByte(),
                (payload.size and 0xff).toByte()
            ),
            frame.copyOfRange(0, 4)
        )
        assertArrayEquals(payload, frame.copyOfRange(4, frame.size))
        assertEquals(message, ApiFrameCodec.decodeTcpFrame(frame))
    }

    @Test
    fun parse_rejectsPayloadsAboveFrameCapBeforeParsing() {
        expectIllegalArgument {
            ApiFrameCodec.parse(ByteArray(ApiFrameCodec.MAX_FRAME_SIZE_BYTES + 1))
        }
    }

    @Test
    fun decodeTcpFrame_rejectsAdvertisedLengthAboveFrameCap() {
        val tooLarge = ApiFrameCodec.MAX_FRAME_SIZE_BYTES + 1
        val frame = byteArrayOf(
            ((tooLarge ushr 24) and 0xff).toByte(),
            ((tooLarge ushr 16) and 0xff).toByte(),
            ((tooLarge ushr 8) and 0xff).toByte(),
            (tooLarge and 0xff).toByte()
        )

        expectIllegalArgument {
            ApiFrameCodec.decodeTcpFrame(frame)
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
