package com.websockeexample.websocket

enum class LogDirection {
    In,
    Out,
    System,
}

data class ProtocolLine(
    val id: Long,
    val atMillis: Long,
    val direction: LogDirection,
    val text: String,
)

sealed interface SocketPhase {
    data object Idle : SocketPhase
    data object Connecting : SocketPhase
    data class Live(val sinceMillis: Long) : SocketPhase
    data class Reconnecting(val attempt: Int, val reason: String) : SocketPhase
}

data class SocketStats(
    val inbound: Long = 0,
    val outbound: Long = 0,
)
