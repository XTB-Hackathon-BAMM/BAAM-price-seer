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

    /**
     * Test purpose - Verify that {@link JdbcPriceRepository#history} returns the previously
     * stored {@link MarketPrice} with correct symbol, open, and close values from the database.
     *
     * <p>Test data - Single {@link MarketPrice} for symbol {@code "XTB"} with {@code open=100.0}
     * and {@code close=110.0}.
     *
     * <p>Test expected result - History contains exactly one entry matching the stored price.
     *
     * <p>Test type - Positive.
     */
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

    /**
     * Test purpose - Verify that {@link JdbcPriceRepository#history} returns all stored prices
     * ordered by timestamp ascending when multiple prices exist for the same symbol.
     *
     * <p>Test data - Two {@link MarketPrice} entries for symbol {@code "CDR"} with timestamps
     * {@code "2026-04-27T12:00:00Z"} and {@code "2026-04-27T12:01:00Z"}.
     *
     * <p>Test expected result - History contains two entries ordered by timestamp ascending.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given multiple prices when history then returns all ordered ascending`() {
        val price1 = marketPrice("CDR", timestamp = "2026-04-27T12:00:00Z", open = 1.0, close = 2.0)
        val price2 = marketPrice("CDR", timestamp = "2026-04-27T12:01:00Z", open = 2.0, close = 3.0)

        sut.store(price1)
        sut.store(price2)
        val history = sut.history("CDR")

        assertEquals(2, history.size)
        assertEquals("2026-04-27T12:00:00Z", history[0].timestamp)
        assertEquals("2026-04-27T12:01:00Z", history[1].timestamp)
    }

    /**
     * Test purpose - Verify that {@link JdbcPriceRepository#store} performs an upsert when
     * a duplicate price (same symbol and timestamp) is stored, avoiding constraint violations.
     *
     * <p>Test data - Same {@link MarketPrice} for symbol {@code "BTC/USD"} stored twice.
     *
     * <p>Test expected result - History contains exactly one entry (no duplicate).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given duplicate price when store then upserts without error`() {
        val price = marketPrice("BTC/USD")

        sut.store(price)
        sut.store(price)

        assertEquals(1, sut.history("BTC/USD").size)
    }

    /**
     * Test purpose - Verify that {@link JdbcPriceRepository#latest} returns the most recently
     * stored {@link MarketPrice} by timestamp for a given symbol.
     *
     * <p>Test data - Two {@link MarketPrice} entries for symbol {@code "ETH/USD"} with timestamps
     * {@code "2026-04-27T12:00:00Z"} and {@code "2026-04-27T12:01:00Z"}.
     *
     * <p>Test expected result - {@code latest} returns the entry with timestamp
     * {@code "2026-04-27T12:01:00Z"}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given stored price when latest then returns most recent`() {
        val older = marketPrice("ETH/USD", timestamp = "2026-04-27T12:00:00Z", open = 1.0, close = 2.0)
        val newer = marketPrice("ETH/USD", timestamp = "2026-04-27T12:01:00Z", open = 2.0, close = 3.0)

        sut.store(older)
        sut.store(newer)

        assertEquals("2026-04-27T12:01:00Z", sut.latest("ETH/USD")?.timestamp)
    }

    /**
     * Test purpose - Verify that {@link JdbcPriceRepository#latest} returns {@code null}
     * when no prices have been stored for the requested symbol.
     *
     * <p>Test data - Empty database, queried with symbol {@code "UNKNOWN"}.
     *
     * <p>Test expected result - {@code null}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given no prices when latest then returns null`() {
        assertNull(sut.latest("UNKNOWN"))
    }
}
