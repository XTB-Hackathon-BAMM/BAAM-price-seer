package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.Direction
import java.time.Instant

/**
 * Repository that tracks which predictions have already been sent,
 * enforcing the one-prediction-per-instrument-per-minute rule.
 */
interface SentPredictionRepository {

    /**
     * Atomically marks a prediction as sent for the given symbol and minute.
     *
     * @param symbol    the instrument symbol
     * @param minute    the truncated-to-minute timestamp
     * @param direction the predicted direction
     * @return {@code true} if this is the first prediction for the (symbol, minute) pair;
     *         {@code false} if a duplicate already exists
     */
    fun tryMarkSent(symbol: String, minute: Instant, direction: Direction): Boolean
}
