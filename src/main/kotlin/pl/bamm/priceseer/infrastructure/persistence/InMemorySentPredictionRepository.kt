package pl.bamm.priceseer.infrastructure.persistence

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.port.SentPredictionRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Profile("in-memory")
@Component
class InMemorySentPredictionRepository : SentPredictionRepository {

    private val sent = ConcurrentHashMap<String, Unit>()

    override fun tryMarkSent(symbol: String, minute: Instant, direction: Direction): Boolean =
        sent.putIfAbsent("$symbol:${minute.epochSecond}", Unit) == null
}
