package com.websockeexample.websocket

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

internal object WsOpcodes {
    const val CONTINUATION = 0x0
    const val TEXT = 0x1
    const val BINARY = 0x2
    const val CLOSE = 0x8
    const val PING = 0x9
    const val PONG = 0xA
}

internal data class WsFrame(
    val fin: Boolean,
    val opcode: Int,
    val payload: ByteArray,
)

internal object WsFrames {
    const val MAX_PAYLOAD = 1_048_576
    private const val ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun acceptKey(secWebSocketKey: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest((secWebSocketKey + ACCEPT_GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun encode(
        opcode: Int,
        payload: ByteArray,
        maskKey: ByteArray,
        fin: Boolean = true,
    ): ByteArray {
        require(maskKey.size == 4) { "mask key must be 4 bytes" }
        val masked = ByteArray(payload.size)
        for (index in payload.indices) {
            masked[index] = (payload[index].toInt() xor maskKey[index and 3].toInt()).toByte()
        }
        val header = ByteArrayOutputStream()
        header.write((if (fin) 0x80 else 0x00) or (opcode and 0x0F))
        when {
            payload.size <= 125 -> header.write(0x80 or payload.size)
            payload.size <= 0xFFFF -> {
                header.write(0x80 or 126)
                header.write((payload.size ushr 8) and 0xFF)
                header.write(payload.size and 0xFF)
            }
            else -> {
                header.write(0x80 or 127)
                var length = payload.size.toLong()
                val extended = ByteArray(8)
                for (index in 7 downTo 0) {
                    extended[index] = (length and 0xFF).toByte()
                    length = length ushr 8
                }
                header.write(extended)
            }
        }
        header.write(maskKey)
        header.write(masked)
        return header.toByteArray()
    }

    fun read(input: InputStream): WsFrame {
        val first = input.read()
        if (first < 0) throw EOFException("сокет закрыт")
        val second = input.read()
        if (second < 0) throw EOFException("сокет закрыт")
        val fin = (first and 0x80) != 0
        val opcode = first and 0x0F
        val masked = (second and 0x80) != 0
        var length = (second and 0x7F).toLong()
        when (length) {
            126L -> {
                val extended = readExact(input, 2)
                length = (((extended[0].toInt() and 0xFF) shl 8) or (extended[1].toInt() and 0xFF)).toLong()
            }
            127L -> {
                val extended = readExact(input, 8)
                length = 0L
                for (byte in extended) {
                    length = (length shl 8) or (byte.toLong() and 0xFF)
                }
            }
        }
        if (length > MAX_PAYLOAD) throw IOException("кадр больше $MAX_PAYLOAD байт")
        val maskKey = if (masked) readExact(input, 4) else null
        val payload = readExact(input, length.toInt())
        if (maskKey != null) {
            for (index in payload.indices) {
                payload[index] = (payload[index].toInt() xor maskKey[index and 3].toInt()).toByte()
            }
        }
        return WsFrame(fin = fin, opcode = opcode, payload = payload)
    }

    fun closePayload(code: Int, reason: String): ByteArray {
        val reasonBytes = reason.encodeToByteArray().let { bytes ->
            if (bytes.size <= 120) bytes else bytes.copyOf(120)
        }
        val payload = ByteArray(2 + reasonBytes.size)
        payload[0] = ((code ushr 8) and 0xFF).toByte()
        payload[1] = (code and 0xFF).toByte()
        reasonBytes.copyInto(payload, destinationOffset = 2)
        return payload
    }

    fun decodeClose(payload: ByteArray): Pair<Int, String> {
        if (payload.size < 2) return 1005 to ""
        val code = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val reason = if (payload.size == 2) {
            ""
        } else {
            payload.copyOfRange(2, payload.size).toString(Charsets.UTF_8)
        }
        return code to reason
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        if (count == 0) return ByteArray(0)
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) throw EOFException("сокет закрыт")
            offset += read
        }
        return buffer
    }
}

internal class WsAssembler {
    private val buffer = ByteArrayOutputStream()
    private var messageOpcode = -1

    fun push(frame: WsFrame): WsFrame? {
        if (frame.opcode >= WsOpcodes.CLOSE) return frame
        when (frame.opcode) {
            WsOpcodes.CONTINUATION -> {
                if (messageOpcode < 0) throw IOException("continuation без начального кадра")
                write(frame.payload)
                if (!frame.fin) return null
                val payload = buffer.toByteArray()
                val opcode = messageOpcode
                buffer.reset()
                messageOpcode = -1
                return WsFrame(fin = true, opcode = opcode, payload = payload)
            }
            WsOpcodes.TEXT, WsOpcodes.BINARY -> {
                if (messageOpcode >= 0) throw IOException("новый кадр внутри фрагментированного сообщения")
                if (frame.fin) return frame
                messageOpcode = frame.opcode
                buffer.reset()
                write(frame.payload)
                return null
            }
            else -> throw IOException("неизвестный opcode ${frame.opcode}")
        }
    }

    private fun write(payload: ByteArray) {
        buffer.write(payload)
        if (buffer.size() > WsFrames.MAX_PAYLOAD) throw IOException("сообщение больше 1 МиБ")
    }
}
