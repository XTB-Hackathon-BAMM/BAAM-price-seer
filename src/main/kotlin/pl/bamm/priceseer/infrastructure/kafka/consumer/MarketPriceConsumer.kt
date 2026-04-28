package pl.bamm.priceseer.infrastructure.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import pl.bamm.priceseer.application.PredictionApplicationService

/**
 * Kafka consumer that listens to the {@code market-prices} topic, deserializes
 * Protobuf-encoded candles, and delegates them to the application service.
 */
@Component
class MarketPriceConsumer(
    private val predictionApplicationService: PredictionApplicationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Processes a single Kafka record from the {@code market-prices} topic.
     * Malformed Protobuf bytes are logged and silently dropped.
     *
     * @param record the raw Kafka consumer record
     */
    @KafkaListener(topics = ["market-prices"], groupId = "priceseer-consumer")
    fun consume(record: ConsumerRecord<String, ByteArray>) {
        runCatching { ProtobufDecoder.decode(record.value()) }
            .onSuccess { price ->
                log.info("Received: symbol={} close={} ts={}", price.symbol, price.close, price.timestamp)
                predictionApplicationService.onPriceReceived(price)
            }
            .onFailure {
                log.error("Failed to decode MarketPrice for key={}", record.key(), it)
            }
    }
}
