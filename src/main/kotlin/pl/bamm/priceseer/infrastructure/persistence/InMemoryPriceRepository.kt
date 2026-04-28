package pl.bamm.priceseer.infrastructure.persistence

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PriceRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation of {@link PriceRepository} backed by
 * {@link ConcurrentHashMap} with a fixed-size {@link ArrayDeque} per symbol.
 * Active only under the {@code in-memory} Spring profile.
 */
@Profile("in-memory")
@Component
class InMemoryPriceRepository : PriceRepository {

    private val histories = ConcurrentHashMap<String, ArrayDeque<MarketPrice>>()

    /** {@inheritDoc} */
    override fun store(price: MarketPrice) {
        val history = histories.computeIfAbsent(price.symbol) { ArrayDeque() }
        synchronized(history) {
            history.addLast(price)
            if (history.size > HISTORY_SIZE) history.removeFirst()
        }
    }

    /** {@inheritDoc} */
    override fun history(symbol: String): List<MarketPrice> {
        val history = histories[symbol] ?: return emptyList()
        return synchronized(history) { history.toList() }
    }

    /** {@inheritDoc} */
    override fun latest(symbol: String): MarketPrice? {
        val history = histories[symbol] ?: return null
        return synchronized(history) { history.lastOrNull() }
    }

    companion object {
        private const val HISTORY_SIZE = 120
    }
}
