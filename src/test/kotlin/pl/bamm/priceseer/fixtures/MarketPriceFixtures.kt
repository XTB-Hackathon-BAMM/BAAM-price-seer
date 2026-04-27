package pl.bamm.priceseer.fixtures

import com.xtb.adapter.outgoing.protobuf.MarketPriceProto
import pl.bamm.priceseer.domain.model.MarketPrice

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
): ByteArray =
    MarketPriceProto.MarketPrice.newBuilder()
        .setSymbol(symbol)
        .setTimestamp(timestamp)
        .setInterval("1min")
        .setOpen(open)
        .setHigh(maxOf(open, close))
        .setLow(minOf(open, close))
        .setClose(close)
        .build()
        .toByteArray()
