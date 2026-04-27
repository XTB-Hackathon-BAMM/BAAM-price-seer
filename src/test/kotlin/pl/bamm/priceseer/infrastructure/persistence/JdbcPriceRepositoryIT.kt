package pl.bamm.priceseer.infrastructure.persistence

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.bamm.priceseer.fixtures.TestDatabase
import pl.bamm.priceseer.fixtures.marketPrice
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JdbcPriceRepositoryIT {

    private lateinit var sut: JdbcPriceRepository

    @BeforeEach
    fun setUp() {
        TestDatabase.resetSchema()
        sut = JdbcPriceRepository(TestDatabase.newJdbcTemplate(), 120)
    }

    @Test
    fun `given stored price when history then returns it`() {
        val price = marketPrice("AAPL", open = 100.0, close = 110.0)

        sut.store(price)
        val history = sut.history("AAPL")

        assertEquals(1, history.size)
        assertEquals("AAPL", history.first().symbol)
        assertEquals(100.0, history.first().open)
        assertEquals(110.0, history.first().close)
    }

    @Test
    fun `given multiple prices when history then returns all ordered ascending`() {
        val price1 = marketPrice("MSFT", timestamp = "2026-04-27T12:00:00Z", open = 1.0, close = 2.0)
        val price2 = marketPrice("MSFT", timestamp = "2026-04-27T12:01:00Z", open = 2.0, close = 3.0)

        sut.store(price1)
        sut.store(price2)
        val history = sut.history("MSFT")

        assertEquals(2, history.size)
        assertEquals("2026-04-27T12:00:00Z", history[0].timestamp)
        assertEquals("2026-04-27T12:01:00Z", history[1].timestamp)
    }

    @Test
    fun `given duplicate price when store then upserts without error`() {
        val price = marketPrice("BTC/USD")

        sut.store(price)
        sut.store(price)

        assertEquals(1, sut.history("BTC/USD").size)
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
}
