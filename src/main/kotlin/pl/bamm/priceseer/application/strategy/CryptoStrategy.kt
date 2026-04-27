package pl.bamm.priceseer.application.strategy

import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PredictionStrategy
import pl.bamm.priceseer.domain.port.PriceRepository
import kotlin.math.abs
import kotlin.math.sqrt

@Component("crypto")
class CryptoStrategy(
    private val priceRepository: PriceRepository,
) : PredictionStrategy {

    override fun predict(symbol: String, history: List<MarketPrice>): Direction {
        if (history.isEmpty()) return Direction.UP

        if (symbol == "ETH/USD") {
            val btcHistory = priceRepository.history("BTC/USD")
            btcLeadingIndicator(btcHistory)?.let { return it }
        }

        val direction = decisionTree(history)

        val last = history.last()
        if (isDoji(last)) return Direction.UP

        return direction
    }

    private fun decisionTree(history: List<MarketPrice>): Direction {
        return when (detectRegime(history)) {
            Regime.QUIET -> Direction.UP
            Regime.TRENDING -> {
                atrFilteredMomentum(history)
                    ?: pureMomentum(history.last())
            }
            Regime.NORMAL -> {
                atrFilteredMomentum(history)
                    ?: rsiSignal(history)
                    ?: pureMomentum(history.last())
            }
        }
    }

    // Step 3: BTC as leading indicator for ETH
    private fun btcLeadingIndicator(btcHistory: List<MarketPrice>): Direction? {
        if (btcHistory.size < ATR_PERIOD) return null
        val last = btcHistory.last()
        val body = abs(last.close - last.open)
        val atr = calculateAtr(btcHistory)
        if (atr == 0.0) return null

        if (body > atr * BTC_LEAD_THRESHOLD) {
            return if (last.close >= last.open) Direction.UP else Direction.DOWN
        }
        return null
    }

    // Step 1: Volatility regime detection
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

    // Step 2a: ATR-filtered momentum
    private fun atrFilteredMomentum(history: List<MarketPrice>): Direction? {
        if (history.size < ATR_PERIOD) return null
        val last = history.last()
        val body = abs(last.close - last.open)
        val atr = calculateAtr(history)
        if (atr == 0.0) return null

        return when {
            body > atr * ATR_STRONG -> if (last.close >= last.open) Direction.UP else Direction.DOWN
            body < atr * ATR_WEAK -> Direction.UP
            else -> null
        }
    }

    // Step 2b: RSI with sharp thresholds
    private fun rsiSignal(history: List<MarketPrice>): Direction? {
        if (history.size < RSI_PERIOD + 1) return null
        val rsi = calculateRsi(history)

        return when {
            rsi < RSI_OVERSOLD -> Direction.UP
            rsi > RSI_OVERBOUGHT -> Direction.DOWN
            else -> null
        }
    }

    // Step 2c: Pure momentum fallback
    private fun pureMomentum(candle: MarketPrice): Direction =
        if (candle.close >= candle.open) Direction.UP else Direction.DOWN

    private fun isDoji(candle: MarketPrice): Boolean {
        val shadow = candle.high - candle.low
        if (shadow <= 0.0) return candle.close == candle.open
        val body = abs(candle.close - candle.open)
        return body / shadow < DOJI_THRESHOLD
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
        private const val VOLATILITY_HIGH = 0.002
        private const val VOLATILITY_LOW = 0.0005
        private const val ATR_PERIOD = 14
        private const val ATR_STRONG = 0.7
        private const val ATR_WEAK = 0.3
        private const val RSI_PERIOD = 14
        private const val RSI_OVERSOLD = 20.0
        private const val RSI_OVERBOUGHT = 80.0
        private const val BTC_LEAD_THRESHOLD = 0.5
        private const val DOJI_THRESHOLD = 0.15
    }
}
