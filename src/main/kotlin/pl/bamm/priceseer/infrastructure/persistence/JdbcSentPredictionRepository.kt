package pl.bamm.priceseer.infrastructure.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.port.SentPredictionRepository
import java.time.Instant

@Repository
class JdbcSentPredictionRepository(private val jdbc: JdbcTemplate) : SentPredictionRepository {

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
