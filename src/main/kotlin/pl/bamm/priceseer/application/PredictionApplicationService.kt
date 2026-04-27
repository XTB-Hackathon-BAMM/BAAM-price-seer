package pl.bamm.priceseer.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.model.Prediction
import pl.bamm.priceseer.domain.port.PredictionPort
import pl.bamm.priceseer.domain.port.PredictionStrategy
import pl.bamm.priceseer.domain.port.PriceRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class PredictionApplicationService(
    private val priceRepository: PriceRepository,
    private val predictionPort: PredictionPort,
    @Qualifier("momentum") private val defaultStrategy: PredictionStrategy,
    @Qualifier("crypto") private val cryptoStrategy: PredictionStrategy,
    @Value("\${app.team-name}") private val teamName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun onPriceReceived(price: MarketPrice) {
        priceRepository.store(price)
        log.info("Stored price: symbol={} close={} ts={}", price.symbol, price.close, price.timestamp)
    }

    fun sendPredictions() {
        val currentMinute = currentUtcMinute()
        log.info("Sending predictions for minute={}", currentMinute)

        INSTRUMENTS.forEach { symbol ->
            val history = priceRepository.history(symbol)
            if (history.isEmpty()) return@forEach

            val strategy = strategyFor(symbol)
            val direction = strategy.predict(symbol, history)
            val prediction = Prediction(
                team = teamName,
                symbol = symbol,
                timestamp = currentMinute,
                direction = direction,
            )
            predictionPort.send(prediction)
            log.info("Sent prediction: symbol={} direction={} minute={}", symbol, direction, currentMinute)
        }
    }

    private fun strategyFor(symbol: String): PredictionStrategy =
        if (symbol in CRYPTO_INSTRUMENTS) cryptoStrategy else defaultStrategy

    private fun currentUtcMinute(): String =
        Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()

    companion object {
        val INSTRUMENTS = listOf(
            "AAPL", "MSFT", "EUR/USD", "GBP/JPY",
            "BTC/USD", "ETH/USD", "XAU/USD", "USD/JPY",
        )
        private val CRYPTO_INSTRUMENTS = setOf("BTC/USD", "ETH/USD")
    }
}
