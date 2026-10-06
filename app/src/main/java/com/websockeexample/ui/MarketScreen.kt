package com.websockeexample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websockeexample.model.Market
import com.websockeexample.model.Quote
import com.websockeexample.ui.theme.Amber
import com.websockeexample.ui.theme.Buy
import com.websockeexample.ui.theme.Ink
import com.websockeexample.ui.theme.Line
import com.websockeexample.ui.theme.LiveBlue
import com.websockeexample.ui.theme.Panel
import com.websockeexample.ui.theme.PanelRaised
import com.websockeexample.ui.theme.Sell
import com.websockeexample.ui.theme.TextMuted
import com.websockeexample.ui.theme.TextPrimary
import com.websockeexample.websocket.ProtocolLine
import com.websockeexample.websocket.SocketPhase
import com.websockeexample.websocket.SocketStats

@Composable
internal fun MarketScreen(
    endpoint: String,
    rows: List<Pair<Market, Quote?>>,
    phase: SocketPhase,
    stats: SocketStats,
    log: List<ProtocolLine>,
    onToggleConnection: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val running = phase !is SocketPhase.Idle
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            Button(
                onClick = onToggleConnection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) PanelRaised else LiveBlue,
                    contentColor = if (running) TextPrimary else Ink,
                ),
            ) {
                Text(if (running) "Отключить" else "Подключить", fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "BINANCE SPOT",
                modifier = Modifier.padding(top = 8.dp),
                color = Amber,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = "Рынок",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = endpoint,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Text(
                text = "Один сокет на miniTicker. Пара открывает второе соединение со сделками.",
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            ConnectionBanner(phase = phase, stats = stats)
            Text(
                text = "Журнал сокета",
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val preview = log.take(4)
                if (preview.isEmpty()) {
                    Text("Ждём handshake…", color = TextMuted, fontSize = 12.sp)
                } else {
                    preview.forEach { ProtocolLineRow(it, maxLines = 2) }
                }
            }
            Text(
                text = "Пары",
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.first.symbol }) { (market, quote) ->
                    MarketRow(market = market, quote = quote, onClick = { onOpen(market.symbol) })
                }
            }
        }
    }
}

@Composable
private fun MarketRow(
    market: Market,
    quote: Quote?,
    onClick: () -> Unit,
) {
    val up = quote?.let { it.last >= it.open }
    val accent = when (up) {
        true -> Buy
        false -> Sell
        null -> Line
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Panel, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(market.base, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${market.name} · ${market.quote}",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
                Text(
                    text = quote?.let { "объём ${formatVolume(it.baseVolume)} ${market.base}" } ?: "ждём тикер",
                    modifier = Modifier.padding(top = 4.dp),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                LivePrice(
                    price = quote?.last,
                    up = up,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = quote?.let { formatPercent(it.changeFraction) } ?: "—",
                    color = when (up) {
                        true -> Buy
                        false -> Sell
                        null -> TextMuted
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
