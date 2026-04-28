package pl.bamm.priceseer.domain.model

/**
 * OHLC candlestick for a single 1-minute interval of a financial instrument,
 * received from the {@code market-prices} Kafka topic.
 */
data class MarketPrice(
    val symbol: String,
    val timestamp: String,
    val interval: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)
