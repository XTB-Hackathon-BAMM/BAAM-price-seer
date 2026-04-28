package pl.bamm.priceseer.infrastructure.kafka.consumer

import pl.bamm.priceseer.fixtures.marketPrice
import pl.bamm.priceseer.fixtures.marketPriceBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtobufDecoderTest {

    @Test
    fun `given valid protobuf bytes when decode then returns correct MarketPrice`() {
        val bytes = marketPriceBytes(
            symbol = "BTC/USD",
            open = 50_000.0,
            close = 51_000.0,
            timestamp = "2026-04-27T12:05:00Z",
        )

        val result = ProtobufDecoder.decode(bytes)

        assertEquals("BTC/USD", result.symbol)
        assertEquals("2026-04-27T12:05:00Z", result.timestamp)
        assertEquals("1min", result.interval)
        assertEquals(50_000.0, result.open)
        assertEquals(51_000.0, result.close)
        assertEquals(51_000.0, result.high)
        assertEquals(50_000.0, result.low)
    }

    @Test
    fun `given all 8 instruments when decoded then symbol is preserved`() {
        val instruments = listOf("XTB", "CDR", "EUR/USD", "GBP/JPY", "BTC/USD", "ETH/USD", "XAU/USD", "USD/JPY")

        instruments.forEach { symbol ->
            val result = ProtobufDecoder.decode(marketPriceBytes(symbol = symbol))
            assertEquals(symbol, result.symbol)
        }
    }

    @Test
    fun `given bytes with missing symbol when decode then throws IllegalArgumentException`() {
        // Empty bytes — no fields encoded
        assertFailsWith<IllegalArgumentException> {
            ProtobufDecoder.decode(byteArrayOf())
        }
    }

    @Test
    fun `given completely malformed bytes when decode then throws`() {
        assertFailsWith<Exception> {
            ProtobufDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        }
    }

    @Test
    fun `given round-trip encode-decode when compare then fields match`() {
        val original = marketPrice("ETH/USD", open = 3_000.0, close = 3_100.0)
        val bytes = marketPriceBytes(
            symbol = original.symbol,
            open = original.open,
            close = original.close,
            timestamp = original.timestamp,
        )

        val decoded = ProtobufDecoder.decode(bytes)

        assertEquals(original, decoded)
    }
}
