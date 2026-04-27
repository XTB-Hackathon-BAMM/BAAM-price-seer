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
import java.util.concurrent.ConcurrentHashMap

@Service
class PredictionApplicationService(
    private val priceRepository: PriceRepository,
    private val predictionPort: PredictionPort,
    @Qualifier("momentum") private val strategy: PredictionStrategy,
    @Value("\${app.team-name}") private val teamName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lastSentMinute = ConcurrentHashMap<String, String>()

    fun onPriceReceived(price: MarketPrice) {
        priceRepository.store(price)

        val currentMinute = currentUtcMinute()
        if (lastSentMinute[price.symbol] == currentMinute) {
            log.debug("Skipping duplicate for symbol={} minute={}", price.symbol, currentMinute)
            return
        }

        val history = priceRepository.history(price.symbol)
        val direction = strategy.predict(price.symbol, history)
        val prediction = Prediction(
            team = teamName,
            symbol = price.symbol,
            timestamp = currentMinute,
            direction = direction,
        )

        predictionPort.send(prediction)
        lastSentMinute[price.symbol] = currentMinute
        log.info("Sent prediction: symbol={} direction={} minute={}", price.symbol, direction, currentMinute)
    }

    private fun currentUtcMinute(): String =
        Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
}
