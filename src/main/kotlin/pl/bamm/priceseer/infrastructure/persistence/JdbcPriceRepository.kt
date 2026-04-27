package pl.bamm.priceseer.infrastructure.persistence

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.domain.port.PriceRepository
import java.sql.ResultSet
import java.sql.Timestamp

@Profile("!in-memory")
@Repository
class JdbcPriceRepository(
    private val jdbc: JdbcTemplate,
    @Value("\${app.history-size:120}") private val historySize: Int,
) : PriceRepository {

    override fun store(price: MarketPrice) {
        jdbc.update(
            """
            INSERT INTO market_price (symbol, ts, interval, open, high, low, close)
            VALUES (?, ?::timestamptz, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, ts) DO NOTHING
            """.trimIndent(),
            price.symbol, price.timestamp, price.interval,
            price.open, price.high, price.low, price.close,
        )
    }

    override fun history(symbol: String): List<MarketPrice> =
        jdbc.query(
            """
            SELECT symbol, ts, interval, open, high, low, close
            FROM market_price
            WHERE symbol = ?
            ORDER BY ts DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.toMarketPrice() },
            symbol, historySize,
        ).reversed()

    override fun latest(symbol: String): MarketPrice? =
        jdbc.query(
            """
            SELECT symbol, ts, interval, open, high, low, close
            FROM market_price
            WHERE symbol = ?
            ORDER BY ts DESC
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> rs.toMarketPrice() },
            symbol,
        ).firstOrNull()

    private fun ResultSet.toMarketPrice() = MarketPrice(
        symbol = getString("symbol"),
        timestamp = getTimestamp("ts").toInstant().toString().replace(".000Z", "Z"),
        interval = getString("interval"),
        open = getDouble("open"),
        high = getDouble("high"),
        low = getDouble("low"),
        close = getDouble("close"),
    )
}
