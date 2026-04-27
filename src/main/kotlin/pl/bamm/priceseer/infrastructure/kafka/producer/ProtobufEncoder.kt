package pl.bamm.priceseer.infrastructure.kafka.producer

import com.google.protobuf.CodedOutputStream
import pl.bamm.priceseer.domain.model.Prediction
import java.io.ByteArrayOutputStream

object ProtobufEncoder {

    fun encode(prediction: Prediction): ByteArray {
        val out = ByteArrayOutputStream()
        val coded = CodedOutputStream.newInstance(out)
        coded.writeString(1, prediction.team)
        coded.writeString(2, prediction.symbol)
        coded.writeString(3, prediction.timestamp)
        coded.writeEnum(4, prediction.direction.ordinal)
        coded.flush()
        return out.toByteArray()
    }
}
