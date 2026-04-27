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

    @Test
    fun `given empty history when predict then returns UP`() {
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", emptyList()))
    }

    @Test
    fun `given quiet regime when predict then returns UP`() {
        val history = (1..5).map { marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.01) }
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // PM Fix Counter-Trend
    // ============================================================

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

    @Test
    fun `PM Fix small candle does not trigger counter-trend`() {
        val sut = strategyAt(14, 2)
        // body = 0.5, ATR ≈ 2.0 → body < ATR*2.0 → no PM Fix signal → proceeds to ATR filter
        // body < ATR*0.3 = 0.6 → UP from ATR weak filter
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2500.0, lastClose = 2500.5)
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

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

    @Test
    fun `strong bullish body returns UP via momentum`() {
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2500.0, lastClose = 2502.0)
        // body = 2.0, ATR ≈ 2.0, body > ATR*0.8=1.6 → momentum UP
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    @Test
    fun `strong bearish body returns DOWN via momentum`() {
        val history = buildGoldHistoryWithAtr(atrRange = 2.0, lastOpen = 2502.0, lastClose = 2500.0)
        assertEquals(Direction.DOWN, daytimeSut.predict("XAU/USD", history))
    }

    @Test
    fun `weak body returns UP`() {
        val history = buildGoldHistoryWithAtr(atrRange = 10.0, lastOpen = 2500.0, lastClose = 2500.5)
        // body=0.5, ATR≈10 → body < ATR*0.3=3.0 → UP
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // Regression to Mean
    // ============================================================

    @Test
    fun `price above SMA10 by more than 0_15 pct returns DOWN`() {
        // 11 candles: RSI needs 15 → null. Body in ATR middle range. Close far above SMA10.
        val base = (1..10).map { marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.0, high = 2501.0, low = 2499.0) }
        val last = marketPrice(symbol = "XAU/USD", open = 2503.5, close = 2504.5, high = 2505.0, low = 2503.0)
        val history = base + last
        assertEquals(Direction.DOWN, daytimeSut.predict("XAU/USD", history))
    }

    @Test
    fun `price below SMA10 by more than 0_15 pct returns UP`() {
        val base = (1..10).map { marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.0, high = 2501.0, low = 2499.0) }
        val last = marketPrice(symbol = "XAU/USD", open = 2496.5, close = 2495.5, high = 2497.0, low = 2495.0)
        val history = base + last
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    // ============================================================
    // Risk Proxy (inverse for gold)
    // ============================================================

    @Test
    fun `risk-on stocks both UP during NY session returns DOWN for gold`() {
        val sut = strategyAt(15) // NY session
        priceRepository.store(marketPrice(symbol = "AAPL", open = 180.0, close = 181.0))
        priceRepository.store(marketPrice(symbol = "MSFT", open = 420.0, close = 421.0))

        val history = listOf(
            marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.5, high = 2503.0, low = 2497.0),
        )
        assertEquals(Direction.DOWN, sut.predict("XAU/USD", history))
    }

    @Test
    fun `risk-off stocks both DOWN during NY session returns UP for gold`() {
        val sut = strategyAt(15)
        priceRepository.store(marketPrice(symbol = "AAPL", open = 181.0, close = 180.0))
        priceRepository.store(marketPrice(symbol = "MSFT", open = 421.0, close = 420.0))

        val history = listOf(
            marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2499.5, high = 2503.0, low = 2497.0),
        )
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    @Test
    fun `risk proxy not used outside NY session`() {
        val sut = strategyAt(10) // 10:00 UTC — outside NY
        priceRepository.store(marketPrice(symbol = "AAPL", open = 180.0, close = 181.0))
        priceRepository.store(marketPrice(symbol = "MSFT", open = 420.0, close = 421.0))

        // Single bullish candle → falls through to pure momentum → UP
        val history = listOf(
            marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2500.5, high = 2503.0, low = 2497.0),
        )
        assertEquals(Direction.UP, sut.predict("XAU/USD", history))
    }

    // ============================================================
    // SMA Trend Filter (fallback)
    // ============================================================

    @Test
    fun `SMA short above rising SMA long returns UP`() {
        // 13 candles: RSI needs 15 → null. BB needs 20 → null. Body in ATR middle range.
        val history = (0 until 13).map { i ->
            val price = 2500.0 + i * 0.3
            marketPrice(symbol = "XAU/USD", open = price, close = price + 0.5, high = price + 0.6, low = price - 0.1)
        }
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

    @Test
    fun `SMA short below falling SMA long returns DOWN`() {
        // Alternating body sizes (1.0/0.3) for NORMAL regime. Spread=2.0 keeps body in ATR middle range.
        val history = (0 until 13).map { i ->
            val price = 2510.0 - i * 0.3
            val body = if (i % 2 == 0) 1.0 else 0.3
            marketPrice(symbol = "XAU/USD", open = price, close = price - body, high = price + 0.5, low = price - 1.5)
        }
        assertEquals(Direction.DOWN, daytimeSut.predict("XAU/USD", history))
    }

    @Test
    fun `single bullish candle uses pure momentum fallback UP`() {
        val history = listOf(marketPrice(symbol = "XAU/USD", open = 2500.0, close = 2501.0))
        assertEquals(Direction.UP, daytimeSut.predict("XAU/USD", history))
    }

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
