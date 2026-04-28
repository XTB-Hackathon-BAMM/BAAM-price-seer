package pl.bamm.priceseer.infrastructure.persistence

import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.port.SentPredictionRepository
import java.time.Instant

/**
 * PostgreSQL-backed implementation of {@link SentPredictionRepository} using Spring
 * {@link JdbcTemplate}. Uses upsert ({@code ON CONFLICT DO NOTHING}) to atomically
 * enforce the one-prediction-per-symbol-per-minute constraint.
 * Active when the {@code in-memory} profile is not set.
 */
@Profile("!in-memory")
@Repository
class JdbcSentPredictionRepository(private val jdbc: JdbcTemplate) : SentPredictionRepository {

    /** {@inheritDoc} */
    override fun tryMarkSent(symbol: String, minute: Instant, direction: Direction): Boolean {
        val rows = jdbc.update(
            """
            INSERT INTO sent_prediction (symbol, minute, direction)
            VALUES (?, ?::timestamptz, ?)
            ON CONFLICT (symbol, minute) DO NOTHING
            """.trimIndent(),
            symbol, minute.toString(), direction.name,
        )
        return rows == 1
    }
}
