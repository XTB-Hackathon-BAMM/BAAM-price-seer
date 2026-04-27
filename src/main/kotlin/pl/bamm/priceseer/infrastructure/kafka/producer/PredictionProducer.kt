package pl.bamm.priceseer.infrastructure.kafka.producer

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Prediction
import pl.bamm.priceseer.domain.port.PredictionPort

@Component
class PredictionProducer(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
) : PredictionPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(prediction: Prediction) {
        val bytes = ProtobufEncoder.encode(prediction)
        kafkaTemplate.send(TOPIC, prediction.symbol, bytes)
            .whenComplete { _, ex ->
                if (ex != null) {
                    log.error(
                        "Failed to send prediction: symbol={} direction={}",
                        prediction.symbol, prediction.direction, ex,
                    )
                }
            }
    }

    companion object {
        private const val TOPIC = "predictions"
    }
}
