package pl.bamm.priceseer.application.strategy

import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PredictionStrategy
import pl.bamm.priceseer.domain.port.PriceRepository
import java.time.Clock
import java.time.ZoneOffset
import kotlin.math.abs

@Component("stock")
class StockStrategy(
    private val priceRepository: PriceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : PredictionStrategy {

    override fun predict(symbol: String, history: List<MarketPrice>): Direction {
        if (history.isEmpty()) return Direction.UP

        val last = history.last()
        val atr = calculateAtr(history)

        if (isFlat(last, atr)) return Direction.UP

        if (!isSessionActive()) return Direction.UP

        val utcMinutes = utcMinutesOfDay()

        return when {
            utcMinutes in OPENING_START..OPENING_END -> openingGap(last, atr)
            utcMinutes in LUNCH_START..LUNCH_END -> lunchMeanReversion(history)
            utcMinutes in CLOSING_START..CLOSING_END -> closingMomentum(history)
            utcMinutes in MORNING_START..MORNING_END -> morningSession(symbol, history, last, atr)
            else -> Direction.UP
        }
    }

    private fun isFlat(last: MarketPrice, atr: Double): Boolean {
        if (atr < FLAT_ATR_THRESHOLD) return true
        val body = abs(last.close - last.open)
        return body < atr * FLAT_BODY_THRESHOLD
    }

    private fun isSessionActive(): Boolean {
        val minutes = utcMinutesOfDay()
        return minutes in SESSION_OPEN..SESSION_CLOSE
    }

    private fun utcMinutesOfDay(): Int {
        val time = clock.instant().atZone(ZoneOffset.UTC).toLocalTime()
        return time.hour * 60 + time.minute
    }

    private fun openingGap(last: MarketPrice, atr: Double): Direction {
        if (atr <= 0) return Direction.UP
        val body = abs(last.close - last.open)
        return if (body > atr * OPENING_GAP_THRESHOLD) {
            pureMomentum(last)
        } else {
            Direction.UP
        }
    }

    private fun morningSession(
        symbol: String,
        history: List<MarketPrice>,
        last: MarketPrice,
        atr: Double,
    ): Direction {
        crossSignal(symbol)?.let { return it }

        rsiSignal(history)?.let { return it }

        return atrFilteredMomentum(last, atr, history)
    }

    private fun crossSignal(symbol: String): Direction? {
        val otherSymbol = when (symbol) {
            "XTB" -> "CDR"
            "CDR" -> "XTB"
            else -> return null
        }

        val otherHistory = priceRepository.history(otherSymbol)
        if (otherHistory.size < ATR_PERIOD) return null

        val otherLast = otherHistory.last()
        val otherAtr = calculateAtr(otherHistory)
        if (otherAtr <= 0) return null

        val otherBody = abs(otherLast.close - otherLast.open)
        if (otherBody <= otherAtr * CROSS_SIGNAL_THRESHOLD) return null

        return pureMomentum(otherLast)
    }

    private fun rsiSignal(history: List<MarketPrice>): Direction? {
        if (history.size < RSI_PERIOD + 1) return null
        val rsi = calculateRsi(history)

        return when {
            rsi < RSI_OVERSOLD -> Direction.UP
            rsi > RSI_OVERBOUGHT -> Direction.DOWN
            else -> null
        }
    }

    private fun atrFilteredMomentum(
        last: MarketPrice,
        atr: Double,
        history: List<MarketPrice>,
    ): Direction {
        if (atr <= 0) return pureMomentum(last)

        val body = abs(last.close - last.open)

        return when {
            body > atr * ATR_STRONG -> pureMomentum(last)
            body < atr * ATR_WEAK -> Direction.UP
            else -> smaTrendFilter(history, last)
        }
    }

    private fun lunchMeanReversion(history: List<MarketPrice>): Direction {
        if (history.size < LUNCH_STREAK) return Direction.UP

        val recent = history.takeLast(LUNCH_STREAK)
        val allUp = recent.all { it.close >= it.open }
        val allDown = recent.all { it.close < it.open }

        return when {
            allUp -> Direction.DOWN
            allDown -> Direction.UP
            else -> Direction.UP
        }
    }

    private fun closingMomentum(history: List<MarketPrice>): Direction {
        return smaTrendFilter(history, history.last())
    }

    private fun smaTrendFilter(history: List<MarketPrice>, last: MarketPrice): Direction {
        if (history.size < SMA_LONG_PERIOD) return pureMomentum(last)

        val smaShort = sma(history, SMA_SHORT_PERIOD)
        val smaLong = sma(history, SMA_LONG_PERIOD)

        return when {
            smaShort > smaLong -> Direction.UP
            smaShort < smaLong -> Direction.DOWN
            else -> pureMomentum(last)
        }
    }

    private fun pureMomentum(candle: MarketPrice): Direction =
        if (candle.close >= candle.open) Direction.UP else Direction.DOWN

    private fun sma(history: List<MarketPrice>, period: Int): Double {
        if (history.size < period) return 0.0
        return history.takeLast(period).map { it.close }.average()
    }

    private fun calculateAtr(history: List<MarketPrice>): Double {
        val candles = history.takeLast(ATR_PERIOD + 1)
        if (candles.size < 2) return 0.0

        val trueRanges = (1 until candles.size).map { i ->
            val current = candles[i]
            val prevClose = candles[i - 1].close
            maxOf(
                current.high - current.low,
                abs(current.high - prevClose),
                abs(current.low - prevClose),
            )
        }
        return trueRanges.average()
    }

    private fun calculateRsi(history: List<MarketPrice>): Double {
        val closes = history.takeLast(RSI_PERIOD + 1).map { it.close }
        if (closes.size < 2) return 50.0

        val changes = (1 until closes.size).map { closes[it] - closes[it - 1] }
        val gains = changes.map { maxOf(it, 0.0) }
        val losses = changes.map { maxOf(-it, 0.0) }

        val avgGain = gains.average()
        val avgLoss = losses.average()

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    companion object {
        private const val ATR_PERIOD = 14
        private const val FLAT_ATR_THRESHOLD = 0.05
        private const val FLAT_BODY_THRESHOLD = 0.15

        private const val SESSION_OPEN = 810   // 13:30 UTC
        private const val SESSION_CLOSE = 1200 // 20:00 UTC

        private const val OPENING_START = 810  // 13:30 UTC
        private const val OPENING_END = 839    // 13:59 UTC
        private const val MORNING_START = 840  // 14:00 UTC
        private const val MORNING_END = 1019   // 16:59 UTC
        private const val LUNCH_START = 1020   // 17:00 UTC
        private const val LUNCH_END = 1109     // 18:29 UTC
        private const val CLOSING_START = 1110 // 18:30 UTC
        private const val CLOSING_END = 1199   // 19:59 UTC

        private const val OPENING_GAP_THRESHOLD = 1.5
        private const val ATR_STRONG = 0.6
        private const val ATR_WEAK = 0.25
        private const val CROSS_SIGNAL_THRESHOLD = 0.4
        private const val LUNCH_STREAK = 3

        private const val RSI_PERIOD = 14
        private const val RSI_OVERSOLD = 25.0
        private const val RSI_OVERBOUGHT = 75.0

        private const val SMA_SHORT_PERIOD = 3
        private const val SMA_LONG_PERIOD = 10
    }
}
