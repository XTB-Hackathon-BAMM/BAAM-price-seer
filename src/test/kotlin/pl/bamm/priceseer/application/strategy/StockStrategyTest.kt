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

class StockStrategyTest {

    private val priceRepository = InMemoryPriceRepository()

    private fun strategyAt(utcHour: Int, utcMinute: Int = 0): StockStrategy {
        val fixed = Clock.fixed(
            Instant.parse("2026-04-27T%02d:%02d:00Z".format(utcHour, utcMinute)),
            ZoneOffset.UTC,
        )
        return StockStrategy(priceRepository, fixed)
    }

    private val morningSut = strategyAt(15) // 15:00 UTC — morning session
    private val lunchSut = strategyAt(17, 30) // 17:30 UTC — lunch
    private val closingSut = strategyAt(19) // 19:00 UTC — closing

    // ============================================================
    // Common / Empty history
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * as a safe default when no price history is available.
     *
     * <p>Test data - Empty list of {@link MarketPrice} for symbol {@code "XTB"}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given empty history when predict then returns UP`() {
        assertEquals(Direction.UP, morningSut.predict("XTB", emptyList()))
    }

    // ============================================================
    // Volatility Regime — FLAT
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * when ATR is below the flat threshold (0.05 USD), indicating an inactive market.
     *
     * <p>Test data - 15 candles with ATR close to zero ({@code high-low=0.01}), during
     * active session at 15:00 UTC.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `flat regime with ATR below threshold returns UP`() {
        val history = buildStockHistory(atrRange = 0.01, lastOpen = 50.0, lastClose = 49.5)
        assertEquals(Direction.UP, morningSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * when the candle body is too small relative to ATR (below FLAT body threshold).
     *
     * <p>Test data - 15 candles with ATR of {@code 0.50}, last candle with body of {@code 0.01}
     * (less than ATR * 0.15 = 0.075).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `flat regime with small body relative to ATR returns UP`() {
        val history = buildStockHistory(atrRange = 0.50, lastOpen = 50.0, lastClose = 50.01)
        assertEquals(Direction.UP, morningSut.predict("XTB", history))
    }

    // ============================================================
    // Time Gate — outside session
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * before NYSE session opens (UTC < 13:30), regardless of price action.
     *
     * <p>Test data - Clock fixed at 10:00 UTC, active history with bearish last candle.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `before session returns UP`() {
        val sut = strategyAt(10) // 10:00 UTC — before session
        val history = buildStockHistory(atrRange = 0.30, lastOpen = 50.0, lastClose = 49.5)
        assertEquals(Direction.UP, sut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * after NYSE session closes (UTC >= 20:00), regardless of price action.
     *
     * <p>Test data - Clock fixed at 21:00 UTC, active history with bearish last candle.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `after session returns UP`() {
        val sut = strategyAt(21) // 21:00 UTC — after session
        val history = buildStockHistory(atrRange = 0.30, lastOpen = 50.0, lastClose = 49.5)
        assertEquals(Direction.UP, sut.predict("XTB", history))
    }

    // ============================================================
    // Opening Gap Momentum (13:30–14:00 UTC)
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} follows pure momentum during
     * the opening phase when the opening candle body exceeds ATR * 1.5 (gap up).
     *
     * <p>Test data - Clock fixed at 13:35 UTC, 15 candles with ATR of {@code 0.20},
     * last candle with bullish body of {@code 0.40} (exceeds ATR * 1.5 = 0.30).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `opening gap large bullish candle returns UP`() {
        val sut = strategyAt(13, 35)
        val history = buildStockHistory(atrRange = 0.20, lastOpen = 50.0, lastClose = 50.40)
        assertEquals(Direction.UP, sut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} follows pure momentum during
     * the opening phase when the opening candle body exceeds ATR * 1.5 (gap down).
     *
     * <p>Test data - Clock fixed at 13:35 UTC, 15 candles with ATR of {@code 0.20},
     * last candle with bearish body of {@code 0.40} (exceeds ATR * 1.5 = 0.30).
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `opening gap large bearish candle returns DOWN`() {
        val sut = strategyAt(13, 35)
        val history = buildStockHistory(atrRange = 0.20, lastOpen = 50.40, lastClose = 50.0)
        assertEquals(Direction.DOWN, sut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * during the opening phase when the candle body is small (no gap).
     *
     * <p>Test data - Clock fixed at 13:35 UTC, 15 candles with ATR of {@code 0.20},
     * last candle with body of {@code 0.05} (less than ATR * 1.5 = 0.30).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `opening gap small candle returns UP`() {
        val sut = strategyAt(13, 35)
        val history = buildStockHistory(atrRange = 0.20, lastOpen = 50.0, lastClose = 50.05)
        assertEquals(Direction.UP, sut.predict("XTB", history))
    }

    // ============================================================
    // Morning Session — ATR-Filtered Momentum (14:00–17:00 UTC)
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * via ATR momentum during the morning session when the last candle has a strong bullish body.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 15 candles with ATR of {@code 0.30},
     * last candle with body of {@code 0.25} (exceeds ATR * 0.6 = 0.18).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session strong bullish body returns UP`() {
        val history = buildStockHistory(atrRange = 0.30, lastOpen = 50.0, lastClose = 50.25)
        assertEquals(Direction.UP, morningSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.DOWN}
     * via ATR momentum during the morning session when the last candle has a strong bearish body.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 15 candles with ATR of {@code 0.30},
     * last candle with body of {@code 0.25} (bearish, exceeds ATR * 0.6 = 0.18).
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session strong bearish body returns DOWN`() {
        val history = buildStockHistory(atrRange = 0.30, lastOpen = 50.25, lastClose = 50.0)
        assertEquals(Direction.DOWN, morningSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * during the morning session when the last candle body is weak (noise).
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 15 candles with ATR of {@code 1.0},
     * last candle with body of {@code 0.10} (less than ATR * 0.25 = 0.25).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session weak body returns UP`() {
        val history = buildStockHistory(atrRange = 1.0, lastOpen = 50.0, lastClose = 50.10)
        assertEquals(Direction.UP, morningSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} falls through to SMA Trend
     * Filter during the morning session when the candle body is in the middle ATR range.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 13 candles with rising prices (SMA3 > SMA10),
     * last candle with body in the 0.25–0.6 ATR range. Uses 13 candles to avoid RSI signal.
     *
     * <p>Test expected result - {@code Direction.UP} (SMA short above SMA long).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session mid-range body uses SMA trend filter UP`() {
        val history = buildRisingStockHistory(13, 50.0, 0.10)
        assertEquals(Direction.UP, morningSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.DOWN}
     * via SMA Trend Filter when prices are declining (SMA3 < SMA10).
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 13 candles with falling prices. Uses 13 candles
     * to avoid RSI signal.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session mid-range body uses SMA trend filter DOWN`() {
        val history = buildDecliningStockHistory(13, 55.0, 0.10)
        assertEquals(Direction.DOWN, morningSut.predict("XTB", history))
    }

    // ============================================================
    // Morning Session — Cross-Signal
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} uses the cross-signal from
     * {@code "CDR"} when predicting {@code "XTB"} — a strong bullish CDR candle overrides
     * XTB's own signal.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, CDR history with ATR of {@code 0.30} and
     * strong bullish last candle (body exceeds ATR * 0.4) stored in repository.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `XTB cross-signal CDR bullish returns UP`() {
        val cdrHistory = buildStockHistory(
            atrRange = 0.30, lastOpen = 100.0, lastClose = 100.20, symbol = "CDR",
        )
        cdrHistory.forEach { priceRepository.store(it) }

        val xtbHistory = buildStockHistory(atrRange = 0.30, lastOpen = 50.20, lastClose = 50.0, symbol = "XTB")
        assertEquals(Direction.UP, morningSut.predict("XTB", xtbHistory))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} uses the cross-signal from
     * {@code "CDR"} when predicting {@code "XTB"} — a strong bearish CDR candle overrides
     * XTB's own signal.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, CDR history with ATR of {@code 0.30} and
     * strong bearish last candle (body exceeds ATR * 0.4) stored in repository.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `XTB cross-signal CDR bearish returns DOWN`() {
        val cdrHistory = buildStockHistory(
            atrRange = 0.30, lastOpen = 100.20, lastClose = 100.0, symbol = "CDR",
        )
        cdrHistory.forEach { priceRepository.store(it) }

        val xtbHistory = buildStockHistory(atrRange = 0.30, lastOpen = 50.0, lastClose = 50.20, symbol = "XTB")
        assertEquals(Direction.DOWN, morningSut.predict("XTB", xtbHistory))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} uses the cross-signal from
     * {@code "XTB"} when predicting {@code "CDR"}.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, XTB history with ATR of {@code 0.30} and
     * strong bullish last candle stored in repository.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `CDR cross-signal XTB bullish returns UP`() {
        val xtbHistory = buildStockHistory(
            atrRange = 0.30, lastOpen = 50.0, lastClose = 50.20, symbol = "XTB",
        )
        xtbHistory.forEach { priceRepository.store(it) }

        val cdrHistory = buildStockHistory(atrRange = 0.30, lastOpen = 100.20, lastClose = 100.0, symbol = "CDR")
        assertEquals(Direction.UP, morningSut.predict("CDR", cdrHistory))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} falls back to own data when
     * the cross-signal from the other stock is too weak (body below threshold).
     *
     * <p>Test data - Clock fixed at 15:00 UTC, CDR history with ATR of {@code 1.0} and
     * tiny body of {@code 0.05} (less than ATR * 0.4 = 0.40).
     *
     * <p>Test expected result - {@code Direction.DOWN} (XTB's own bearish momentum).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `XTB weak CDR cross-signal falls through to own momentum`() {
        val cdrHistory = buildStockHistory(
            atrRange = 1.0, lastOpen = 100.0, lastClose = 100.05, symbol = "CDR",
        )
        cdrHistory.forEach { priceRepository.store(it) }

        val xtbHistory = buildStockHistory(atrRange = 0.30, lastOpen = 50.25, lastClose = 50.0, symbol = "XTB")
        assertEquals(Direction.DOWN, morningSut.predict("XTB", xtbHistory))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} uses own data when no cross-signal
     * data is available in the repository.
     *
     * <p>Test data - Clock fixed at 15:00 UTC, no CDR data in repository.
     *
     * <p>Test expected result - {@code Direction.DOWN} (XTB's own bearish ATR momentum).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `XTB no CDR data falls through to own momentum`() {
        val xtbHistory = buildStockHistory(atrRange = 0.30, lastOpen = 50.25, lastClose = 50.0, symbol = "XTB")
        assertEquals(Direction.DOWN, morningSut.predict("XTB", xtbHistory))
    }

    // ============================================================
    // Morning Session — RSI
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * during the morning session when RSI indicates oversold conditions (RSI < 25).
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 15 declining candles producing RSI below 25.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session oversold RSI returns UP`() {
        val history = buildDecliningStockHistory(15, 55.0, 0.15)
        assertEquals(Direction.UP, morningSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.DOWN}
     * during the morning session when RSI indicates overbought conditions (RSI > 75).
     *
     * <p>Test data - Clock fixed at 15:00 UTC, 15 rising candles producing RSI above 75.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `morning session overbought RSI returns DOWN`() {
        val history = buildRisingStockHistory(15, 50.0, 0.15)
        assertEquals(Direction.DOWN, morningSut.predict("XTB", history))
    }

    // ============================================================
    // Lunch Mean Reversion (17:00–18:30 UTC)
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} applies mean reversion during
     * lunch time after 3 consecutive bullish candles.
     *
     * <p>Test data - Clock fixed at 17:30 UTC, 3 bullish candles with active ATR.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `lunch 3 bullish streak returns DOWN`() {
        val history = (1..3).map {
            marketPrice(symbol = "XTB", open = 50.0, close = 50.30, high = 50.40, low = 49.90)
        }
        assertEquals(Direction.DOWN, lunchSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} applies mean reversion during
     * lunch time after 3 consecutive bearish candles.
     *
     * <p>Test data - Clock fixed at 17:30 UTC, 3 bearish candles with active ATR.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `lunch 3 bearish streak returns UP`() {
        val history = (1..3).map {
            marketPrice(symbol = "XTB", open = 50.30, close = 50.0, high = 50.40, low = 49.90)
        }
        assertEquals(Direction.UP, lunchSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} returns {@code Direction.UP}
     * during lunch time when there is no 3-candle streak.
     *
     * <p>Test data - Clock fixed at 17:30 UTC, 2 alternating candles with active ATR.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `lunch no streak returns UP`() {
        val history = listOf(
            marketPrice(symbol = "XTB", open = 50.0, close = 50.30, high = 50.40, low = 49.90),
            marketPrice(symbol = "XTB", open = 50.30, close = 50.0, high = 50.40, low = 49.90),
        )
        assertEquals(Direction.UP, lunchSut.predict("XTB", history))
    }

    // ============================================================
    // Closing Momentum (18:30–20:00 UTC)
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} uses SMA trend filter during
     * the closing phase, returning {@code Direction.UP} when SMA3 > SMA10.
     *
     * <p>Test data - Clock fixed at 19:00 UTC, 15 rising candles.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `closing SMA trend UP returns UP`() {
        val history = buildRisingStockHistory(15, 50.0, 0.10)
        assertEquals(Direction.UP, closingSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} uses SMA trend filter during
     * the closing phase, returning {@code Direction.DOWN} when SMA3 < SMA10.
     *
     * <p>Test data - Clock fixed at 19:00 UTC, 15 declining candles.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `closing SMA trend DOWN returns DOWN`() {
        val history = buildDecliningStockHistory(15, 55.0, 0.10)
        assertEquals(Direction.DOWN, closingSut.predict("XTB", history))
    }

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} falls back to pure momentum
     * during the closing phase when not enough history for SMA10.
     *
     * <p>Test data - Clock fixed at 19:00 UTC, single bullish candle with active ATR.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `closing insufficient history uses pure momentum`() {
        val history = listOf(
            marketPrice(symbol = "XTB", open = 50.0, close = 50.30, high = 50.40, low = 49.90),
        )
        assertEquals(Direction.UP, closingSut.predict("XTB", history))
    }

    // ============================================================
    // Cross-signal not used outside morning session
    // ============================================================

    /**
     * Test purpose - Verify that {@link StockStrategy#predict} does not use the cross-signal
     * during the lunch phase — lunch uses mean reversion only.
     *
     * <p>Test data - Clock fixed at 17:30 UTC (lunch), CDR strong bullish signal in repository,
     * XTB with 3 bearish candles.
     *
     * <p>Test expected result - {@code Direction.UP} (mean reversion from bearish streak,
     * not cross-signal).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `cross-signal not used during lunch`() {
        val cdrHistory = buildStockHistory(
            atrRange = 0.30, lastOpen = 100.0, lastClose = 100.20, symbol = "CDR",
        )
        cdrHistory.forEach { priceRepository.store(it) }

        val xtbHistory = (1..3).map {
            marketPrice(symbol = "XTB", open = 50.30, close = 50.0, high = 50.40, low = 49.90)
        }
        assertEquals(Direction.UP, lunchSut.predict("XTB", xtbHistory))
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun buildStockHistory(
        atrRange: Double,
        lastOpen: Double,
        lastClose: Double,
        symbol: String = "XTB",
    ): List<MarketPrice> {
        val mid = (lastOpen + lastClose) / 2
        val wobble = atrRange * 0.05
        val candles = (1..14).map { i ->
            val offset = if (i % 2 == 0) wobble else -wobble
            marketPrice(
                symbol = symbol,
                open = mid - offset,
                close = mid + offset,
                high = mid + atrRange / 2,
                low = mid - atrRange / 2,
            )
        }
        return candles + marketPrice(
            symbol = symbol,
            open = lastOpen,
            close = lastClose,
            high = maxOf(lastOpen, lastClose) + atrRange / 4,
            low = minOf(lastOpen, lastClose) - atrRange / 4,
        )
    }

    private fun buildRisingStockHistory(
        periods: Int,
        startPrice: Double,
        risePerCandle: Double,
    ): List<MarketPrice> {
        return (0 until periods).map { i ->
            val rise = if (i % 2 == 0) risePerCandle * 1.5 else risePerCandle * 0.5
            val open = startPrice + i * risePerCandle
            val close = open + rise
            val spread = rise * 2
            marketPrice(
                symbol = "XTB",
                open = open,
                close = close,
                high = open + spread,
                low = open - spread / 2,
            )
        }
    }

    private fun buildDecliningStockHistory(
        periods: Int,
        startPrice: Double,
        dropPerCandle: Double,
    ): List<MarketPrice> {
        return (0 until periods).map { i ->
            val drop = if (i % 2 == 0) dropPerCandle * 1.5 else dropPerCandle * 0.5
            val open = startPrice - i * dropPerCandle
            val close = open - drop
            val spread = drop * 2
            marketPrice(
                symbol = "XTB",
                open = open,
                close = close,
                high = open + spread,
                low = open - spread,
            )
        }
    }
}
