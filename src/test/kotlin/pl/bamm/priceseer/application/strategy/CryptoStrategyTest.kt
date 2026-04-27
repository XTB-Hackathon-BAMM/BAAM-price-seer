package pl.bamm.priceseer.application.strategy

import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.fixtures.marketPrice
import pl.bamm.priceseer.infrastructure.persistence.InMemoryPriceRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoStrategyTest {

    private val priceRepository = InMemoryPriceRepository()
    private val sut = CryptoStrategy(priceRepository)

    @Test
    fun `given empty history when predict then returns UP`() {
        assertEquals(Direction.UP, sut.predict("BTC/USD", emptyList()))
    }

    // -- Regime detection (Step 1) --

    @Test
    fun `given quiet regime when predict then returns UP`() {
        // All candles with nearly identical open/close → std of returns ≈ 0
        val history = (1..5).map { marketPrice(open = 100.0, close = 100.001) }

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    @Test
    fun `given trending regime with strong bearish candle when predict then returns DOWN`() {
        // High volatility returns + strong bearish last candle → momentum DOWN
        val history = buildTrendingHistory(lastOpen = 100.0, lastClose = 95.0)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    // -- Doji filter --

    @Test
    fun `given doji candle when predict then overrides to UP`() {
        // Candle with tiny body relative to shadow → doji
        val doji = marketPrice(open = 100.0, close = 100.01, high = 101.0, low = 99.0)
        val history = buildNormalHistory() + doji

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    // -- ATR-filtered momentum (Step 2a) --

    @Test
    fun `given strong bullish body relative to ATR when predict then returns UP`() {
        val history = buildHistoryWithConsistentAtr(atrRange = 1.0, lastOpen = 100.0, lastClose = 101.0)

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    @Test
    fun `given strong bearish body relative to ATR when predict then returns DOWN`() {
        val history = buildHistoryWithConsistentAtr(atrRange = 1.0, lastOpen = 101.0, lastClose = 100.0)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    @Test
    fun `given weak body relative to ATR when predict then returns UP`() {
        // body < ATR * 0.3 → noise → UP
        val history = buildHistoryWithConsistentAtr(atrRange = 10.0, lastOpen = 100.0, lastClose = 100.1)

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    // -- RSI (Step 2b) --

    @Test
    fun `given oversold RSI when predict then returns UP`() {
        // Series of declining closes → RSI < 20
        val history = buildDecliningHistory(periods = 15, startPrice = 100.0, dropPerCandle = 2.0)

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    @Test
    fun `given overbought RSI when predict then returns DOWN`() {
        // Series of rising closes → RSI > 80
        val history = buildRisingHistory(periods = 15, startPrice = 100.0, risePerCandle = 2.0)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    // -- Pure momentum fallback (Step 2c) --

    @Test
    fun `given single bullish candle when predict then returns UP`() {
        val history = listOf(marketPrice(open = 100.0, close = 101.0))

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    @Test
    fun `given single bearish candle when predict then returns DOWN`() {
        val history = listOf(marketPrice(open = 101.0, close = 100.0))

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    // -- BTC leading indicator for ETH (Step 3) --

    @Test
    fun `given strong bullish BTC move when predicting ETH then returns UP`() {
        val btcHistory = buildHistoryWithConsistentAtr(
            atrRange = 1.0, lastOpen = 100.0, lastClose = 101.0, symbol = "BTC/USD",
        )
        btcHistory.forEach { priceRepository.store(it) }

        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 50.0, close = 49.0))

        assertEquals(Direction.UP, sut.predict("ETH/USD", ethHistory))
    }

    @Test
    fun `given strong bearish BTC move when predicting ETH then returns DOWN`() {
        val btcHistory = buildHistoryWithConsistentAtr(
            atrRange = 1.0, lastOpen = 101.0, lastClose = 100.0, symbol = "BTC/USD",
        )
        btcHistory.forEach { priceRepository.store(it) }

        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 49.0, close = 50.0))

        assertEquals(Direction.DOWN, sut.predict("ETH/USD", ethHistory))
    }

    @Test
    fun `given weak BTC move when predicting ETH then uses ETH own data`() {
        // Small BTC body → BTC signal not strong enough → fallback to ETH's own tree
        val btcHistory = buildHistoryWithConsistentAtr(
            atrRange = 10.0, lastOpen = 100.0, lastClose = 100.01, symbol = "BTC/USD",
        )
        btcHistory.forEach { priceRepository.store(it) }

        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 50.0, close = 49.0))

        // ETH has bearish candle, no BTC override → pure momentum → DOWN
        assertEquals(Direction.DOWN, sut.predict("ETH/USD", ethHistory))
    }

    @Test
    fun `given no BTC data when predicting ETH then uses ETH own data`() {
        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 49.0, close = 50.0))

        assertEquals(Direction.UP, sut.predict("ETH/USD", ethHistory))
    }

    @Test
    fun `BTC prediction does not use BTC leading indicator`() {
        // Store BTC data in repo — should NOT trigger leading indicator for BTC itself
        val btcHistory = listOf(marketPrice(symbol = "BTC/USD", open = 101.0, close = 100.0))
        btcHistory.forEach { priceRepository.store(it) }

        // Bearish candle → DOWN (not overridden by leading indicator since it's BTC, not ETH)
        assertEquals(Direction.DOWN, sut.predict("BTC/USD", btcHistory))
    }

    // -- Helpers --

    private fun buildTrendingHistory(lastOpen: Double, lastClose: Double): List<MarketPrice> {
        // Volatile candles to trigger TRENDING regime + enough for ATR
        val candles = (1..14).map { i ->
            val swing = if (i % 2 == 0) 3.0 else -3.0
            marketPrice(
                open = 100.0,
                close = 100.0 + swing,
                high = 104.0,
                low = 96.0,
            )
        }
        return candles + marketPrice(open = lastOpen, close = lastClose, high = maxOf(lastOpen, lastClose), low = minOf(lastOpen, lastClose))
    }

    private fun buildNormalHistory(): List<MarketPrice> {
        // Moderate volatility → NORMAL regime, enough candles for ATR
        return (1..14).map { i ->
            val swing = if (i % 2 == 0) 0.5 else -0.5
            marketPrice(open = 100.0, close = 100.0 + swing, high = 101.0, low = 99.0)
        }
    }

    private fun buildHistoryWithConsistentAtr(
        atrRange: Double,
        lastOpen: Double,
        lastClose: Double,
        symbol: String = "BTC/USD",
    ): List<MarketPrice> {
        val candles = (1..14).map {
            marketPrice(
                symbol = symbol,
                open = 100.0,
                close = 100.0,
                high = 100.0 + atrRange / 2,
                low = 100.0 - atrRange / 2,
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

    private fun buildDecliningHistory(periods: Int, startPrice: Double, dropPerCandle: Double): List<MarketPrice> {
        // Closes consistently fall (for RSI < 20) but each candle body is moderate relative to high-low range
        // so ATR step 2a returns null → falls through to RSI step 2b
        return (0 until periods).map { i ->
            val drop = if (i % 2 == 0) dropPerCandle * 1.5 else dropPerCandle * 0.5
            val open = startPrice - i * dropPerCandle
            val close = open - drop
            val range = drop * 3
            marketPrice(open = open, close = close, high = open + range / 2, low = open - range / 2)
        }
    }

    private fun buildRisingHistory(periods: Int, startPrice: Double, risePerCandle: Double): List<MarketPrice> {
        // Closes consistently rise (for RSI > 80) but each candle body is moderate relative to high-low range
        return (0 until periods).map { i ->
            val rise = if (i % 2 == 0) risePerCandle * 1.5 else risePerCandle * 0.5
            val open = startPrice + i * risePerCandle
            val close = open + rise
            val range = rise * 3
            marketPrice(open = open, close = close, high = open + range, low = open - range / 2)
        }
    }
}
