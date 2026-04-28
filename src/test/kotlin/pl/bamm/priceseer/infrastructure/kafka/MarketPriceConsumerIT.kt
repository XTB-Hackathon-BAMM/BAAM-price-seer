package pl.bamm.priceseer.infrastructure.kafka

import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import pl.bamm.priceseer.application.PredictionApplicationService
import pl.bamm.priceseer.fixtures.TestDatabase
import pl.bamm.priceseer.fixtures.TestKafka
import pl.bamm.priceseer.fixtures.marketPriceBytes

@SpringBootTest
@ActiveProfiles("test")
class MarketPriceConsumerIT {

    companion object {
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
    lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @MockkBean
    lateinit var predictionApplicationService: PredictionApplicationService

    /**
     * Test purpose - Verify that {@link MarketPriceConsumer} correctly deserializes valid Protobuf
     * bytes from the {@code market-prices} Kafka topic and delegates to
     * {@link PredictionApplicationService#onPriceReceived}.
     *
     * <p>Test data - Valid Protobuf-encoded {@link MarketPrice} bytes for symbol {@code "BTC/USD"}
     * with {@code open=50000.0} and {@code close=51000.0}, published to the {@code market-prices} topic.
     *
     * <p>Test expected result - {@link PredictionApplicationService#onPriceReceived} is invoked
     * exactly once.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `given valid market price bytes when published then application service is called`() {
        val bytes = marketPriceBytes(symbol = "BTC/USD", open = 50_000.0, close = 51_000.0)

        kafkaTemplate.send("market-prices", "BTC/USD", bytes).get()

        verify(timeout = 10_000L, exactly = 1) { predictionApplicationService.onPriceReceived(any()) }
    }

    /**
     * Test purpose - Verify that {@link MarketPriceConsumer} gracefully handles malformed bytes
     * from the {@code market-prices} Kafka topic without invoking the application service.
     *
     * <p>Test data - Single byte {@code 0xFF} published to the {@code market-prices} topic
     * with key {@code "BAD"}.
     *
     * <p>Test expected result - {@link PredictionApplicationService#onPriceReceived} is never invoked.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `given malformed bytes when published then application service is never called`() {
        kafkaTemplate.send("market-prices", "BAD", byteArrayOf(0xFF.toByte())).get()

        verify(timeout = 3_000L, exactly = 0) { predictionApplicationService.onPriceReceived(any()) }
    }
}
