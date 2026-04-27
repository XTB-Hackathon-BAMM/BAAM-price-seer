package pl.bamm.priceseer.domain.model

data class MarketPrice(
    val symbol: String,
    val timestamp: String,
    val interval: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)
