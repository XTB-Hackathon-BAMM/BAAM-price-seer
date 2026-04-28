package pl.bamm.priceseer.application.strategy

import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PredictionStrategy
import pl.bamm.priceseer.domain.port.PriceRepository
import java.time.Clock
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Prediction strategy for the gold instrument ({@code XAU/USD}).
 *
 * <p>Combines volatility regime detection, PM fix counter-trend logic (14:00–14:05 UTC),
 * ATR-filtered momentum, RSI signals, a stock-market risk proxy during the New York session,
 * and SMA trend filtering as a final fallback.
 */
@Component("gold")
class GoldStrategy(
    private val priceRepository: PriceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : PredictionStrategy {

    /** {@inheritDoc} */
    override fun predict(symbol: String, history: List<MarketPrice>): Direction {
        if (history.isEmpty()) return Direction.UP

        if (detectRegime(history) == Regime.QUIET) return Direction.UP

        val last = history.last()
        val atr = calculateAtr(history)

        if (atr > 0) {
            pmFixCounterTrend(last, atr)?.let { return it }
        }

        if (atr > 0) {
            val body = abs(last.close - last.open)
            when {
                body > atr * ATR_STRONG -> return pureMomentum(last)
                body < atr * ATR_WEAK -> return Direction.UP
            }
        }

        rsiSignal(history)?.let { return it }

        if (isNySession()) {
            riskProxy()?.let { return it }
        }

        return smaTrendFilter(history)
    }

    private fun detectRegime(history: List<MarketPrice>): Regime {
        val recent = history.takeLast(REGIME_WINDOW)
        if (recent.size < 2) return Regime.NORMAL

        val returns = recent.map { (it.close - it.open) / it.open }
        val std = standardDeviation(returns)

        return when {
            std > VOLATILITY_HIGH -> Regime.TRENDING
            std < VOLATILITY_LOW -> Regime.QUIET
            else -> Regime.NORMAL
        }
    }

    private fun pmFixCounterTrend(candle: MarketPrice, atr: Double): Direction? {
        val time = clock.instant().atZone(ZoneOffset.UTC).toLocalTime()
        val minutes = time.hour * 60 + time.minute
        if (minutes !in 840..845) return null // 14:00–14:05 UTC

        val body = abs(candle.close - candle.open)
        if (body <= atr * PM_FIX_THRESHOLD) return null

        return if (candle.close >= candle.open) Direction.DOWN else Direction.UP
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

    private fun riskProxy(): Direction? {
        val xtbLatest = priceRepository.latest("XTB") ?: return null
        val cdrLatest = priceRepository.latest("CDR") ?: return null

        val xtbUp = xtbLatest.close >= xtbLatest.open
        val cdrUp = cdrLatest.close >= cdrLatest.open

        return when {
            xtbUp && cdrUp -> Direction.DOWN
            !xtbUp && !cdrUp -> Direction.UP
            else -> null
        }
    }

    private fun smaTrendFilter(history: List<MarketPrice>): Direction {
        if (history.size < SMA_LONG_PERIOD + SMA_SLOPE_LOOKBACK) return pureMomentum(history.last())

        val smaShort = sma(history, SMA_SHORT_PERIOD)
        val smaLong = sma(history, SMA_LONG_PERIOD)

        val olderSlice = history.subList(0, history.size - SMA_SLOPE_LOOKBACK)
        val smaLongPrev = if (olderSlice.size >= SMA_LONG_PERIOD) sma(olderSlice, SMA_LONG_PERIOD) else smaLong

        val smaRising = smaLong > smaLongPrev
        val smaFalling = smaLong < smaLongPrev

        return when {
            smaShort > smaLong && smaRising -> Direction.UP
            smaShort < smaLong && smaFalling -> Direction.DOWN
            else -> pureMomentum(history.last())
        }
    }

    private fun pureMomentum(candle: MarketPrice): Direction =
        if (candle.close >= candle.open) Direction.UP else Direction.DOWN

    private fun isNySession(): Boolean {
        val time = clock.instant().atZone(ZoneOffset.UTC).toLocalTime()
        val minutes = time.hour * 60 + time.minute
        return minutes in 810..1200 // 13:30–20:00 UTC
    }

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

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private enum class Regime { QUIET, TRENDING, NORMAL }

    companion object {
        private const val REGIME_WINDOW = 5
        private const val VOLATILITY_HIGH = 0.001
        private const val VOLATILITY_LOW = 0.0002
        private const val ATR_PERIOD = 14
        private const val ATR_STRONG = 0.8
        private const val ATR_WEAK = 0.3
        private const val PM_FIX_THRESHOLD = 2.0
        private const val RSI_PERIOD = 14
        private const val RSI_OVERSOLD = 25.0
        private const val RSI_OVERBOUGHT = 75.0
        private const val SMA_SHORT_PERIOD = 3
        private const val SMA_LONG_PERIOD = 10
        private const val SMA_SLOPE_LOOKBACK = 3
    }
}
