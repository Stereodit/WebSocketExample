package com.websockeexample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.websockeexample.model.Market
import com.websockeexample.model.Quote
import com.websockeexample.model.TradePrint
import com.websockeexample.ui.theme.Buy
import com.websockeexample.ui.theme.Ink
import com.websockeexample.ui.theme.LiveBlue
import com.websockeexample.ui.theme.Panel
import com.websockeexample.ui.theme.Sell
import com.websockeexample.ui.theme.TextMuted
import com.websockeexample.ui.theme.TextPrimary
import com.websockeexample.websocket.ProtocolLine
import com.websockeexample.websocket.SocketPhase
import com.websockeexample.websocket.SocketStats

@Composable
internal fun TradeScreen(
    market: Market,
    quote: Quote?,
    trades: List<TradePrint>,
    phase: SocketPhase,
    stats: SocketStats,
    log: List<ProtocolLine>,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val up = quote?.let { it.last >= it.open }
    Scaffold(containerColor = Ink) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("Назад", color = LiveBlue)
                }
            }
            Text(
                text = "${market.base} / ${market.quote}",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "${market.name} · отдельный сокет · ${market.tradeStream}",
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            LivePrice(
                price = quote?.last,
                up = up,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = quote?.let { formatPercent(it.changeFraction) + " за 24ч" } ?: "ждём котировку",
                color = when (up) {
                    true -> Buy
                    false -> Sell
                    null -> TextMuted
                },
                fontFamily = FontFamily.Monospace,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stat("Откр.", quote?.open, Modifier.weight(1f))
                Stat("Макс.", quote?.high, Modifier.weight(1f))
                Stat("Мин.", quote?.low, Modifier.weight(1f))
                VolumeStat(quote, market, Modifier.weight(1f))
            }
            ConnectionBanner(
                phase = phase,
                stats = stats,
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp)
                    .background(Panel, RoundedCornerShape(14.dp))
                    .padding(4.dp),
            ) {
                PageTab("Сделки", selected = page == 0, onClick = { page = 0 }, modifier = Modifier.weight(1f))
                PageTab("Журнал", selected = page == 1, onClick = { page = 1 }, modifier = Modifier.weight(1f))
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (page == 0) {
                    if (trades.isEmpty()) {
                        Text("Ждём первые сделки…", color = TextMuted)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(trades, key = { "${it.id}-${it.time}" }) { trade ->
                                TradeRow(trade)
                            }
                        }
                    }
                } else if (log.isEmpty()) {
                    Text("Журнал появится после handshake.", color = TextMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(log, key = { it.id }) { line ->
                            ProtocolLineRow(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(
            text = value?.let(::formatPrice) ?: "—",
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun VolumeStat(quote: Quote?, market: Market, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text("Объём", color = TextMuted, fontSize = 11.sp)
        Text(
            text = quote?.let { formatVolume(it.baseVolume) } ?: "—",
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(market.base, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun PageTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = title,
            color = if (selected) TextPrimary else TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TradeRow(trade: TradePrint) {
    val buy = !trade.buyerIsMaker
    val color = if (buy) Buy else Sell
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClock(trade.time),
            modifier = Modifier.weight(1.1f),
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(
            text = formatPrice(trade.price),
            modifier = Modifier.weight(1f),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Text(
            text = formatQuantity(trade.quantity),
            modifier = Modifier.weight(0.8f),
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(
            text = if (buy) "покупка" else "продажа",
            color = color,
            fontSize = 12.sp,
        )
    }
}
