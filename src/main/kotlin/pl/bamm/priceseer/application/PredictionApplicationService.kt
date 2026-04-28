package pl.bamm.priceseer.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.model.Prediction
import pl.bamm.priceseer.domain.port.PredictionPort
import pl.bamm.priceseer.domain.port.PredictionStrategy
import pl.bamm.priceseer.domain.port.PriceRepository
import pl.bamm.priceseer.domain.port.SentPredictionRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Application service that orchestrates price ingestion, strategy selection, and
 * prediction dispatch for all tracked instruments.
 */
@Service
class PredictionApplicationService(
    private val priceRepository: PriceRepository,
    private val predictionPort: PredictionPort,
    private val sentPredictionRepository: SentPredictionRepository,
    @Qualifier("momentum") private val defaultStrategy: PredictionStrategy,
    @Qualifier("crypto") private val cryptoStrategy: PredictionStrategy,
    @Qualifier("forex") private val forexStrategy: PredictionStrategy,
    @Qualifier("gold") private val goldStrategy: PredictionStrategy,
    @Qualifier("stock") private val stockStrategy: PredictionStrategy,
    @Value("\${app.team-name}") private val teamName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Stores a received {@link MarketPrice} candle in the price repository.
     *
     * @param price the market price candle received from Kafka
     */
    fun onPriceReceived(price: MarketPrice) {
        priceRepository.store(price)
        log.info("Stored price: symbol={} close={} ts={}", price.symbol, price.close, price.timestamp)
    }

    /**
     * Sends one directional prediction per instrument for the current minute.
     * Selects the appropriate {@link PredictionStrategy} for each symbol and
     * skips instruments that already have a prediction recorded for the current minute.
     */
    fun sendPredictions() {
        val currentMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        log.info("Sending predictions for minute={}", currentMinute)

        INSTRUMENTS.forEach { symbol ->
            val history = priceRepository.history(symbol)
            val direction = if (history.isEmpty()) {
                log.info("No price data for symbol={}, defaulting to UP", symbol)
                Direction.UP
            } else {
                strategyFor(symbol).predict(symbol, history)
            }

            if (!sentPredictionRepository.tryMarkSent(symbol, currentMinute, direction)) {
                log.debug("Skipping duplicate for symbol={} minute={}", symbol, currentMinute)
                return@forEach
            }

            val prediction = Prediction(
                team = teamName,
                symbol = symbol,
                timestamp = currentMinute.toString(),
                direction = direction,
            )
            predictionPort.send(prediction)
            log.info("Sent prediction: symbol={} direction={} minute={}", symbol, direction, currentMinute)
        }
    }

    private fun strategyFor(symbol: String): PredictionStrategy = when (symbol) {
        in CRYPTO_INSTRUMENTS -> cryptoStrategy
        in FOREX_INSTRUMENTS -> forexStrategy
        in STOCK_INSTRUMENTS -> stockStrategy
        "XAU/USD" -> goldStrategy
        else -> defaultStrategy
    }

    companion object {
        val INSTRUMENTS = listOf(
            "XTB", "CDR", "EUR/USD", "GBP/JPY",
            "BTC/USD", "ETH/USD", "XAU/USD", "USD/JPY",
        )
        private val CRYPTO_INSTRUMENTS = setOf("BTC/USD", "ETH/USD")
        private val FOREX_INSTRUMENTS = setOf("EUR/USD", "GBP/JPY", "USD/JPY")
        private val STOCK_INSTRUMENTS = setOf("XTB", "CDR")
    }
}
