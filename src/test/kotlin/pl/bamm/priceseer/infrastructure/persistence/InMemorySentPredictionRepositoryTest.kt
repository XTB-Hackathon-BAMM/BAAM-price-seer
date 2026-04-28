package pl.bamm.priceseer.infrastructure.persistence

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.bamm.priceseer.domain.model.Direction
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemorySentPredictionRepositoryTest {

    private lateinit var sut: InMemorySentPredictionRepository

    @BeforeEach
    fun setUp() {
        sut = InMemorySentPredictionRepository()
    }

    /**
     * Test purpose - Verify that {@link InMemorySentPredictionRepository#tryMarkSent} returns
     * {@code true} when no previous prediction has been recorded for the given symbol and minute.
     *
     * <p>Test data - Symbol {@code "BTC/USD"}, timestamp {@code "2026-04-27T12:00:00Z"},
     * direction {@code Direction.UP}, with an empty repository.
     *
     * <p>Test expected result - {@code true}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given no previous entry when tryMarkSent then returns true`() {
        val result = sut.tryMarkSent("BTC/USD", Instant.parse("2026-04-27T12:00:00Z"), Direction.UP)

        assertTrue(result)
    }

    /**
     * Test purpose - Verify that {@link InMemorySentPredictionRepository#tryMarkSent} returns
     * {@code false} when a prediction for the same symbol and minute already exists, preventing
     * duplicate submissions.
     *
     * <p>Test data - Two calls with symbol {@code "ETH/USD"} and the same minute
     * {@code "2026-04-27T12:01:00Z"}, with different directions.
     *
     * <p>Test expected result - Second call returns {@code false}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given existing entry when tryMarkSent with same symbol and minute then returns false`() {
        val minute = Instant.parse("2026-04-27T12:01:00Z")
        sut.tryMarkSent("ETH/USD", minute, Direction.UP)

        val result = sut.tryMarkSent("ETH/USD", minute, Direction.DOWN)

        assertFalse(result)
    }

    /**
     * Test purpose - Verify that {@link InMemorySentPredictionRepository#tryMarkSent} returns
     * {@code true} when the same symbol is used but at a different minute, allowing one prediction
     * per instrument per minute.
     *
     * <p>Test data - Two calls with symbol {@code "XTB"}, first at {@code "2026-04-27T12:02:00Z"},
     * second at {@code "2026-04-27T12:03:00Z"}.
     *
     * <p>Test expected result - Second call returns {@code true}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given existing entry when tryMarkSent with different minute then returns true`() {
        sut.tryMarkSent("XTB", Instant.parse("2026-04-27T12:02:00Z"), Direction.UP)

        val result = sut.tryMarkSent("XTB", Instant.parse("2026-04-27T12:03:00Z"), Direction.UP)

        assertTrue(result)
    }

    /**
     * Test purpose - Verify that {@link InMemorySentPredictionRepository#tryMarkSent} returns
     * {@code true} when a different symbol is used at the same minute, confirming per-symbol
     * deduplication.
     *
     * <p>Test data - First call with symbol {@code "CDR"} at {@code "2026-04-27T12:04:00Z"},
     * second call with symbol {@code "XTB"} at the same minute.
     *
     * <p>Test expected result - Second call returns {@code true}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given existing entry when tryMarkSent with different symbol then returns true`() {
        sut.tryMarkSent("CDR", Instant.parse("2026-04-27T12:04:00Z"), Direction.DOWN)

        val result = sut.tryMarkSent("XTB", Instant.parse("2026-04-27T12:04:00Z"), Direction.DOWN)

        assertTrue(result)
    }
}
