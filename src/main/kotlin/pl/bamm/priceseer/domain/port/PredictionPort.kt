package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.Prediction

interface PredictionPort {
    fun send(prediction: Prediction)
}
