package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.Prediction

/**
 * Outbound port for publishing {@link Prediction}s to the scoring infrastructure.
 */
interface PredictionPort {

    /**
     * Sends a prediction to the {@code predictions} Kafka topic.
     *
     * @param prediction the directional prediction to publish
     */
    fun send(prediction: Prediction)
}
