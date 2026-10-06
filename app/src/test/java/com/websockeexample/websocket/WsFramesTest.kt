package com.websockeexample.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class WsFramesTest {
    @Test
    fun acceptKey_matchesRfc6455Vector() {
        assertEquals(
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            WsFrames.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="),
        )
    }

    @Test
    fun encode_maskedHello_matchesRfcFrame() {
        val frame = WsFrames.encode(
            opcode = WsOpcodes.TEXT,
            payload = "Hello".toByteArray(Charsets.UTF_8),
            maskKey = byteArrayOf(0x37, 0xfa.toByte(), 0x21, 0x3d),
        )
        val expected = byteArrayOf(
            0x81.toByte(),
            0x85.toByte(),
            0x37,
            0xfa.toByte(),
            0x21,
            0x3d,
            0x7f,
            0x9f.toByte(),
            0x4d,
            0x51,
            0x58,
        )
        assertTrue(frame.contentEquals(expected))
    }

    @Test
    fun read_unmaskedServerFrame_returnsText() {
        val bytes = byteArrayOf(
            0x81.toByte(),
            0x05,
            'H'.code.toByte(),
            'e'.code.toByte(),
            'l'.code.toByte(),
            'l'.code.toByte(),
            'o'.code.toByte(),
        )
        val frame = WsFrames.read(ByteArrayInputStream(bytes))
        assertTrue(frame.fin)
        assertEquals(WsOpcodes.TEXT, frame.opcode)
        assertEquals("Hello", frame.payload.toString(Charsets.UTF_8))
    }

    @Test
    fun read_maskedFrame_unmasksPayload() {
        val encoded = WsFrames.encode(
            opcode = WsOpcodes.TEXT,
            payload = "Hello".toByteArray(Charsets.UTF_8),
            maskKey = byteArrayOf(0x37, 0xfa.toByte(), 0x21, 0x3d),
        )
        val frame = WsFrames.read(ByteArrayInputStream(encoded))
        assertEquals("Hello", frame.payload.toString(Charsets.UTF_8))
    }

    @Test
    fun roundTrip_usesExtendedLengthAbove125Bytes() {
        val text = "x".repeat(200)
        val encoded = WsFrames.encode(
            opcode = WsOpcodes.TEXT,
            payload = text.toByteArray(Charsets.UTF_8),
            maskKey = byteArrayOf(1, 2, 3, 4),
        )
        assertEquals(126, encoded[1].toInt() and 0x7F)
        val frame = WsFrames.read(ByteArrayInputStream(encoded))
        assertEquals(text, frame.payload.toString(Charsets.UTF_8))
    }

    @Test
    fun assembler_joinsFragmentsAndLetsPingPassThrough() {
        val assembler = WsAssembler()
        val mask = byteArrayOf(9, 8, 7, 6)
        val start = WsFrames.read(
            ByteArrayInputStream(
                WsFrames.encode(WsOpcodes.TEXT, "Hel".toByteArray(), mask, fin = false),
            ),
        )
        assertNull(assembler.push(start))

        val ping = assembler.push(WsFrame(fin = true, opcode = WsOpcodes.PING, payload = byteArrayOf(1)))
        assertEquals(WsOpcodes.PING, ping?.opcode)

        val end = WsFrames.read(
            ByteArrayInputStream(
                WsFrames.encode(WsOpcodes.CONTINUATION, "lo".toByteArray(), mask, fin = true),
            ),
        )
        val message = assembler.push(end)
        assertEquals("Hello", message?.payload?.toString(Charsets.UTF_8))
    }

    @Test
    fun decodeClose_readsCodeAndReason() {
        val payload = WsFrames.closePayload(1000, "bye")
        val (code, reason) = WsFrames.decodeClose(payload)
        assertEquals(1000, code)
        assertEquals("bye", reason)
    }
}
