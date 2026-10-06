package com.websockeexample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websockeexample.data.BinanceEvent
import com.websockeexample.data.BinanceMessages
import com.websockeexample.model.DemoMarkets
import com.websockeexample.model.Market
import com.websockeexample.model.Quote
import com.websockeexample.model.TradePrint
import com.websockeexample.websocket.ProtocolLine
import com.websockeexample.websocket.ReconnectingWebSocket
import com.websockeexample.websocket.SocketPhase
import com.websockeexample.websocket.SocketStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MarketsViewModel : ViewModel() {
    val endpointLabel: String = ENDPOINT.displayUrl
    val catalog: List<Market> = DemoMarkets

    private val marketSocket = ReconnectingWebSocket(
        scope = viewModelScope,
        endpoint = ENDPOINT,
        logTextFrames = false,
    )
    private val tradeSocket = ReconnectingWebSocket(
        scope = viewModelScope,
        endpoint = ENDPOINT,
        logTextFrames = true,
    )

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    val marketPhase: StateFlow<SocketPhase> = marketSocket.phase
    val marketStats: StateFlow<SocketStats> = marketSocket.stats
    val marketLog: StateFlow<List<ProtocolLine>> = marketSocket.log

    private val _selectedSymbol = MutableStateFlow<String?>(null)
    val selectedSymbol: StateFlow<String?> = _selectedSymbol.asStateFlow()

    private val _trades = MutableStateFlow<List<TradePrint>>(emptyList())
    val trades: StateFlow<List<TradePrint>> = _trades.asStateFlow()

    val tradePhase: StateFlow<SocketPhase> = tradeSocket.phase
    val tradeStats: StateFlow<SocketStats> = tradeSocket.stats
    val tradeLog: StateFlow<List<ProtocolLine>> = tradeSocket.log

    init {
        viewModelScope.launch {
            marketSocket.messages.collect { text ->
                val event = BinanceMessages.parse(text)
                if (event is BinanceEvent.Ticker) {
                    _quotes.update { it + (event.quote.symbol to event.quote) }
                }
            }
        }
        viewModelScope.launch {
            tradeSocket.messages.collect { text ->
                val event = BinanceMessages.parse(text)
                if (event is BinanceEvent.Trade && event.print.symbol == _selectedSymbol.value) {
                    _trades.update { current ->
                        if (current.any { it.id == event.print.id }) current
                        else (listOf(event.print) + current).take(TRADE_LIMIT)
                    }
                    _quotes.update { current ->
                        val previous = current[event.print.symbol] ?: return@update current
                        current + (event.print.symbol to previous.copy(
                            last = event.print.price,
                            eventTime = event.print.time,
                        ))
                    }
                }
            }
        }
        connectMarket()
    }

    fun connectMarket() {
        marketSocket.start(catalog.map(Market::tickerStream))
    }

    fun disconnectMarket() {
        marketSocket.stop()
    }

    fun open(symbol: String) {
        _trades.value = emptyList()
        _selectedSymbol.value = symbol
        val market = catalog.first { it.symbol == symbol }
        tradeSocket.start(listOf(market.tradeStream))
    }

    fun closeDetail() {
        _selectedSymbol.value = null
        tradeSocket.stop()
        _trades.value = emptyList()
    }

    override fun onCleared() {
        marketSocket.stop()
        tradeSocket.stop()
    }

    private companion object {
        const val TRADE_LIMIT = 50
        val ENDPOINT = ReconnectingWebSocket.Endpoint(
            host = "data-stream.binance.vision",
            port = 443,
            path = "/ws",
        )
    }
}
