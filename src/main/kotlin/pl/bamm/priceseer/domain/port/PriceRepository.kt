package pl.bamm.priceseer.domain.port

import pl.bamm.priceseer.domain.model.MarketPrice

interface PriceRepository {
    fun store(price: MarketPrice)
    fun history(symbol: String): List<MarketPrice>
    fun latest(symbol: String): MarketPrice?
}
