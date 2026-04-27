package pl.bamm.priceseer.infrastructure.kafka

import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.kafka.core.KafkaTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import pl.bamm.priceseer.application.PredictionApplicationService
import pl.bamm.priceseer.domain.model.MarketPrice
import pl.bamm.priceseer.fixtures.marketPriceBytes

@SpringBootTest
@Testcontainers
class MarketPriceConsumerIT {

    companion object {
        @Container
        @JvmStatic
        @ServiceConnection
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
    }

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @MockitoBean
    lateinit var predictionApplicationService: PredictionApplicationService

    @Test
    fun `given valid market price bytes when published then application service is called`() {
        val bytes = marketPriceBytes(symbol = "BTC/USD", open = 50_000.0, close = 51_000.0)

        kafkaTemplate.send("market-prices", "BTC/USD", bytes).get()

        verify(predictionApplicationService, timeout(10_000).times(1))
            .onPriceReceived(any(MarketPrice::class.java))
    }

    @Test
    fun `given malformed bytes when published then application service is never called`() {
        kafkaTemplate.send("market-prices", "BAD", byteArrayOf(0xFF.toByte())).get()

        verify(predictionApplicationService, timeout(3_000).times(0))
            .onPriceReceived(any(MarketPrice::class.java))
    }
}
