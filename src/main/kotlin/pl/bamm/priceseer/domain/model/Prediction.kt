package pl.bamm.priceseer.domain.model

/**
 * Directional prediction submitted to the Oracle scoring server
 * via the {@code predictions} Kafka topic.
 */
data class Prediction(
    val team: String,
    val symbol: String,
    val timestamp: String,
    val direction: Direction,
)
