package pl.bamm.priceseer.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension
import pl.bamm.priceseer.domain.model.Direction
import pl.bamm.priceseer.domain.model.Prediction
import pl.bamm.priceseer.domain.port.PredictionPort
import pl.bamm.priceseer.domain.port.PredictionStrategy
import pl.bamm.priceseer.infrastructure.persistence.InMemoryPriceRepository
import pl.bamm.priceseer.fixtures.marketPrice
import kotlin.test.Test

@ExtendWith(MockKExtension::class)
class PredictionApplicationServiceTest {

    private lateinit var priceRepository: InMemoryPriceRepository
    private val predictionPort = mockk<PredictionPort>()
    private val strategy = mockk<PredictionStrategy>()

    private lateinit var sut: PredictionApplicationService

    @BeforeEach
    fun setUp() {
        priceRepository = InMemoryPriceRepository()
        sut = PredictionApplicationService(priceRepository, predictionPort, strategy, "BAAM")
    }

    @Test
    fun `given price received when onPriceReceived then stores price and sends prediction`() {
        val price = marketPrice("BTC/USD")
        every { strategy.predict("BTC/USD", any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.onPriceReceived(price)

        verify(exactly = 1) {
            predictionPort.send(match { it.symbol == "BTC/USD" && it.direction == Direction.UP && it.team == "BAAM" })
        }
    }

    @Test
    fun `given two prices in same minute when onPriceReceived then prediction sent only once`() {
        val price = marketPrice("BTC/USD")
        every { strategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.onPriceReceived(price)
        sut.onPriceReceived(price)

        verify(exactly = 1) { predictionPort.send(any()) }
    }

    @Test
    fun `given different symbols in same minute when onPriceReceived then both predictions sent`() {
        val btc = marketPrice("BTC/USD")
        val eth = marketPrice("ETH/USD")
        every { strategy.predict(any(), any()) } returns Direction.DOWN
        every { predictionPort.send(any()) } just runs

        sut.onPriceReceived(btc)
        sut.onPriceReceived(eth)

        verify(exactly = 2) { predictionPort.send(any()) }
    }

    @Test
    fun `given team name when prediction created then team name is set correctly`() {
        val price = marketPrice("AAPL")
        val capturedPredictions = mutableListOf<Prediction>()
        every { strategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(capture(capturedPredictions)) } just runs

        sut.onPriceReceived(price)

        verify(exactly = 1) { predictionPort.send(any()) }
        assert(capturedPredictions.first().team == "BAAM")
    }
}