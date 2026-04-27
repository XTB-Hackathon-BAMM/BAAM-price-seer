package pl.bamm.priceseer.application.strategy

import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PredictionStrategy

@Component("momentum")
class MomentumStrategy : PredictionStrategy {

    override fun predict(symbol: String, history: List<MarketPrice>): Direction {
        val last = history.lastOrNull() ?: return Direction.UP
        return if (last.close >= last.open) Direction.UP else Direction.DOWN
    }
}
