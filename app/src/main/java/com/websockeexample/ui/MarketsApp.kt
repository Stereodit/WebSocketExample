package com.websockeexample.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.websockeexample.websocket.SocketPhase

@Composable
fun MarketsApp(viewModel: MarketsViewModel) {
    val selected by viewModel.selectedSymbol.collectAsStateWithLifecycle()
    val symbol = selected
    if (symbol == null) {
        MarketRoute(viewModel)
    } else {
        BackHandler(onBack = viewModel::closeDetail)
        TradeRoute(viewModel, symbol)
    }
}

@Composable
private fun MarketRoute(viewModel: MarketsViewModel) {
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val phase by viewModel.marketPhase.collectAsStateWithLifecycle()
    val stats by viewModel.marketStats.collectAsStateWithLifecycle()
    val log by viewModel.marketLog.collectAsStateWithLifecycle()
    MarketScreen(
        endpoint = viewModel.endpointLabel,
        rows = viewModel.catalog.map { market -> market to quotes[market.symbol] },
        phase = phase,
        stats = stats,
        log = log,
        onToggleConnection = {
            if (phase is SocketPhase.Idle) viewModel.connectMarket() else viewModel.disconnectMarket()
        },
        onOpen = viewModel::open,
    )
}

@Composable
private fun TradeRoute(viewModel: MarketsViewModel, symbol: String) {
    val market = viewModel.catalog.first { it.symbol == symbol }
    val quotes by viewModel.quotes.collectAsStateWithLifecycle()
    val trades by viewModel.trades.collectAsStateWithLifecycle()
    val phase by viewModel.tradePhase.collectAsStateWithLifecycle()
    val stats by viewModel.tradeStats.collectAsStateWithLifecycle()
    val log by viewModel.tradeLog.collectAsStateWithLifecycle()
    TradeScreen(
        market = market,
        quote = quotes[symbol],
        trades = trades,
        phase = phase,
        stats = stats,
        log = log,
        onBack = viewModel::closeDetail,
    )
}
