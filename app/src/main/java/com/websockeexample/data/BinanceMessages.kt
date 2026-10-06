package com.websockeexample.data

import com.websockeexample.model.Quote
import com.websockeexample.model.TradePrint

internal sealed interface BinanceEvent {
    data class Ticker(val quote: Quote) : BinanceEvent
    data class Trade(val print: TradePrint) : BinanceEvent
    data class Ack(val id: Long, val ok: Boolean, val error: String?) : BinanceEvent
    data object Ignored : BinanceEvent
}

internal object BinanceMessages {
    fun parse(text: String): BinanceEvent {
        val root = try {
            parseJson(text) as? JVal.Obj ?: return BinanceEvent.Ignored
        } catch (_: IllegalArgumentException) {
            return BinanceEvent.Ignored
        }
        val fields = root.fields
        return when (fields.text("e")) {
            "24hrMiniTicker" -> ticker(fields)
            "trade" -> trade(fields)
            else -> if (fields.containsKey("id")) ack(fields) else BinanceEvent.Ignored
        }
    }

    private fun ticker(fields: Map<String, JVal>): BinanceEvent {
        val symbol = fields.text("s") ?: return BinanceEvent.Ignored
        val last = fields.decimal("c") ?: return BinanceEvent.Ignored
        val open = fields.decimal("o") ?: return BinanceEvent.Ignored
        val high = fields.decimal("h") ?: return BinanceEvent.Ignored
        val low = fields.decimal("l") ?: return BinanceEvent.Ignored
        val volume = fields.decimal("v") ?: return BinanceEvent.Ignored
        val time = fields.long("E") ?: return BinanceEvent.Ignored
        return BinanceEvent.Ticker(
            Quote(
                symbol = symbol,
                last = last,
                open = open,
                high = high,
                low = low,
                baseVolume = volume,
                eventTime = time,
            )
        )
    }

    private fun trade(fields: Map<String, JVal>): BinanceEvent {
        val symbol = fields.text("s") ?: return BinanceEvent.Ignored
        val id = fields.long("t") ?: return BinanceEvent.Ignored
        val price = fields.decimal("p") ?: return BinanceEvent.Ignored
        val quantity = fields.decimal("q") ?: return BinanceEvent.Ignored
        val time = fields.long("T") ?: fields.long("E") ?: return BinanceEvent.Ignored
        val buyerIsMaker = fields.bool("m") ?: return BinanceEvent.Ignored
        return BinanceEvent.Trade(
            TradePrint(
                id = id,
                symbol = symbol,
                price = price,
                quantity = quantity,
                time = time,
                buyerIsMaker = buyerIsMaker,
            )
        )
    }

    private fun ack(fields: Map<String, JVal>): BinanceEvent {
        val id = fields.long("id") ?: return BinanceEvent.Ignored
        val error = fields["error"]
        val failed = error != null && error !is JVal.Null
        val message = (error as? JVal.Obj)?.fields?.text("msg")
        return BinanceEvent.Ack(id = id, ok = !failed, error = message)
    }
}
