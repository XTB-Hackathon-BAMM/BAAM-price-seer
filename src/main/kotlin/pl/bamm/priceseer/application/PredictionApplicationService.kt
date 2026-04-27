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
import pl.bamm.priceseer.domain.port.SentPredictionRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class PredictionApplicationService(
    private val priceRepository: PriceRepository,
    private val predictionPort: PredictionPort,
    private val sentPredictionRepository: SentPredictionRepository,
    @Qualifier("momentum") private val strategy: PredictionStrategy,
    @Value("\${app.team-name}") private val teamName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun onPriceReceived(price: MarketPrice) {
        priceRepository.store(price)

        val currentMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val history = priceRepository.history(price.symbol)
        val direction = strategy.predict(price.symbol, history)

        val sent = sentPredictionRepository.tryMarkSent(price.symbol, currentMinute, direction)
        if (!sent) {
            log.debug("Skipping duplicate for symbol={} minute={}", price.symbol, currentMinute)
            return
        }

        val prediction = Prediction(
            team = teamName,
            symbol = price.symbol,
            timestamp = currentMinute.toString(),
            direction = direction,
        )
        predictionPort.send(prediction)
        log.info("Sent prediction: symbol={} direction={} minute={}", price.symbol, direction, currentMinute)
    }
}
