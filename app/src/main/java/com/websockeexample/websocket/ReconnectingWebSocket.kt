package com.websockeexample.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlin.coroutines.cancellation.CancellationException

/**
 * Клиент WebSocket (RFC 6455) поверх TLS.
 *
 * Сам собирает HTTP-upgrade, маскирует исходящие кадры, отвечает PONG на PING
 * и после обрыва поднимает соединение снова, с увеличивающейся паузой.
 */
class ReconnectingWebSocket(
    private val scope: CoroutineScope,
    private val endpoint: Endpoint,
    private val logTextFrames: Boolean,
) {
    data class Endpoint(
        val host: String,
        val port: Int,
        val path: String,
    ) {
        val displayUrl: String
            get() = if (port == 443) "wss://$host$path" else "wss://$host:$port$path"
    }

    private val _phase = MutableStateFlow<SocketPhase>(SocketPhase.Idle)
    val phase: StateFlow<SocketPhase> = _phase.asStateFlow()

    private val _stats = MutableStateFlow(SocketStats())
    val stats: StateFlow<SocketStats> = _stats.asStateFlow()

    private val _log = MutableStateFlow<List<ProtocolLine>>(emptyList())
    val log: StateFlow<List<ProtocolLine>> = _log.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 256,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val random = SecureRandom()
    private val nextLineId = AtomicLong(1)
    private val nextRequestId = AtomicLong(1)
    private val phaseLock = Any()
    private val writeLock = Any()

    @Volatile
    private var generation = 0

    @Volatile
    private var job: Job? = null

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var closeSent = false

    fun start(streams: List<String>) {
        val gen: Int
        synchronized(phaseLock) {
            if (job?.isActive == true) return
            generation += 1
            gen = generation
            _phase.value = SocketPhase.Connecting
        }
        val launched = scope.launch(Dispatchers.IO) { run(gen, streams) }
        synchronized(phaseLock) {
            if (generation != gen) {
                launched.cancel()
            } else {
                job = launched
            }
        }
    }

    fun stop() {
        val running: Job?
        synchronized(phaseLock) {
            generation += 1
            running = job
            job = null
            _phase.value = SocketPhase.Idle
        }
        append(LogDirection.System, "DISCONNECT")
        running?.cancel()
        closeSocket()
    }

    private suspend fun run(gen: Int, streams: List<String>) {
        var attempt = 0
        var reason = ""
        while (generation == gen) {
            try {
                publish(gen, if (attempt == 0) SocketPhase.Connecting else SocketPhase.Reconnecting(attempt, reason))
                connectOnce(gen, streams)
                if (generation != gen) break
                reason = "сервер закрыл соединение"
                attempt += 1
                publish(gen, SocketPhase.Reconnecting(attempt, reason))
                delay(backoffMillis(attempt))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != gen) break
                reason = error.message?.lineSequence()?.firstOrNull()?.take(160).orEmpty()
                    .ifBlank { "соединение оборвалось" }
                attempt += 1
                publish(gen, SocketPhase.Reconnecting(attempt, reason))
                delay(backoffMillis(attempt))
            }
        }
    }

    private suspend fun connectOnce(gen: Int, streams: List<String>) {
        append(LogDirection.System, "CONNECT ${endpoint.displayUrl}")
        val opened = openSocket(gen) ?: return
        try {
            handshake(opened)
            synchronized(writeLock) {
                if (generation != gen) return
                socket = opened
                output = opened.getOutputStream()
                closeSent = false
            }
            publish(gen, SocketPhase.Live(System.currentTimeMillis()))
            append(LogDirection.System, "101 Switching Protocols")
            sendText(subscribePayload(streams, nextRequestId.getAndIncrement()))
            coroutineScope {
                val ping = launch(Dispatchers.IO) {
                    while (isActive && generation == gen) {
                        delay(PING_INTERVAL_MS)
                        if (generation != gen) break
                        sendFrame(WsOpcodes.PING, ByteArray(0))
                        append(LogDirection.System, "→ PING")
                    }
                }
                try {
                    readLoop(gen, opened.getInputStream())
                } finally {
                    ping.cancel()
                }
            }
        } finally {
            synchronized(writeLock) {
                if (socket === opened) socket = null
                output = null
            }
            opened.closeQuietly()
        }
    }

    private fun openSocket(gen: Int): SSLSocket? {
        val context = SSLContext.getInstance("TLS")
        context.init(null, null, null)
        val created = context.socketFactory.createSocket() as SSLSocket
        created.tcpNoDelay = true
        created.keepAlive = true
        val parameters = created.sslParameters
        parameters.endpointIdentificationAlgorithm = "HTTPS"
        parameters.serverNames = mutableListOf(SNIHostName(endpoint.host))
        created.sslParameters = parameters
        synchronized(writeLock) {
            if (generation != gen) {
                created.closeQuietly()
                return null
            }
            socket = created
        }
        try {
            created.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
            created.startHandshake()
        } catch (error: Exception) {
            created.closeQuietly()
            if (generation != gen) return null
            throw error
        }
        return created
    }

    private fun handshake(opened: Socket) {
        val key = ByteArray(16).also { random.nextBytes(it) }.let(Base64.getEncoder()::encodeToString)
        val hostHeader = if (endpoint.port == 443) endpoint.host else "${endpoint.host}:${endpoint.port}"
        val request = buildString {
            append("GET ${endpoint.path} HTTP/1.1\r\n")
            append("Host: $hostHeader\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $key\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        val stream = opened.getOutputStream()
        stream.write(request.toByteArray(Charsets.US_ASCII))
        stream.flush()
        val header = readHeaders(opened.getInputStream())
        val status = header.lineSequence().firstOrNull().orEmpty()
        if (!status.contains(" 101 ")) {
            throw IOException(status.ifBlank { "сервер отклонил handshake" })
        }
        val accept = header.lineSequence()
            .firstOrNull { it.startsWith("Sec-WebSocket-Accept:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        if (accept != WsFrames.acceptKey(key)) {
            throw IOException("не совпал Sec-WebSocket-Accept")
        }
    }

    private fun readLoop(gen: Int, input: InputStream) {
        val assembler = WsAssembler()
        while (generation == gen) {
            val frame = WsFrames.read(input)
            when (frame.opcode) {
                WsOpcodes.PING -> {
                    append(LogDirection.System, "← PING ${frame.payload.size} B")
                    sendFrame(WsOpcodes.PONG, frame.payload)
                    append(LogDirection.System, "→ PONG")
                }
                WsOpcodes.PONG -> append(LogDirection.System, "← PONG")
                WsOpcodes.CLOSE -> {
                    val (code, reason) = WsFrames.decodeClose(frame.payload)
                    append(LogDirection.System, "← CLOSE $code ${reason.trim()}".trim())
                    sendClose(1000, "bye")
                    throw IOException("сервер закрыл соединение ($code)")
                }
                else -> {
                    val message = assembler.push(frame) ?: continue
                    if (message.opcode != WsOpcodes.TEXT) continue
                    val text = message.payload.toString(Charsets.UTF_8)
                    _stats.update { it.copy(inbound = it.inbound + 1) }
                    if (shouldLogInbound(text)) append(LogDirection.In, text)
                    _messages.tryEmit(text)
                }
            }
        }
    }

    private fun sendText(text: String) {
        append(LogDirection.Out, text)
        sendFrame(WsOpcodes.TEXT, text.toByteArray(Charsets.UTF_8))
    }

    private fun sendClose(code: Int, reason: String) {
        synchronized(writeLock) {
            if (closeSent || output == null) return
            closeSent = true
        }
        append(LogDirection.System, "→ CLOSE $code")
        sendFrame(WsOpcodes.CLOSE, WsFrames.closePayload(code, reason))
    }

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val mask = ByteArray(4).also { random.nextBytes(it) }
        val bytes = WsFrames.encode(opcode, payload, mask)
        synchronized(writeLock) {
            val stream = output ?: throw IOException("сокет не открыт")
            stream.write(bytes)
            stream.flush()
        }
        _stats.update { it.copy(outbound = it.outbound + 1) }
    }

    private fun closeSocket() {
        try {
            sendClose(1000, "bye")
        } catch (_: Exception) {
        }
        val current = synchronized(writeLock) { socket }
        current?.closeQuietly()
    }

    private fun shouldLogInbound(text: String): Boolean {
        if (logTextFrames) return true
        return !text.contains("\"e\":")
    }

    private fun subscribePayload(streams: List<String>, id: Long): String {
        val params = streams.joinToString(",") { "\"$it\"" }
        return """{"method":"SUBSCRIBE","params":[$params],"id":$id}"""
    }

    private fun publish(gen: Int, phase: SocketPhase) {
        synchronized(phaseLock) {
            if (generation == gen) _phase.value = phase
        }
    }

    private fun append(direction: LogDirection, text: String) {
        val line = ProtocolLine(
            id = nextLineId.getAndIncrement(),
            atMillis = System.currentTimeMillis(),
            direction = direction,
            text = text.replace('\n', ' ').take(280),
        )
        _log.update { (listOf(line) + it).take(LOG_LIMIT) }
    }

    private fun backoffMillis(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 5)
        val base = (500L shl shift).coerceAtMost(15_000L)
        return base + random.nextInt(300)
    }

    private fun readHeaders(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val byte = input.read()
            if (byte < 0) throw EOFException("handshake оборвался")
            buffer.write(byte)
            matched = when {
                byte == HEADER_TAIL[matched] -> matched + 1
                byte == HEADER_TAIL[0] -> 1
                else -> 0
            }
            if (buffer.size() > 8_192) throw IOException("заголовок handshake слишком большой")
        }
        return buffer.toString(Charsets.US_ASCII.name())
    }

    private fun Socket.closeQuietly() {
        try {
            close()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val PING_INTERVAL_MS = 20_000L
        const val LOG_LIMIT = 80
        val HEADER_TAIL = intArrayOf('\r'.code, '\n'.code, '\r'.code, '\n'.code)
    }
}
