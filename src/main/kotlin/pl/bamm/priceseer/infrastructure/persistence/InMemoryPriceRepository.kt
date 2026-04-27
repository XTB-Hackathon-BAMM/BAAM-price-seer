package pl.bamm.priceseer.infrastructure.persistence

import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PriceRepository
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryPriceRepository : PriceRepository {

    private val histories = ConcurrentHashMap<String, ArrayDeque<MarketPrice>>()

    override fun store(price: MarketPrice) {
        val history = histories.computeIfAbsent(price.symbol) { ArrayDeque() }
        synchronized(history) {
            history.addLast(price)
            if (history.size > HISTORY_SIZE) history.removeFirst()
        }
    }

    override fun history(symbol: String): List<MarketPrice> {
        val history = histories[symbol] ?: return emptyList()
        return synchronized(history) { history.toList() }
    }

    override fun latest(symbol: String): MarketPrice? {
        val history = histories[symbol] ?: return null
        return synchronized(history) { history.lastOrNull() }
    }

    companion object {
        private const val HISTORY_SIZE = 20
    }
}
