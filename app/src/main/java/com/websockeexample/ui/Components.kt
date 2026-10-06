package com.websockeexample.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websockeexample.ui.theme.Amber
import com.websockeexample.ui.theme.Buy
import com.websockeexample.ui.theme.LiveBlue
import com.websockeexample.ui.theme.Sell
import com.websockeexample.ui.theme.TextMuted
import com.websockeexample.ui.theme.TextPrimary
import com.websockeexample.websocket.LogDirection
import com.websockeexample.websocket.ProtocolLine
import com.websockeexample.websocket.SocketPhase
import com.websockeexample.websocket.SocketStats
import kotlinx.coroutines.delay

@Composable
internal fun ConnectionBanner(
    phase: SocketPhase,
    stats: SocketStats,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val liveSince = (phase as? SocketPhase.Live)?.sinceMillis
    LaunchedEffect(liveSince) {
        if (liveSince == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val (label, color) = when (phase) {
        SocketPhase.Idle -> "Отключено" to TextMuted
        SocketPhase.Connecting -> "Подключение" to Amber
        is SocketPhase.Live -> "В эфире" to Buy
        is SocketPhase.Reconnecting -> "Повтор ${phase.attempt}" to Amber
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            val uptime = if (liveSince != null) " · ${formatUptime(liveSince, now)}" else ""
            Text(
                text = "↑ ${formatCount(stats.outbound)}   ↓ ${formatCount(stats.inbound)}$uptime",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
        if (phase is SocketPhase.Reconnecting && phase.reason.isNotBlank()) {
            Text(
                text = phase.reason,
                color = Amber,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun LivePrice(
    price: Double?,
    up: Boolean?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(price) {
        if (price == null) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, tween(420))
    }
    val accent = when (up) {
        true -> Buy
        false -> Sell
        null -> Amber
    }
    Text(
        text = price?.let(::formatPrice) ?: "—",
        modifier = modifier,
        style = style,
        fontFamily = FontFamily.Monospace,
        color = lerp(TextPrimary, accent, pulse.value.coerceIn(0f, 1f)),
    )
}

@Composable
internal fun ProtocolLineRow(line: ProtocolLine, maxLines: Int = Int.MAX_VALUE) {
    val color = when (line.direction) {
        LogDirection.In -> LiveBlue
        LogDirection.Out -> Amber
        LogDirection.System -> TextMuted
    }
    val mark = when (line.direction) {
        LogDirection.In -> "IN"
        LogDirection.Out -> "OUT"
        LogDirection.System -> "SYS"
    }
    Text(
        text = "${formatClock(line.atMillis)}  $mark  ${line.text}",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
