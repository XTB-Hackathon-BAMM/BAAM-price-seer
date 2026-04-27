package pl.bamm.priceseer.infrastructure.kafka.consumer

import com.xtb.adapter.outgoing.protobuf.MarketPriceProto
import pl.bamm.priceseer.domain.model.MarketPrice

object ProtobufDecoder {

    fun decode(bytes: ByteArray): MarketPrice {
        val proto = MarketPriceProto.MarketPrice.parseFrom(bytes)
        require(proto.symbol.isNotBlank()) { "MarketPrice.symbol is missing or blank" }
        return MarketPrice(
            symbol = proto.symbol,
            timestamp = proto.timestamp,
            interval = proto.interval,
            open = proto.open,
            high = proto.high,
            low = proto.low,
            close = proto.close,
        )
    }
}
