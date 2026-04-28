package pl.bamm.priceseer.infrastructure.kafka.consumer

import com.xtb.adapter.outgoing.protobuf.MarketPriceProto
import pl.bamm.priceseer.domain.model.MarketPrice

/**
 * Decodes Protobuf-encoded bytes into a domain {@link MarketPrice}.
 */
object ProtobufDecoder {

    /**
     * Parses Protobuf bytes into a {@link MarketPrice} domain object.
     *
     * @param bytes the Protobuf-encoded market price
     * @return the decoded domain model
     * @throws IllegalArgumentException if the symbol field is missing or blank
     */
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
