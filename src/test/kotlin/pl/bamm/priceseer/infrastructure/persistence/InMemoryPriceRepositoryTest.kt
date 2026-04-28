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

    /**
     * Test purpose - Verify that {@link InMemoryPriceRepository#history} returns the previously
     * stored {@link MarketPrice} with correct symbol, open, and close values.
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
     * Test purpose - Verify that {@link InMemoryPriceRepository#history} returns all stored
     * prices in insertion order when multiple prices exist for the same symbol.
     *
     * <p>Test data - Two {@link MarketPrice} entries for symbol {@code "CDR"} with timestamps
     * {@code "2026-04-27T12:00:00Z"} and {@code "2026-04-27T12:01:00Z"}.
     *
     * <p>Test expected result - History contains two entries in chronological insertion order.
     *
     * <p>Test type - Positive.
     */
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

    /**
     * Test purpose - Verify that {@link InMemoryPriceRepository#latest} returns the most recently
     * stored {@link MarketPrice} for a given symbol.
     *
     * <p>Test data - Two {@link MarketPrice} entries for symbol {@code "ETH/USD"} with different
     * timestamps.
     *
     * <p>Test expected result - {@code latest} returns the entry with the newer timestamp.
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
     * Test purpose - Verify that {@link InMemoryPriceRepository#latest} returns {@code null}
     * when no prices have been stored for the requested symbol.
     *
     * <p>Test data - Empty repository, queried with symbol {@code "UNKNOWN"}.
     *
     * <p>Test expected result - {@code null}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given no prices when latest then returns null`() {
        assertNull(sut.latest("UNKNOWN"))
    }

    /**
     * Test purpose - Verify that {@link InMemoryPriceRepository#history} returns an empty list
     * when no prices have been stored for the requested symbol.
     *
     * <p>Test data - Empty repository, queried with symbol {@code "UNKNOWN"}.
     *
     * <p>Test expected result - Empty list.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given no prices when history then returns empty list`() {
        assertEquals(emptyList(), sut.history("UNKNOWN"))
    }

    /**
     * Test purpose - Verify that {@link InMemoryPriceRepository#history} returns only prices
     * matching the requested symbol, isolating data between different instruments.
     *
     * <p>Test data - One {@link MarketPrice} for {@code "XTB"} and one for {@code "CDR"}.
     *
     * <p>Test expected result - History for {@code "XTB"} contains exactly one entry with
     * symbol {@code "XTB"}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given prices from different symbols when history then returns only requested symbol`() {
        sut.store(marketPrice("XTB"))
        sut.store(marketPrice("CDR"))

        assertEquals(1, sut.history("XTB").size)
        assertEquals("XTB", sut.history("XTB").first().symbol)
    }

    /**
     * Test purpose - Verify that {@link InMemoryPriceRepository} evicts the oldest prices when
     * the number of stored prices exceeds the configured history size limit.
     *
     * <p>Test data - 21 {@link MarketPrice} entries for symbol {@code "BTC/USD"} stored into a
     * repository with a history size of 20.
     *
     * <p>Test expected result - History contains exactly 20 entries (the oldest one is evicted).
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `when prices exceed history size then oldest are evicted`() {
        val historySize = 20
        repeat(historySize + 1) { i ->
            sut.store(marketPrice("BTC/USD", timestamp = "2026-04-27T${12 + i / 60}:${String.format("%02d", i % 60)}:00Z"))
        }

        assertEquals(historySize, sut.history("BTC/USD").size)
    }
}
