package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.Direction
import java.time.Instant

interface SentPredictionRepository {
    fun tryMarkSent(symbol: String, minute: Instant, direction: Direction): Boolean
}
