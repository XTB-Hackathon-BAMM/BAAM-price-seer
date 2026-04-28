package pl.bamm.priceseer.infrastructure.kafka.producer

import com.xtb.adapter.outgoing.protobuf.PredictionProto
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.Prediction

/**
 * Encodes a domain {@link Prediction} into Protobuf bytes for Kafka publishing.
 */
object ProtobufEncoder {

    /**
     * Serializes a {@link Prediction} domain object into Protobuf bytes.
     *
     * @param prediction the prediction to encode
     * @return the Protobuf-encoded byte array
     */
    fun encode(prediction: Prediction): ByteArray =
        PredictionProto.Prediction.newBuilder()
            .setTeam(prediction.team)
            .setSymbol(prediction.symbol)
            .setTimestamp(prediction.timestamp)
            .setPrediction(prediction.direction.toProto())
            .build()
            .toByteArray()

    private fun Direction.toProto(): PredictionProto.Direction = when (this) {
        Direction.UP -> PredictionProto.Direction.UP
        Direction.DOWN -> PredictionProto.Direction.DOWN
    }
}
