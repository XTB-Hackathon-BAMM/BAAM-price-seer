package pl.bamm.priceseer.infrastructure.kafka.consumer

import pl.bamm.priceseer.fixtures.marketPrice
import pl.bamm.priceseer.fixtures.marketPriceBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtobufDecoderTest {

    /**
     * Test purpose - Verify that {@link ProtobufDecoder#decode} correctly deserializes valid
     * Protobuf bytes into a {@link MarketPrice} with all fields populated.
     *
     * <p>Test data - Protobuf-encoded bytes for symbol {@code "BTC/USD"} with {@code open=50000.0},
     * {@code close=51000.0}, and timestamp {@code "2026-04-27T12:05:00Z"}.
     *
     * <p>Test expected result - Decoded {@link MarketPrice} has matching symbol, timestamp,
     * interval, open, close, high, and low values.
     *
     * <p>Test type - Positive.
     */
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

    /**
     * Test purpose - Verify that {@link ProtobufDecoder#decode} preserves the symbol field
     * correctly for all 8 supported instruments.
     *
     * <p>Test data - Protobuf-encoded bytes for each of the 8 instruments: {@code "XTB"},
     * {@code "CDR"}, {@code "EUR/USD"}, {@code "GBP/JPY"}, {@code "BTC/USD"}, {@code "ETH/USD"},
     * {@code "XAU/USD"}, {@code "USD/JPY"}.
     *
     * <p>Test expected result - Each decoded {@link MarketPrice} has its symbol matching
     * the original instrument.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given all 8 instruments when decoded then symbol is preserved`() {
        val instruments = listOf("XTB", "CDR", "EUR/USD", "GBP/JPY", "BTC/USD", "ETH/USD", "XAU/USD", "USD/JPY")

        instruments.forEach { symbol ->
            val result = ProtobufDecoder.decode(marketPriceBytes(symbol = symbol))
            assertEquals(symbol, result.symbol)
        }
    }

    /**
     * Test purpose - Verify that {@link ProtobufDecoder#decode} throws
     * {@link IllegalArgumentException} when given empty bytes with no encoded fields.
     *
     * <p>Test data - Empty byte array.
     *
     * <p>Test expected result - {@link IllegalArgumentException} is thrown.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given bytes with missing symbol when decode then throws IllegalArgumentException`() {
        // Empty bytes — no fields encoded
        assertFailsWith<IllegalArgumentException> {
            ProtobufDecoder.decode(byteArrayOf())
        }
    }

    /**
     * Test purpose - Verify that {@link ProtobufDecoder#decode} throws an exception when given
     * completely malformed Protobuf bytes that cannot be parsed.
     *
     * <p>Test data - Byte array {@code [0xFF, 0xFF, 0xFF]}.
     *
     * <p>Test expected result - An {@link Exception} is thrown.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given completely malformed bytes when decode then throws`() {
        assertFailsWith<Exception> {
            ProtobufDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        }
    }

    /**
     * Test purpose - Verify that encoding a {@link MarketPrice} to Protobuf bytes and decoding
     * it back produces a {@link MarketPrice} equal to the original.
     *
     * <p>Test data - {@link MarketPrice} for symbol {@code "ETH/USD"} with {@code open=3000.0}
     * and {@code close=3100.0}, encoded and then decoded.
     *
     * <p>Test expected result - Decoded {@link MarketPrice} equals the original.
     *
     * <p>Test type - Positive.
     */
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
