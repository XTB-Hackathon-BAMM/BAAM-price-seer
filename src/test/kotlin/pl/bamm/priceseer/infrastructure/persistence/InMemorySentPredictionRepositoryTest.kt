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

    @Test
    fun `given no previous entry when tryMarkSent then returns true`() {
        val result = sut.tryMarkSent("BTC/USD", Instant.parse("2026-04-27T12:00:00Z"), Direction.UP)

        assertTrue(result)
    }

    @Test
    fun `given existing entry when tryMarkSent with same symbol and minute then returns false`() {
        val minute = Instant.parse("2026-04-27T12:01:00Z")
        sut.tryMarkSent("ETH/USD", minute, Direction.UP)

        val result = sut.tryMarkSent("ETH/USD", minute, Direction.DOWN)

        assertFalse(result)
    }

    @Test
    fun `given existing entry when tryMarkSent with different minute then returns true`() {
        sut.tryMarkSent("AAPL", Instant.parse("2026-04-27T12:02:00Z"), Direction.UP)

        val result = sut.tryMarkSent("AAPL", Instant.parse("2026-04-27T12:03:00Z"), Direction.UP)

        assertTrue(result)
    }

    @Test
    fun `given existing entry when tryMarkSent with different symbol then returns true`() {
        sut.tryMarkSent("MSFT", Instant.parse("2026-04-27T12:04:00Z"), Direction.DOWN)

        val result = sut.tryMarkSent("AAPL", Instant.parse("2026-04-27T12:04:00Z"), Direction.DOWN)

        assertTrue(result)
    }
}
