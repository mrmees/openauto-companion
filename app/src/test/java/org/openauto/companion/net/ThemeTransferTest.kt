package org.openauto.companion.net

import org.junit.Assert.*
import org.junit.Test

class ThemeTransferTest {
    @Test
    fun chunkData_singleChunk() {
        val data = ByteArray(100) { it.toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        assertEquals(1, chunks.size)
        val decoded = java.util.Base64.getDecoder().decode(chunks[0])
        assertArrayEquals(data, decoded)
    }

    @Test
    fun chunkData_multipleChunks() {
        val data = ByteArray(150000) { (it % 256).toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        assertEquals(3, chunks.size)
    }

    @Test
    fun chunkData_exactBoundary() {
        val data = ByteArray(65536) { it.toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        assertEquals(1, chunks.size)
    }

    @Test
    fun chunkData_emptyInput() {
        val chunks = ThemeTransfer.chunkBytes(ByteArray(0), chunkSize = 65536)
        assertEquals(0, chunks.size)
    }

    @Test
    fun chunkData_roundTripsAllData() {
        val data = ByteArray(200000) { (it % 256).toByte() }
        val chunks = ThemeTransfer.chunkBytes(data, chunkSize = 65536)
        val reassembled = chunks.map { java.util.Base64.getDecoder().decode(it) }
            .reduce { acc, bytes -> acc + bytes }
        assertArrayEquals(data, reassembled)
    }
}
