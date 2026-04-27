package pl.bamm.priceseer.infrastructure.kafka.consumer

import com.google.protobuf.CodedInputStream
import pl.bamm.priceseer.domain.model.MarketPrice

object ProtobufDecoder {

    // tag = (fieldNumber shl 3) or wireType   — string=2 (length-delimited), double=1 (fixed64)
    private const val TAG_SYMBOL = (1 shl 3) or 2    // 10
    private const val TAG_TIMESTAMP = (2 shl 3) or 2  // 18
    private const val TAG_INTERVAL = (3 shl 3) or 2   // 26
    private const val TAG_OPEN = (4 shl 3) or 1        // 33
    private const val TAG_HIGH = (5 shl 3) or 1        // 41
    private const val TAG_LOW = (6 shl 3) or 1         // 49
    private const val TAG_CLOSE = (7 shl 3) or 1       // 57

    fun decode(bytes: ByteArray): MarketPrice {
        val input = CodedInputStream.newInstance(bytes)
        var symbol = ""
        var timestamp = ""
        var interval = ""
        var open = 0.0
        var high = 0.0
        var low = 0.0
        var close = 0.0

        while (!input.isAtEnd) {
            when (val tag = input.readTag()) {
                TAG_SYMBOL -> symbol = input.readString()
                TAG_TIMESTAMP -> timestamp = input.readString()
                TAG_INTERVAL -> interval = input.readString()
                TAG_OPEN -> open = input.readDouble()
                TAG_HIGH -> high = input.readDouble()
                TAG_LOW -> low = input.readDouble()
                TAG_CLOSE -> close = input.readDouble()
                0 -> break
                else -> input.skipField(tag)
            }
        }

        require(symbol.isNotBlank()) { "MarketPrice.symbol is missing or blank" }
        return MarketPrice(
            symbol = symbol,
            timestamp = timestamp,
            interval = interval,
            open = open,
            high = high,
            low = low,
            close = close,
        )
    }
}
