package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.MarketPrice

/**
 * Repository for persisting and retrieving {@link MarketPrice} candles.
 */
interface PriceRepository {

    /**
     * Persists a single market price candle. Duplicate (symbol, timestamp)
     * pairs are silently ignored.
     *
     * @param price the candle to store
     */
    fun store(price: MarketPrice)

    /**
     * Returns the most recent candles for the given symbol in ascending
     * timestamp order, up to the configured history size.
     *
     * @param symbol the instrument symbol
     * @return ordered list of candles, or empty if none exist
     */
    fun history(symbol: String): List<MarketPrice>

    /**
     * Returns the single most recent candle for the given symbol.
     *
     * @param symbol the instrument symbol
     * @return the latest candle, or {@code null} if none exist
     */
    fun latest(symbol: String): MarketPrice?
}
