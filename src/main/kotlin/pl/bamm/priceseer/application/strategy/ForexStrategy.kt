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

@Component("forex")
class ForexStrategy(
    private val priceRepository: PriceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : PredictionStrategy {

    override fun predict(symbol: String, history: List<MarketPrice>): Direction {
        if (history.isEmpty()) return Direction.UP

        return when (symbol) {
            "EUR/USD" -> predictEurUsd(history)
            "GBP/JPY" -> predictGbpJpy(history)
            "USD/JPY" -> predictUsdJpy(history)
            else -> pureMomentum(history.last())
        }
    }

    private fun predictEurUsd(history: List<MarketPrice>): Direction {
        if (detectRegime(history) == Regime.QUIET) return Direction.UP

        val last = history.last()
        val atr = calculateAtr(history)

        if (atr > 0 && isSmallCandle(last, atr)) return Direction.UP

        rsiSignal(history)?.let { return it }
        meanReversion(history)?.let { return it }

        return Direction.UP
    }

    private fun predictGbpJpy(history: List<MarketPrice>): Direction {
        if (isAsianSession()) return Direction.UP
        if (detectRegime(history) == Regime.QUIET) return Direction.UP

        val last = history.last()
        val atr = calculateAtr(history)

        if (atr > 0) {
            val body = abs(last.close - last.open)
            if (body > atr * ATR_MOMENTUM_THRESHOLD) {
                return pureMomentum(last)
            }
        }

        meanReversion(history)?.let { return it }

        return pureMomentum(history.last())
    }

    private fun predictUsdJpy(history: List<MarketPrice>): Direction {
        if (isAsianSession()) return Direction.UP
        if (detectRegime(history) == Regime.QUIET) return Direction.UP

        val last = history.last()
        val atr = calculateAtr(history)

        if (atr > 0) {
            counterTrend(last, atr)?.let { return it }
        }

        if (isNySession()) {
            riskProxy()?.let { return it }
        }

        rsiSignal(history)?.let { return it }
        meanReversion(history)?.let { return it }

        return Direction.UP
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

    private fun isSmallCandle(candle: MarketPrice, atr: Double): Boolean {
        val body = abs(candle.close - candle.open)
        return body < atr * SMALL_CANDLE_THRESHOLD
    }

    private fun meanReversion(history: List<MarketPrice>): Direction? {
        if (history.size < MEAN_REVERSION_STREAK) return null
        val recent = history.takeLast(MEAN_REVERSION_STREAK)

        val allUp = recent.all { it.close >= it.open }
        val allDown = recent.all { it.close < it.open }

        return when {
            allUp -> Direction.DOWN
            allDown -> Direction.UP
            else -> null
        }
    }

    private fun counterTrend(candle: MarketPrice, atr: Double): Direction? {
        val body = abs(candle.close - candle.open)
        if (body <= atr * COUNTER_TREND_THRESHOLD) return null
        return if (candle.close >= candle.open) Direction.DOWN else Direction.UP
    }

    private fun riskProxy(): Direction? {
        val aaplLatest = priceRepository.latest("AAPL") ?: return null
        val msftLatest = priceRepository.latest("MSFT") ?: return null

        val aaplUp = aaplLatest.close >= aaplLatest.open
        val msftUp = msftLatest.close >= msftLatest.open

        return when {
            aaplUp && msftUp -> Direction.UP
            !aaplUp && !msftUp -> Direction.DOWN
            else -> null
        }
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

    private fun pureMomentum(candle: MarketPrice): Direction =
        if (candle.close >= candle.open) Direction.UP else Direction.DOWN

    private fun isAsianSession(): Boolean {
        val hour = clock.instant().atZone(ZoneOffset.UTC).hour
        return hour < 7
    }

    private fun isNySession(): Boolean {
        val time = clock.instant().atZone(ZoneOffset.UTC).toLocalTime()
        val minutes = time.hour * 60 + time.minute
        return minutes in 810..1200 // 13:30–20:00 UTC
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
        private const val VOLATILITY_HIGH = 0.0002
        private const val VOLATILITY_LOW = 0.00005
        private const val ATR_PERIOD = 14
        private const val SMALL_CANDLE_THRESHOLD = 0.25
        private const val ATR_MOMENTUM_THRESHOLD = 0.5
        private const val COUNTER_TREND_THRESHOLD = 2.0
        private const val MEAN_REVERSION_STREAK = 3
        private const val RSI_PERIOD = 14
        private const val RSI_OVERSOLD = 25.0
        private const val RSI_OVERBOUGHT = 75.0
    }
}
