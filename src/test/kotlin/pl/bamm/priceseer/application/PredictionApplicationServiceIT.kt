package pl.bamm.priceseer.application

import com.xtb.adapter.outgoing.protobuf.PredictionProto
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import pl.bamm.priceseer.domain.port.PriceRepository
import pl.bamm.priceseer.fixtures.TestDatabase
import pl.bamm.priceseer.fixtures.TestKafka
import pl.bamm.priceseer.fixtures.marketPrice
import pl.bamm.priceseer.fixtures.marketPriceBytes
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class PredictionApplicationServiceIT {

    companion object {
        private const val PREDICTIONS_TOPIC = "predictions"
        private const val MARKET_PRICES_TOPIC = "market-prices"

        @JvmStatic
        @DynamicPropertySource
        fun containerProperties(registry: DynamicPropertyRegistry) {
            TestKafka
            TestDatabase
            registry.add("spring.kafka.bootstrap-servers") { TestKafka.bootstrapServers }
            registry.add("spring.datasource.url") { TestDatabase.jdbcUrl }
            registry.add("spring.datasource.username") { "test" }
            registry.add("spring.datasource.password") { "test" }
        }
    }

    @Autowired
    lateinit var sut: PredictionApplicationService

    @Autowired
    lateinit var priceRepository: PriceRepository

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var consumer: KafkaConsumer<String, ByteArray>

    @BeforeEach
    fun setUp() {
        TestDatabase.resetSchema()
        consumer = KafkaConsumer<String, ByteArray>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to TestKafka.bootstrapServers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java.name,
            ),
        )
        val tp = TopicPartition(PREDICTIONS_TOPIC, 0)
        consumer.assign(listOf(tp))
        consumer.seekToEnd(listOf(tp))
        consumer.poll(Duration.ofMillis(100))
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#onPriceReceived} persists
     * the received {@link MarketPrice} in the JDBC-backed {@link PriceRepository}.
     *
     * <p>Test data - Single {@link MarketPrice} for symbol {@code "BTC/USD"} with
     * {@code open=50000.0} and {@code close=51000.0}.
     *
     * <p>Test expected result - Repository history for {@code "BTC/USD"} contains exactly one
     * entry with matching symbol, open, and close values.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `onPriceReceived stores price in database`() {
        val price = marketPrice("BTC/USD", open = 50_000.0, close = 51_000.0)

        sut.onPriceReceived(price)

        val history = priceRepository.history("BTC/USD")
        assertEquals(1, history.size)
        assertEquals("BTC/USD", history.first().symbol)
        assertEquals(50_000.0, history.first().open)
        assertEquals(51_000.0, history.first().close)
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} produces
     * Protobuf-encoded predictions to the {@code predictions} Kafka topic for all 8 instruments.
     *
     * <p>Test data - All 8 instruments stored in the JDBC repository via
     * {@link PredictionApplicationService#onPriceReceived}.
     *
     * <p>Test expected result - 8 Protobuf records appear on the {@code predictions} topic,
     * one per instrument, each with non-blank team, symbol, and timestamp fields.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions sends to Kafka for all instruments`() {
        PredictionApplicationService.INSTRUMENTS.forEach { symbol ->
            sut.onPriceReceived(marketPrice(symbol))
        }

        sut.sendPredictions()
        kafkaTemplate.flush()

        val records = pollPredictions(expected = 8)
        assertEquals(8, records.size)
        val symbols = records.map { it.first }.toSet()
        assertEquals(PredictionApplicationService.INSTRUMENTS.toSet(), symbols)
        records.forEach { (_, proto) ->
            assertTrue(proto.team.isNotBlank())
            assertTrue(proto.symbol.isNotBlank())
            assertTrue(proto.timestamp.isNotBlank())
        }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} does not
     * insert duplicate rows into {@code sent_prediction} when invoked twice within the same
     * minute, confirming the JDBC-backed deduplication mechanism.
     *
     * <p>Test data - All 8 instruments stored in the repository, {@code sendPredictions()}
     * invoked twice in succession within the same minute.
     *
     * <p>Test expected result - The {@code sent_prediction} table contains exactly 8 rows
     * (one per instrument), not 16.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions skips duplicate within same minute`() {
        PredictionApplicationService.INSTRUMENTS.forEach { symbol ->
            sut.onPriceReceived(marketPrice(symbol))
        }

        sut.sendPredictions()
        sut.sendPredictions()

        val rowCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sent_prediction",
            Int::class.java,
        )
        assertEquals(8, rowCount)
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} defaults
     * to {@code Direction.UP} for all instruments when the JDBC repository contains no price
     * history.
     *
     * <p>Test data - Empty {@code market_price} table, no prior calls to
     * {@link PredictionApplicationService#onPriceReceived}.
     *
     * <p>Test expected result - 8 predictions appear on the {@code predictions} Kafka topic,
     * all with {@code Direction.UP}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `sendPredictions defaults to UP when no history`() {
        sut.sendPredictions()
        kafkaTemplate.flush()

        val records = pollPredictions(expected = 8)
        assertEquals(8, records.size)
        records.forEach { (_, proto) ->
            assertEquals(PredictionProto.Direction.UP, proto.prediction)
        }
    }

    /**
     * Test purpose - Verify the full end-to-end flow: a Protobuf-encoded {@link MarketPrice}
     * published to the {@code market-prices} Kafka topic is consumed by
     * {@link MarketPriceConsumer}, stored via {@link PredictionApplicationService#onPriceReceived},
     * and then {@link PredictionApplicationService#sendPredictions} produces a corresponding
     * prediction to the {@code predictions} topic.
     *
     * <p>Test data - Protobuf-encoded {@link MarketPrice} for symbol {@code "XTB"} with
     * {@code open=100.0} and {@code close=110.0}, published to {@code market-prices}.
     *
     * <p>Test expected result - A prediction for symbol {@code "XTB"} with a non-blank team
     * name appears on the {@code predictions} Kafka topic.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `end-to-end Kafka price in then prediction out`() {
        val bytes = marketPriceBytes(symbol = "XTB", open = 100.0, close = 110.0)
        kafkaTemplate.send(MARKET_PRICES_TOPIC, "XTB", bytes).get()

        Thread.sleep(3_000)

        sut.sendPredictions()
        kafkaTemplate.flush()

        val records = pollPredictions(expected = 8)
        val xtbPrediction = records.first { it.first == "XTB" }
        assertEquals("XTB", xtbPrediction.second.symbol)
        assertTrue(xtbPrediction.second.team.isNotBlank())
    }

    private fun pollPredictions(
        expected: Int,
        timeout: Duration = Duration.ofSeconds(10),
    ): List<Pair<String, PredictionProto.Prediction>> {
        val result = mutableListOf<Pair<String, PredictionProto.Prediction>>()
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        while (result.size < expected && System.currentTimeMillis() < deadline) {
            val batch = consumer.poll(Duration.ofMillis(500))
            batch.forEach { record ->
                val proto = PredictionProto.Prediction.parseFrom(record.value())
                result.add(record.key() to proto)
            }
        }
        return result
    }
}
