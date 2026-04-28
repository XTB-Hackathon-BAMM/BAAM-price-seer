package pl.bamm.priceseer.application.strategy

import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.fixtures.marketPrice
import pl.bamm.priceseer.infrastructure.persistence.InMemoryPriceRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class GoldStrategyTest {

    private val priceRepository = InMemoryPriceRepository()

    private fun strategyAt(utcHour: Int, utcMinute: Int = 0): GoldStrategy {
        val fixed = Clock.fixed(
            Instant.parse("2026-04-27T%02d:%02d:00Z".format(utcHour, utcMinute)),
            ZoneOffset.UTC,
        )
        return GoldStrategy(priceRepository, fixed)
    }

    private val daytimeSut = strategyAt(10)

    // ============================================================
    // Common
    // ============================================================

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.UP}
     * as a safe default when no price history is available.
     *
     * <p>Test data - Empty list of {@link MarketPrice} for symbol {@code "XAU/USD"}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given empty history when predict then returns UP`() {
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", emptyList()))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} detects a QUIET regime for
     * {@code "XAU/USD"} (near-zero volatility) and returns {@code Direction.UP}.
     *
     * <p>Test data - 5 candles with nearly identical open/close ({@code open=2500.0},
     * {@code close=2500.01}).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given quiet regime when predict then returns UP`() {
        val history = (1..5).map { marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.01) }
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // PM Fix Counter-Trend
    // ============================================================

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} applies PM Fix counter-trend
     * reversal during the 14:00-14:05 UTC window when a large bullish candle is detected.
     *
     * <p>Test data - Clock fixed at 14:02 UTC (PM Fix window), 15 candles with ATR of
     * {@code 2.0}, last candle with body of {@code 5.0} (exceeds ATR * 2.0).
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `PM Fix large bullish candle returns DOWN`() {
        val sut = strategyAt(14, 2) // 14:02 UTC — within PM Fix window
        val history = buildGoldHistoryWithAtr(
            atrRange = 2.0,
            lastOpen = 2500.0,
            lastClose = 2505.0, // body = 5.0, ATR ≈ 2.0 → body > ATR*2.0
        )
        assertEquals(Direction.DOWN, sut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} applies PM Fix counter-trend
     * reversal during the 14:00-14:05 UTC window when a large bearish candle is detected.
     *
     * <p>Test data - Clock fixed at 14:03 UTC (PM Fix window), 15 candles with ATR of
     * {@code 2.0}, last candle with body of {@code 5.0} (bearish, exceeds ATR * 2.0).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `PM Fix large bearish candle returns UP`() {
        val sut = strategyAt(14, 3)
        val history = buildGoldHistoryWithAtr(
            atrRange = 2.0,
            lastOpen = 2505.0,
            lastClose = 2500.0,
        )
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} does not trigger PM Fix
     * counter-trend when the candle's body is too small relative to ATR, even during the
     * PM Fix window.
     *
     * <p>Test data - Clock fixed at 14:02 UTC (PM Fix window), 15 candles with ATR of
     * {@code 2.0}, last candle with body of {@code 0.5} (less than ATR * 2.0).
     *
     * <p>Test expected result - {@code Direction.UP} (proceeds to ATR weak filter).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `PM Fix small candle does not trigger counter-trend`() {
        val sut = strategyAt(14, 2)
        // body = 0.5, ATR ≈ 2.0 → body < ATR*2.0 → no PM Fix signal → proceeds to ATR filter
        // body < ATR*0.3 = 0.6 → UP from ATR weak filter
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2500.0, lastClose = 2500.5)
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} does not trigger PM Fix
     * counter-trend outside the 14:00-14:05 UTC window, even with a large candle body.
     *
     * <p>Test data - Clock fixed at 15:00 UTC (outside PM Fix window), 15 candles with ATR of
     * {@code 2.0}, last candle with body of {@code 5.0}.
     *
     * <p>Test expected result - {@code Direction.UP} (ATR strong momentum, not counter-trend).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `outside PM Fix window does not trigger counter-trend`() {
        val sut = strategyAt(15, 0) // 15:00 — outside 14:00-14:05
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2500.0, lastClose = 2505.0)
        // body=5.0, ATR≈2.0 → body > ATR*0.8=1.6 → ATR strong → momentum UP
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    // ============================================================
    // ATR Filter
    // ============================================================

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.UP}
     * via ATR-filtered momentum when the last candle has a strong bullish body.
     *
     * <p>Test data - 15 candles with ATR of {@code 2.0}, last candle with body of {@code 2.0}
     * (exceeds ATR * 0.8).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `strong bullish body returns UP via momentum`() {
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2500.0, lastClose = 2502.0)
        // body = 2.0, ATR ≈ 2.0, body > ATR*0.8=1.6 → momentum UP
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.DOWN}
     * via ATR-filtered momentum when the last candle has a strong bearish body.
     *
     * <p>Test data - 15 candles with ATR of {@code 2.0}, last candle with body of {@code 2.0}
     * (bearish, exceeds ATR * 0.8).
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `strong bearish body returns DOWN via momentum`() {
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2502.0, lastClose = 2500.0)
        assertEquals(Direction.DOWN, daytimeSut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.UP}
     * when the last candle's body is weak relative to ATR, treating it as noise.
     *
     * <p>Test data - 15 candles with ATR of {@code 10.0}, last candle with body of {@code 0.5}
     * (less than ATR * 0.3).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `weak body returns UP`() {
        val history = buildGoldHistoryWithAtr(atrRange = 10.0, lastOpen = 2500.0, lastClose = 2500.5)
        // body=0.5, ATR≈10 → body < ATR*0.3=3.0 → UP
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // Pure momentum fallback (insufficient history for SMA slope)
    // ============================================================

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.UP}
     * via pure momentum fallback when history is insufficient for SMA slope analysis and
     * the last candle is bullish.
     *
     * <p>Test data - 10 flat candles at {@code 2500.0} followed by one bullish candle closing
     * at {@code 2504.5}. Only 11 candles — not enough for SMA slope (needs 13).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `price above SMA10 with insufficient slope history returns UP via momentum`() {
        val base = (1..10).map { marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.0, high = 2501.0, low = 2499.0) }
        val last = marketPrice(symbol = "XAU/USD", open = 2503.5, close = 2504.5, high = 2505.0, low = 2503.0)
        val history = base + last
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.UP}
     * for a QUIET regime when 10 flat candles are followed by one small bearish candle.
     * The low volatility of this data triggers the QUIET regime classification.
     *
     * <p>Test data - 10 flat candles at {@code 2500.0} followed by one bearish candle closing
     * at {@code 2495.5}. Volatility std falls below QUIET threshold.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `low volatility bearish data classified as QUIET returns UP`() {
        val base = (1..10).map { marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.0, high = 2501.0, low = 2499.0) }
        val last = marketPrice(symbol = "XAU/USD", open = 2496.5, close = 2495.5, high = 2497.0, low = 2495.0)
        val history = base + last
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // Risk Proxy (inverse for gold)
    // ============================================================

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} applies the inverse risk proxy
     * during the NY session — when both stock proxies are UP (risk-on), gold is predicted DOWN.
     *
     * <p>Test data - Clock fixed at 15:00 UTC (NY session), {@code "XTB"} and {@code "CDR"}
     * both bullish in repository, normal {@code "XAU/USD"} candle.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `risk-on stocks both UP during NY session returns DOWN for gold`() {
        val sut = strategyAt(15) // NY session
        priceRepository.store(marketPrice(symbol = "XTB", open = 180.0, close = 181.0))
        priceRepository.store(marketPrice(symbol = "CDR", open = 420.0, close = 421.0))

        val history = listOf(
            marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.5, high = 2503.0, low = 2497.0),
        )
        assertEquals(Direction.DOWN, sut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} applies the inverse risk proxy
     * during the NY session — when both stock proxies are DOWN (risk-off), gold is predicted UP.
     *
     * <p>Test data - Clock fixed at 15:00 UTC (NY session), {@code "XTB"} and {@code "CDR"}
     * both bearish in repository, normal {@code "XAU/USD"} candle.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `risk-off stocks both DOWN during NY session returns UP for gold`() {
        val sut = strategyAt(15)
        priceRepository.store(marketPrice(symbol = "XTB", open = 181.0, close = 180.0))
        priceRepository.store(marketPrice(symbol = "CDR", open = 421.0, close = 420.0))

        val history = listOf(
            marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2499.5, high = 2503.0, low = 2497.0),
        )
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} does not use the risk proxy
     * outside the NY session, even when both stocks are bullish.
     *
     * <p>Test data - Clock fixed at 10:00 UTC (outside NY session), {@code "XTB"} and
     * {@code "CDR"} both bullish in repository, single bullish {@code "XAU/USD"} candle.
     *
     * <p>Test expected result - {@code Direction.UP} (risk proxy skipped, pure momentum fallback).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `risk proxy not used outside NY session`() {
        val sut = strategyAt(10) // 10:00 UTC — outside NY
        priceRepository.store(marketPrice(symbol = "XTB", open = 180.0, close = 181.0))
        priceRepository.store(marketPrice(symbol = "CDR", open = 420.0, close = 421.0))

        // Single bullish candle → falls through to pure momentum → UP
        val history = listOf(
            marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.5, high = 2503.0, low = 2497.0),
        )
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    // ============================================================
    // SMA Trend Filter (fallback)
    // ============================================================

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.UP}
     * when the short-term SMA is above the rising long-term SMA, indicating an uptrend.
     *
     * <p>Test data - 13 candles with gradually rising prices, insufficient for RSI or
     * Bollinger Bands.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `SMA short above rising SMA long returns UP`() {
        // 13 candles: RSI needs 15 → null. BB needs 20 → null. Body in ATR middle range.
        val history = (0 until 13).map { i ->
            val price = 2500.0 + i * 0.3
            marketPrice(symbol = "XAU/USD", open = price, close = price + 0.5, high = price + 0.6, low = price - 0.1)
        }
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} returns {@code Direction.DOWN}
     * when the short-term SMA is below the falling long-term SMA, indicating a downtrend.
     *
     * <p>Test data - 13 candles with gradually falling prices, alternating body sizes for
     * NORMAL regime classification.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `SMA short below falling SMA long returns DOWN`() {
        val history = (0 until 13).map { i ->
            val price = 2510.0 - i * 0.8
            val body = if (i % 2 == 0) 3.0 else 1.0
            marketPrice(symbol = "XAU/USD", open = price, close = price - body, high = price + 1.0, low = price - 4.0)
        }
        assertEquals(Direction.DOWN, daytimeSut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} uses pure momentum fallback
     * and returns {@code Direction.UP} for a single bullish candle.
     *
     * <p>Test data - Single {@link MarketPrice} for {@code "XAU/USD"} with {@code open=2500.0}
     * and {@code close=2501.0}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `single bullish candle uses pure momentum fallback UP`() {
        val history = listOf(marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2501.0))
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    /**
     * Test purpose - Verify that {@link GoldStrategy#predict} uses pure momentum fallback
     * and returns {@code Direction.DOWN} for a single bearish candle.
     *
     * <p>Test data - Single {@link MarketPrice} for {@code "XAU/USD"} with {@code open=2501.0}
     * and {@code close=2500.0}.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `single bearish candle uses pure momentum fallback DOWN`() {
        val history = listOf(marketPrice(symbol = "XAU/USD", open = 2501.0, close = 2500.0))
        assertEquals(Direction.DOWN, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun buildGoldHistoryWithAtr(
        atrRange: Double,
        lastOpen: Double,
        lastClose: Double,
    ): List<MarketPrice> {
        val mid = (lastOpen + lastClose) / 2
        val candles = (1..14).map {
            marketPrice(
                symbol = "XAU/USD",
                open = mid,
                close = mid,
                high = mid + atrRange / 2,
                low = mid - atrRange / 2,
            )
        }
        return candles + marketPrice(
            symbol = "XAU/USD",
            open = lastOpen,
            close = lastClose,
            high = maxOf(lastOpen, lastClose),
            low = minOf(lastOpen, lastClose),
        )
    }
}
