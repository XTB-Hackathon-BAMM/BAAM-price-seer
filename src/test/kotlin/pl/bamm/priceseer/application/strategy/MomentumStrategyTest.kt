package pl.bamm.priceseer.application.strategy

import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.fixtures.marketPrice
import kotlin.test.Test
import kotlin.test.assertEquals

class MomentumStrategyTest {

    private val sut = MomentumStrategy()

    /**
     * Test purpose - Verify that {@link MomentumStrategy#predict} returns {@code Direction.UP}
     * when the last candle's close is greater than its open.
     *
     * <p>Test data - Single {@link MarketPrice} with {@code open=1.0} and {@code close=1.1}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given close greater than open when predict then returns UP`() {
        val history = listOf(marketPrice(open = 1.0, close = 1.1))

        val result = sut.predict("XTB", history)

        assertEquals(Direction.UP, result)
    }

    /**
     * Test purpose - Verify that {@link MomentumStrategy#predict} returns {@code Direction.DOWN}
     * when the last candle's close is less than its open.
     *
     * <p>Test data - Single {@link MarketPrice} with {@code open=1.1} and {@code close=1.0}.
     *
     * <p>Test expected result - {@code Direction.DOWN}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given close less than open when predict then returns DOWN`() {
        val history = listOf(marketPrice(open = 1.1, close = 1.0))

        val result = sut.predict("XTB", history)

        assertEquals(Direction.DOWN, result)
    }

    /**
     * Test purpose - Verify that {@link MomentumStrategy#predict} returns {@code Direction.UP}
     * when the last candle's close equals its open (tie-break rule).
     *
     * <p>Test data - Single {@link MarketPrice} with {@code open=1.0} and {@code close=1.0}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given close equal to open when predict then returns UP`() {
        val history = listOf(marketPrice(open = 1.0, close = 1.0))

        val result = sut.predict("XTB", history)

        assertEquals(Direction.UP, result)
    }

    /**
     * Test purpose - Verify that {@link MomentumStrategy#predict} returns {@code Direction.UP}
     * as a safe default when no price history is available.
     *
     * <p>Test data - Empty list of {@link MarketPrice}.
     *
     * <p>Test expected result - {@code Direction.UP}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given empty history when predict then returns UP as safe default`() {
        val result = sut.predict("XTB", emptyList())

        assertEquals(Direction.UP, result)
    }

    /**
     * Test purpose - Verify that {@link MomentumStrategy#predict} uses only the last candle
     * in the history list, ignoring earlier candles.
     *
     * <p>Test data - Two {@link MarketPrice} candles: first bullish (UP), second bearish (DOWN).
     *
     * <p>Test expected result - {@code Direction.DOWN} (based on the last candle only).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given multiple candles when predict then uses only last candle`() {
        val history = listOf(
            marketPrice(open = 1.0, close = 2.0),  // UP
            marketPrice(open = 2.0, close = 1.5),  // DOWN — this is last
        )

        val result = sut.predict("XTB", history)

        assertEquals(Direction.DOWN, result)
    }
}
