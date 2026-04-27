package pl.bamm.priceseer.domain.model

data class Prediction(
    val team: String,
    val symbol: String,
    val timestamp: String,
    val direction: Direction,
)
