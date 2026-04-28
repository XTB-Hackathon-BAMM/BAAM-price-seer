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
