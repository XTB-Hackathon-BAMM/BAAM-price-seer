package pl.bamm.priceseer.infrastructure.kafka

import com.ninjasquad.springmockk.MockkBean
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import pl.bamm.priceseer.application.PredictionApplicationService
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.fixtures.TestKafka
import pl.bamm.priceseer.fixtures.marketPriceBytes

@SpringBootTest
class MarketPriceConsumerIT {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            TestKafka  // trigger lazy init
            registry.add("spring.kafka.bootstrap-servers") { TestKafka.bootstrapServers }
        }
    }

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @MockkBean
    lateinit var predictionApplicationService: PredictionApplicationService

    @Test
    fun `given valid market price bytes when published then application service is called`() {
        val bytes = marketPriceBytes(symbol = "BTC/USD", open = 50_000.0, close = 51_000.0)

        kafkaTemplate.send("market-prices", "BTC/USD", bytes).get()

        verify(timeout = 10_000L, exactly = 1) { predictionApplicationService.onPriceReceived(any()) }
    }

    @Test
    fun `given malformed bytes when published then application service is never called`() {
        kafkaTemplate.send("market-prices", "BAD", byteArrayOf(0xFF.toByte())).get()

        verify(timeout = 3_000L, exactly = 0) { predictionApplicationService.onPriceReceived(any()) }
    }
}
