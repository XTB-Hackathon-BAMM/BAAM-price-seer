package pl.bamm.priceseer.infrastructure.kafka.producer

import com.xtb.adapter.outgoing.protobuf.PredictionProto
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.Prediction

object ProtobufEncoder {

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
