package pl.bamm.priceseer.application.strategy

import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PredictionStrategy

/**
 * Fallback prediction strategy that follows the direction of the most recent candle.
 * Returns {@link Direction#UP} if the last close is greater than or equal to the open,
 * {@link Direction#DOWN} otherwise.
 */
@Component("momentum")
class MomentumStrategy : PredictionStrategy {

    /** {@inheritDoc} */
    override fun predict(symbol: String, history: List<MarketPrice>): Direction {
        val last = history.lastOrNull() ?: return Direction.UP
        return if (last.close >= last.open) Direction.UP else Direction.DOWN
    }
}
