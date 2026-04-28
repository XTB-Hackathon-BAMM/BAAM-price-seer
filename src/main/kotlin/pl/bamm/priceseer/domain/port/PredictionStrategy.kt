package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice

/**
 * Strategy that determines a directional prediction for a given instrument
 * based on its recent price history.
 */
interface PredictionStrategy {

    /**
     * Predicts the next price direction for the given instrument.
     *
     * @param symbol  the instrument symbol (e.g. {@code "BTC/USD"})
     * @param history recent {@link MarketPrice} candles in ascending timestamp order
     * @return the predicted {@link Direction}
     */
    fun predict(symbol: String, history: List<MarketPrice>): Direction
}
