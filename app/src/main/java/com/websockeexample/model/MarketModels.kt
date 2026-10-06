package com.websockeexample.model

data class Market(
    val symbol: String,
    val base: String,
    val quote: String,
    val name: String,
) {
    val tickerStream: String get() = "${symbol.lowercase()}@miniTicker"
    val tradeStream: String get() = "${symbol.lowercase()}@trade"
}

val DemoMarkets = listOf(
    Market("BTCUSDT", "BTC", "USDT", "Bitcoin"),
    Market("ETHUSDT", "ETH", "USDT", "Ethereum"),
    Market("SOLUSDT", "SOL", "USDT", "Solana"),
    Market("BNBUSDT", "BNB", "USDT", "BNB"),
    Market("XRPUSDT", "XRP", "USDT", "XRP"),
    Market("DOGEUSDT", "DOGE", "USDT", "Dogecoin"),
)

data class Quote(
    val symbol: String,
    val last: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val baseVolume: Double,
    val eventTime: Long,
) {
    val changeFraction: Double
        get() = if (open == 0.0) 0.0 else (last - open) / open
}

data class TradePrint(
    val id: Long,
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val time: Long,
    val buyerIsMaker: Boolean,
)
