package com.websockeexample.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinanceMessagesTest {
    @Test
    fun parse_miniTicker_readsPriceAndChangeBase() {
        val text = """
            {"e":"24hrMiniTicker","E":1791296828016,"s":"BTCUSDT","c":"86128.01000000","o":"86342.54000000","h":"86488.00000000","l":"84972.01000000","v":"13426.62951000","q":"1151284684.75282880"}
        """.trim()
        val event = BinanceMessages.parse(text) as BinanceEvent.Ticker
        assertEquals("BTCUSDT", event.quote.symbol)
        assertEquals(86128.01, event.quote.last, 0.001)
        assertEquals(86342.54, event.quote.open, 0.001)
        assertTrue(event.quote.changeFraction < 0)
    }

    @Test
    fun parse_trade_marksBuyerMakerAsSellSide() {
        val text = """
            {"e":"trade","E":10,"s":"ETHUSDT","t":99,"p":"1800.5","q":"0.25","T":11,"m":true}
        """.trim()
        val event = BinanceMessages.parse(text) as BinanceEvent.Trade
        assertEquals(99L, event.print.id)
        assertEquals(1800.5, event.print.price, 0.001)
        assertEquals(0.25, event.print.quantity, 0.0001)
        assertTrue(event.print.buyerIsMaker)
        assertEquals(11L, event.print.time)
    }

    @Test
    fun parse_subscribeAck_isNotATicker() {
        val event = BinanceMessages.parse("""{"result":null,"id":1}""")
        val ack = event as BinanceEvent.Ack
        assertEquals(1L, ack.id)
        assertTrue(ack.ok)
    }

    @Test
    fun parse_brokenJson_isIgnored() {
        assertEquals(BinanceEvent.Ignored, BinanceMessages.parse("{"))
    }

    @Test
    fun parseJson_readsNestedErrorMessage() {
        val root = parseJson("""{"id":7,"error":{"code":2,"msg":"Invalid"}}""") as JVal.Obj
        val error = root.fields.getValue("error") as JVal.Obj
        assertEquals("Invalid", error.fields.text("msg"))
        assertEquals(7L, root.fields.long("id"))
    }
}
