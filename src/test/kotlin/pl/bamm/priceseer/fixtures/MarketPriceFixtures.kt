package pl.bamm.priceseer.fixtures

import com.google.protobuf.CodedOutputStream
import pl.bamm.priceseer.domain.model.MarketPrice
import java.io.ByteArrayOutputStream

fun marketPrice(
    symbol: String = "BTC/USD",
    open: Double = 50_000.0,
    close: Double = 51_000.0,
    timestamp: String = "2026-04-27T12:05:00Z",
): MarketPrice = MarketPrice(
    symbol = symbol,
    timestamp = timestamp,
    interval = "1min",
    open = open,
    high = maxOf(open, close),
    low = minOf(open, close),
    close = close,
)

fun marketPriceBytes(
    symbol: String = "BTC/USD",
    open: Double = 50_000.0,
    close: Double = 51_000.0,
    timestamp: String = "2026-04-27T12:05:00Z",
): ByteArray {
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)
    coded.writeString(1, symbol)
    coded.writeString(2, timestamp)
    coded.writeString(3, "1min")
    coded.writeDouble(4, open)
    coded.writeDouble(5, maxOf(open, close))
    coded.writeDouble(6, minOf(open, close))
    coded.writeDouble(7, close)
    coded.flush()
    return out.toByteArray()
}
