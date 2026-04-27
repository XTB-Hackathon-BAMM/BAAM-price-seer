package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice

interface PredictionStrategy {
    fun predict(symbol: String, history: List<MarketPrice>): Direction
}
