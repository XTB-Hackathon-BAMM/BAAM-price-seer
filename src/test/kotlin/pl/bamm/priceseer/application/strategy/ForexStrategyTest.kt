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

class ForexStrategyTest {

    private val priceRepository = InMemoryPriceRepository()

    private fun strategyAt(utcHour: Int, utcMinute: Int = 0): ForexStrategy {
        val fixed = Clock.fixed(
            Instant.parse("2026-04-27T%02d:%02d:00Z".format(utcHour, utcMinute)),
            ZoneOffset.UTC,
        )
        return ForexStrategy(priceRepository, fixed)
    }

    private val daytimeSut = strategyAt(10)

    // ============================================================
    // Common
    // ============================================================

    @Test
    fun `given empty history when predict then returns UP`() {
        assertEquals(Direction.UP, daytimeSut.predict("EUR/USD", emptyList()))
    }

    // ============================================================
    // EUR/USD
    // ============================================================

    @Test
    fun `EUR-USD quiet regime returns UP`() {
        val history = (1..5).map { marketPrice(symbol = "EUR/USD", open = 1.1000, close = 1.10001) }
        assertEquals(Direction.UP, daytimeSut.predict("EUR/USD", history))
    }

    @Test
    fun `EUR-USD small candle returns UP`() {
        val history = buildForexHistoryWithAtr(
            atrRange = 0.0020,
            lastOpen = 1.1000,
            lastClose = 1.10002, // body = 0.00002, ATR ≈ 0.002 → body < ATR*0.25
            symbol = "EUR/USD",
        )
        assertEquals(Direction.UP, daytimeSut.predict("EUR/USD", history))
    }

    @Test
    fun `EUR-USD mean reversion after 3 bullish candles returns DOWN`() {
        // Bodies must be > ATR*0.25 to pass small candle filter, varied to avoid QUIET regime
        val history = listOf(
            marketPrice(symbol = "EUR/USD", open = 1.1000, close = 1.1008, high = 1.1010, low = 1.0998),
            marketPrice(symbol = "EUR/USD", open = 1.1000, close = 1.1012, high = 1.1014, low = 1.0998),
            marketPrice(symbol = "EUR/USD", open = 1.1000, close = 1.1006, high = 1.1008, low = 1.0998),
        )
        assertEquals(Direction.DOWN, daytimeSut.predict("EUR/USD", history))
    }

    @Test
    fun `EUR-USD mean reversion after 3 bearish candles returns UP`() {
        val history = listOf(
            marketPrice(symbol = "EUR/USD", open = 1.1010, close = 1.1002, high = 1.1012, low = 1.1000),
            marketPrice(symbol = "EUR/USD", open = 1.1010, close = 1.0998, high = 1.1012, low = 1.0996),
            marketPrice(symbol = "EUR/USD", open = 1.1010, close = 1.1004, high = 1.1012, low = 1.1002),
        )
        assertEquals(Direction.UP, daytimeSut.predict("EUR/USD", history))
    }

    @Test
    fun `EUR-USD fallback returns UP`() {
        // 2 candles with alternating direction → no streak, not enough for RSI → fallback UP
        val history = listOf(
            marketPrice(symbol = "EUR/USD", open = 1.1000, close = 1.1010, high = 1.1015, low = 1.0995),
            marketPrice(symbol = "EUR/USD", open = 1.1010, close = 1.1000, high = 1.1015, low = 1.0995),
        )
        assertEquals(Direction.UP, daytimeSut.predict("EUR/USD", history))
    }

    @Test
    fun `EUR-USD oversold RSI returns UP`() {
        val history = buildDecliningForexHistory(15, 1.1000, 0.0010, "EUR/USD")
        assertEquals(Direction.UP, daytimeSut.predict("EUR/USD", history))
    }

    @Test
    fun `EUR-USD overbought RSI returns DOWN`() {
        val history = buildRisingForexHistory(15, 1.1000, 0.0010, "EUR/USD")
        assertEquals(Direction.DOWN, daytimeSut.predict("EUR/USD", history))
    }

    // ============================================================
    // GBP/JPY
    // ============================================================

    @Test
    fun `GBP-JPY asian session returns UP`() {
        val sut = strategyAt(3) // 03:00 UTC — Asian session
        val history = listOf(marketPrice(symbol = "GBP/JPY", open = 190.0, close = 189.0))
        assertEquals(Direction.UP, sut.predict("GBP/JPY", history))
    }

    @Test
    fun `GBP-JPY quiet regime returns UP`() {
        val history = (1..5).map { marketPrice(symbol = "GBP/JPY", open = 190.0, close = 190.001) }
        assertEquals(Direction.UP, daytimeSut.predict("GBP/JPY", history))
    }

    @Test
    fun `GBP-JPY strong bullish body returns UP via momentum`() {
        val history = buildForexHistoryWithAtr(
            atrRange = 0.20,
            lastOpen = 190.0,
            lastClose = 190.20, // body = 0.20, ATR ≈ 0.20 → body > ATR*0.5
            symbol = "GBP/JPY",
        )
        assertEquals(Direction.UP, daytimeSut.predict("GBP/JPY", history))
    }

    @Test
    fun `GBP-JPY strong bearish body returns DOWN via momentum`() {
        val history = buildForexHistoryWithAtr(
            atrRange = 0.20,
            lastOpen = 190.20,
            lastClose = 190.0,
            symbol = "GBP/JPY",
        )
        assertEquals(Direction.DOWN, daytimeSut.predict("GBP/JPY", history))
    }

    @Test
    fun `GBP-JPY mean reversion after 3 bullish candles returns DOWN`() {
        val base = buildForexHistoryWithAtr(atrRange = 0.20, lastOpen = 190.0, lastClose = 190.0, symbol = "GBP/JPY")
        // Add 3 small bullish candles (body < ATR*0.5 to skip ATR momentum)
        val streak = (1..3).map {
            marketPrice(symbol = "GBP/JPY", open = 190.0, close = 190.02, high = 190.10, low = 189.90)
        }
        assertEquals(Direction.DOWN, daytimeSut.predict("GBP/JPY", base + streak))
    }

    @Test
    fun `GBP-JPY pure momentum fallback bullish returns UP`() {
        val base = buildForexHistoryWithAtr(atrRange = 0.50, lastOpen = 190.0, lastClose = 190.0, symbol = "GBP/JPY")
        // Two alternating small candles → no streak → fallback to pure momentum
        val tail = listOf(
            marketPrice(symbol = "GBP/JPY", open = 190.0, close = 189.99, high = 190.25, low = 189.75),
            marketPrice(symbol = "GBP/JPY", open = 190.0, close = 190.01, high = 190.25, low = 189.75),
        )
        assertEquals(Direction.UP, daytimeSut.predict("GBP/JPY", base + tail))
    }

    // ============================================================
    // USD/JPY
    // ============================================================

    @Test
    fun `USD-JPY asian session returns UP`() {
        val sut = strategyAt(5) // 05:00 UTC — Asian session
        val history = listOf(marketPrice(symbol = "USD/JPY", open = 155.0, close = 154.0))
        assertEquals(Direction.UP, sut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY quiet regime returns UP`() {
        val history = (1..5).map { marketPrice(symbol = "USD/JPY", open = 155.0, close = 155.001) }
        assertEquals(Direction.UP, daytimeSut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY counter-trend after large bullish candle returns DOWN`() {
        val history = buildForexHistoryWithAtr(
            atrRange = 0.10,
            lastOpen = 155.0,
            lastClose = 155.30, // body = 0.30, ATR ≈ 0.10 → body > ATR*2.0 → counter-trend
            symbol = "USD/JPY",
        )
        assertEquals(Direction.DOWN, daytimeSut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY counter-trend after large bearish candle returns UP`() {
        val history = buildForexHistoryWithAtr(
            atrRange = 0.10,
            lastOpen = 155.30,
            lastClose = 155.0,
            symbol = "USD/JPY",
        )
        assertEquals(Direction.UP, daytimeSut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY risk-proxy both stocks UP during NY session returns UP`() {
        val sut = strategyAt(15) // 15:00 UTC — NY session
        priceRepository.store(marketPrice(symbol = "AAPL", open = 180.0, close = 181.0))
        priceRepository.store(marketPrice(symbol = "MSFT", open = 420.0, close = 421.0))

        // Normal candle, no counter-trend trigger
        val history = listOf(
            marketPrice(symbol = "USD/JPY", open = 155.0, close = 154.99, high = 155.05, low = 154.95),
        )
        assertEquals(Direction.UP, sut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY risk-proxy both stocks DOWN during NY session returns DOWN`() {
        val sut = strategyAt(15)
        priceRepository.store(marketPrice(symbol = "AAPL", open = 181.0, close = 180.0))
        priceRepository.store(marketPrice(symbol = "MSFT", open = 421.0, close = 420.0))

        val history = listOf(
            marketPrice(symbol = "USD/JPY", open = 155.0, close = 155.01, high = 155.05, low = 154.95),
        )
        assertEquals(Direction.DOWN, sut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY risk-proxy not used outside NY session`() {
        val sut = strategyAt(10) // 10:00 UTC — outside NY session
        priceRepository.store(marketPrice(symbol = "AAPL", open = 181.0, close = 180.0))
        priceRepository.store(marketPrice(symbol = "MSFT", open = 421.0, close = 420.0))

        // Both stocks DOWN but not in NY session → risk proxy skipped → fallback UP
        val history = listOf(
            marketPrice(symbol = "USD/JPY", open = 155.0, close = 155.01, high = 155.05, low = 154.95),
        )
        assertEquals(Direction.UP, sut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY risk-proxy mixed signals falls through`() {
        val sut = strategyAt(15)
        priceRepository.store(marketPrice(symbol = "AAPL", open = 180.0, close = 181.0)) // UP
        priceRepository.store(marketPrice(symbol = "MSFT", open = 421.0, close = 420.0)) // DOWN

        val history = listOf(
            marketPrice(symbol = "USD/JPY", open = 155.0, close = 155.01, high = 155.05, low = 154.95),
        )
        // Mixed signals → no consensus → falls through to RSI/mean reversion → fallback UP
        assertEquals(Direction.UP, sut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY mean reversion after 3 bearish candles returns UP`() {
        val history = (1..3).map {
            marketPrice(symbol = "USD/JPY", open = 155.10, close = 155.0, high = 155.15, low = 154.95)
        }
        assertEquals(Direction.UP, daytimeSut.predict("USD/JPY", history))
    }

    @Test
    fun `USD-JPY fallback returns UP`() {
        val history = listOf(
            marketPrice(symbol = "USD/JPY", open = 155.0, close = 155.01, high = 155.05, low = 154.95),
        )
        assertEquals(Direction.UP, daytimeSut.predict("USD/JPY", history))
    }

    // ============================================================
    // Helpers
    // ============================================================

    private fun buildForexHistoryWithAtr(
        atrRange: Double,
        lastOpen: Double,
        lastClose: Double,
        symbol: String,
    ): List<MarketPrice> {
        val mid = (lastOpen + lastClose) / 2
        val candles = (1..14).map {
            marketPrice(
                symbol = symbol,
                open = mid,
                close = mid,
                high = mid + atrRange / 2,
                low = mid - atrRange / 2,
            )
        }
        return candles + marketPrice(
            symbol = symbol,
            open = lastOpen,
            close = lastClose,
            high = maxOf(lastOpen, lastClose),
            low = minOf(lastOpen, lastClose),
        )
    }

    private fun buildDecliningForexHistory(
        periods: Int,
        startPrice: Double,
        dropPerCandle: Double,
        symbol: String,
    ): List<MarketPrice> {
        return (0 until periods).map { i ->
            val drop = if (i % 2 == 0) dropPerCandle * 1.5 else dropPerCandle * 0.5
            val open = startPrice - i * dropPerCandle
            val close = open - drop
            val range = drop * 3
            marketPrice(symbol = symbol, open = open, close = close, high = open + range / 2, low = open - range / 2)
        }
    }

    private fun buildRisingForexHistory(
        periods: Int,
        startPrice: Double,
        risePerCandle: Double,
        symbol: String,
    ): List<MarketPrice> {
        return (0 until periods).map { i ->
            val rise = if (i % 2 == 0) risePerCandle * 1.5 else risePerCandle * 0.5
            val open = startPrice + i * risePerCandle
            val close = open + rise
            val range = rise * 3
            marketPrice(symbol = symbol, open = open, close = close, high = open + range, low = open - range / 2)
        }
    }
}
