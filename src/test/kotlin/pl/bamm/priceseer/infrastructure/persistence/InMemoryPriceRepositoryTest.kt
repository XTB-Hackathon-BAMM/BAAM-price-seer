package pl.bamm.priceseer.infrastructure.persistence

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.bamm.priceseer.fixtures.marketPrice
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryPriceRepositoryTest {

    private lateinit var sut: InMemoryPriceRepository

    @BeforeEach
    fun setUp() {
        sut = InMemoryPriceRepository()
    }

    @Test
    fun `given stored price when history then returns it`() {
        val price = marketPrice("XTB", open = 100.0, close = 110.0)

        sut.store(price)
        val history = sut.history("XTB")

        assertEquals(1, history.size)
        assertEquals("XTB", history.first().symbol)
        assertEquals(100.0, history.first().open)
        assertEquals(110.0, history.first().close)
    }

    @Test
    fun `given multiple prices when history then returns all in insertion order`() {
        val price1 = marketPrice("CDR", timestamp = "2026-04-27T12:00:00Z", open = 1.0, close = 2.0)
        val price2 = marketPrice("CDR", timestamp = "2026-04-27T12:01:00Z", open = 2.0, close = 3.0)

        sut.store(price1)
        sut.store(price2)
        val history = sut.history("CDR")

        assertEquals(2, history.size)
        assertEquals("2026-04-27T12:00:00Z", history[0].timestamp)
        assertEquals("2026-04-27T12:01:00Z", history[1].timestamp)
    }

    @Test
    fun `given stored price when latest then returns most recent`() {
        val older = marketPrice("ETH/USD", timestamp = "2026-04-27T12:00:00Z", open = 1.0, close = 2.0)
        val newer = marketPrice("ETH/USD", timestamp = "2026-04-27T12:01:00Z", open = 2.0, close = 3.0)

        sut.store(older)
        sut.store(newer)

        assertEquals("2026-04-27T12:01:00Z", sut.latest("ETH/USD")?.timestamp)
    }

    @Test
    fun `given no prices when latest then returns null`() {
        assertNull(sut.latest("UNKNOWN"))
    }

    @Test
    fun `given no prices when history then returns empty list`() {
        assertEquals(emptyList(), sut.history("UNKNOWN"))
    }

    @Test
    fun `given prices from different symbols when history then returns only requested symbol`() {
        sut.store(marketPrice("XTB"))
        sut.store(marketPrice("CDR"))

        assertEquals(1, sut.history("XTB").size)
        assertEquals("XTB", sut.history("XTB").first().symbol)
    }

    @Test
    fun `when prices exceed history size then oldest are evicted`() {
        val historySize = 20
        repeat(historySize + 1) { i ->
            sut.store(marketPrice("BTC/USD", timestamp = "2026-04-27T${12 + i / 60}:${String.format("%02d", i % 60)}:00Z"))
        }

        assertEquals(historySize, sut.history("BTC/USD").size)
    }
}
