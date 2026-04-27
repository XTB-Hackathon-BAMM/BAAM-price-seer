package pl.bamm.priceseer.application.strategy

import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.fixtures.marketPrice
import kotlin.test.Test
import kotlin.test.assertEquals

class MomentumStrategyTest {

    private val sut = MomentumStrategy()

    @Test
    fun `given close greater than open when predict then returns UP`() {
        val history = listOf(marketPrice(open = 1.0, close = 1.1))

        val result = sut.predict("AAPL", history)

        assertEquals(Direction.UP, result)
    }

    @Test
    fun `given close less than open when predict then returns DOWN`() {
        val history = listOf(marketPrice(open = 1.1, close = 1.0))

        val result = sut.predict("AAPL", history)

        assertEquals(Direction.DOWN, result)
    }

    @Test
    fun `given close equal to open when predict then returns UP`() {
        val history = listOf(marketPrice(open = 1.0, close = 1.0))

        val result = sut.predict("AAPL", history)

        assertEquals(Direction.UP, result)
    }

    @Test
    fun `given empty history when predict then returns UP as safe default`() {
        val result = sut.predict("AAPL", emptyList())

        assertEquals(Direction.UP, result)
    }

    @Test
    fun `given multiple candles when predict then uses only last candle`() {
        val history = listOf(
            marketPrice(open = 1.0, close = 2.0),  // UP
            marketPrice(open = 2.0, close = 1.5),  // DOWN — this is last
        )

        val result = sut.predict("AAPL", history)

        assertEquals(Direction.DOWN, result)
    }
}
