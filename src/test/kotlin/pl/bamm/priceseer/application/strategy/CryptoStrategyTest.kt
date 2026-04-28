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

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} returns {@code Direction.UP}
     * as a safe default when no price history is available.
     *
     * <p>Test data - Empty list of {@link MarketPrice} for symbol {@code "BTC/USD"}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given empty history when predict then returns UP`() {
        assertEquals(Direction.UP, sut.predict("BTC/USD", emptyList()))
    }

    // -- Regime detection (Step 1) --

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} detects a QUIET regime
     * (near-zero volatility) and returns {@code Direction.UP}.
     *
     * <p>Test data - 5 candles with nearly identical open/close ({@code open=100.0},
     * {@code close=100.001}), producing standard deviation of returns close to zero.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given quiet regime when predict then returns UP`() {
        // All candles with nearly identical open/close → std of returns ≈ 0
        val history = (1..5).map { marketPrice(open = 100.0, close = 100.001) }

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} detects a TRENDING regime
     * (high volatility) and follows pure momentum with a strong bearish last candle.
     *
     * <p>Test data - 15 volatile candles triggering TRENDING regime, with the last candle
     * having {@code open=100.0} and {@code close=95.0} (strong bearish).
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given trending regime with strong bearish candle when predict then returns DOWN`() {
        // High volatility returns + strong bearish last candle → momentum DOWN
        val history = buildTrendingHistory(lastOpen = 100.0, lastClose = 95.0)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    // -- Doji filter --

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} detects a doji candle
     * (tiny body relative to shadow) and overrides the prediction to {@code Direction.UP}.
     *
     * <p>Test data - Normal history followed by a doji candle with {@code open=100.0},
     * {@code close=100.01}, {@code high=101.0}, {@code low=99.0}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given doji candle when predict then overrides to UP`() {
        // Candle with tiny body relative to shadow → doji
        val doji = marketPrice(open = 100.0, close = 100.01, high = 101.0, low = 99.0)
        val history = buildNormalHistory() + doji

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    // -- ATR-filtered momentum (Step 2a) --

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} returns {@code Direction.UP}
     * when the last candle has a strong bullish body relative to ATR.
     *
     * <p>Test data - 15 candles with consistent ATR of {@code 1.0}, last candle with
     * {@code open=100.0} and {@code close=101.0} (body exceeds ATR threshold).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given strong bullish body relative to ATR when predict then returns UP`() {
        val history = buildHistoryWithConsistentAtr(atrRange = 1.0, lastOpen = 100.0, lastClose = 101.0)

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} returns {@code Direction.DOWN}
     * when the last candle has a strong bearish body relative to ATR.
     *
     * <p>Test data - 15 candles with consistent ATR of {@code 1.0}, last candle with
     * {@code open=101.0} and {@code close=100.0} (body exceeds ATR threshold).
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given strong bearish body relative to ATR when predict then returns DOWN`() {
        val history = buildHistoryWithConsistentAtr(atrRange = 1.0, lastOpen = 101.0, lastClose = 100.0)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} returns {@code Direction.UP}
     * when the last candle's body is weak relative to ATR, treating it as noise.
     *
     * <p>Test data - 15 candles with consistent ATR of {@code 10.0}, last candle with
     * {@code open=100.0} and {@code close=100.1} (body less than ATR * 0.3).
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given weak body relative to ATR when predict then returns UP`() {
        // body < ATR * 0.3 → noise → UP
        val history = buildHistoryWithConsistentAtr(atrRange = 10.0, lastOpen = 100.0, lastClose = 100.1)

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    // -- RSI (Step 2b) — only in NORMAL regime --

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} returns {@code Direction.UP}
     * when RSI indicates oversold conditions in a NORMAL volatility regime.
     *
     * <p>Test data - 15 declining candles with {@code startPrice=100.0} and
     * {@code dropPerCandle=0.15}, producing RSI below the oversold threshold.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given oversold RSI in NORMAL regime when predict then returns UP`() {
        val history = buildDecliningHistory(periods = 15, startPrice = 100.0, dropPerCandle = 0.15)

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} returns {@code Direction.DOWN}
     * when RSI indicates overbought conditions in a NORMAL volatility regime.
     *
     * <p>Test data - 15 rising candles with {@code startPrice=100.0} and
     * {@code risePerCandle=0.15}, producing RSI above the overbought threshold.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given overbought RSI in NORMAL regime when predict then returns DOWN`() {
        val history = buildRisingHistory(periods = 15, startPrice = 100.0, risePerCandle = 0.15)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} skips RSI in a TRENDING regime
     * and follows pure momentum instead, even when RSI is oversold.
     *
     * <p>Test data - Volatile declining history triggering TRENDING regime with RSI below 20
     * and a bearish last candle ({@code open=100.0}, {@code close=95.0}).
     *
     * <p>Test expected result - {@code Direction.DOWN} (momentum, not RSI reversal).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given oversold RSI in TRENDING regime when predict then follows momentum instead`() {
        // Volatile declining history → TRENDING + RSI < 20 + bearish last candle
        // RSI should be skipped → pure momentum → DOWN (not UP)
        val history = buildTrendingHistory(lastOpen = 100.0, lastClose = 95.0)

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    // -- Pure momentum fallback (Step 2c) --

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} uses pure momentum fallback
     * and returns {@code Direction.UP} for a single bullish candle.
     *
     * <p>Test data - Single {@link MarketPrice} with {@code open=100.0} and {@code close=101.0}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given single bullish candle when predict then returns UP`() {
        val history = listOf(marketPrice(open = 100.0, close = 101.0))

        assertEquals(Direction.UP, sut.predict("BTC/USD", history))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} uses pure momentum fallback
     * and returns {@code Direction.DOWN} for a single bearish candle.
     *
     * <p>Test data - Single {@link MarketPrice} with {@code open=101.0} and {@code close=100.0}.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given single bearish candle when predict then returns DOWN`() {
        val history = listOf(marketPrice(open = 101.0, close = 100.0))

        assertEquals(Direction.DOWN, sut.predict("BTC/USD", history))
    }

    // -- BTC leading indicator for ETH (Step 3) --

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} uses the BTC leading indicator
     * to override ETH's own signal when BTC has a strong bullish move.
     *
     * <p>Test data - BTC history with consistent ATR of {@code 1.0} and strong bullish last candle
     * ({@code open=100.0}, {@code close=101.0}) stored in the repository. ETH history with a
     * bearish candle ({@code open=50.0}, {@code close=49.0}).
     *
     * <p>Test expected result - {@code Direction.UP} (BTC bullish overrides ETH bearish).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given strong bullish BTC move when predicting ETH then returns UP`() {
        val btcHistory = buildHistoryWithConsistentAtr(
            atrRange = 1.0, lastOpen = 100.0, lastClose = 101.0, symbol = "BTC/USD",
        )
        btcHistory.forEach { priceRepository.store(it) }

        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 50.0, close = 49.0))

        assertEquals(Direction.UP, sut.predict("ETH/USD", ethHistory))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} uses the BTC leading indicator
     * to override ETH's own signal when BTC has a strong bearish move.
     *
     * <p>Test data - BTC history with consistent ATR of {@code 1.0} and strong bearish last candle
     * ({@code open=101.0}, {@code close=100.0}) stored in the repository. ETH history with a
     * bullish candle ({@code open=49.0}, {@code close=50.0}).
     *
     * <p>Test expected result - {@code Direction.DOWN} (BTC bearish overrides ETH bullish).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given strong bearish BTC move when predicting ETH then returns DOWN`() {
        val btcHistory = buildHistoryWithConsistentAtr(
            atrRange = 1.0, lastOpen = 101.0, lastClose = 100.0, symbol = "BTC/USD",
        )
        btcHistory.forEach { priceRepository.store(it) }

        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 49.0, close = 50.0))

        assertEquals(Direction.DOWN, sut.predict("ETH/USD", ethHistory))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} falls back to ETH's own data
     * when BTC's move is too weak to trigger the leading indicator.
     *
     * <p>Test data - BTC history with large ATR of {@code 10.0} and tiny body
     * ({@code open=100.0}, {@code close=100.01}). ETH history with a bearish candle
     * ({@code open=50.0}, {@code close=49.0}).
     *
     * <p>Test expected result - {@code Direction.DOWN} (ETH's own bearish momentum).
     *
     * <p>Test type - Positive.
     */
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

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} uses ETH's own data when
     * no BTC price history is available in the repository.
     *
     * <p>Test data - No BTC data in repository. ETH history with a bullish candle
     * ({@code open=49.0}, {@code close=50.0}).
     *
     * <p>Test expected result - {@code Direction.UP} (ETH's own bullish momentum).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given no BTC data when predicting ETH then uses ETH own data`() {
        val ethHistory = listOf(marketPrice(symbol = "ETH/USD", open = 49.0, close = 50.0))

        assertEquals(Direction.UP, sut.predict("ETH/USD", ethHistory))
    }

    /**
     * Test purpose - Verify that {@link CryptoStrategy#predict} does not apply the BTC leading
     * indicator when predicting BTC itself — the indicator is only for ETH.
     *
     * <p>Test data - BTC data stored in repository with a bearish candle ({@code open=101.0},
     * {@code close=100.0}), predicting for {@code "BTC/USD"}.
     *
     * <p>Test expected result - {@code Direction.DOWN} (pure momentum, no self-referencing).
     *
     * <p>Test type - Positive.
     */
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
        return (0 until periods).map { i ->
            val drop = if (i % 2 == 0) dropPerCandle * 1.5 else dropPerCandle * 0.5
            val open = startPrice - i * dropPerCandle
            val close = open - drop
            val range = drop * 2
            marketPrice(open = open, close = close, high = open + range, low = open - range)
        }
    }

    private fun buildRisingHistory(periods: Int, startPrice: Double, risePerCandle: Double): List<MarketPrice> {
        return (0 until periods).map { i ->
            val rise = if (i % 2 == 0) risePerCandle * 1.5 else risePerCandle * 0.5
            val open = startPrice + i * risePerCandle
            val close = open + rise
            val range = rise * 2
            marketPrice(open = open, close = close, high = open + range, low = open - range)
        }
    }
}
