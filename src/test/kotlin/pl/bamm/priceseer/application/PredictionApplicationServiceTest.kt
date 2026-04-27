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
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class PredictionApplicationServiceTest {

    private lateinit var priceRepository: InMemoryPriceRepository
    private val predictionPort = mockk<PredictionPort>()
    private val defaultStrategy = mockk<PredictionStrategy>()
    private val cryptoStrategy = mockk<PredictionStrategy>()

    private lateinit var sut: PredictionApplicationService

    @BeforeEach
    fun setUp() {
        priceRepository = InMemoryPriceRepository()
        sut = PredictionApplicationService(priceRepository, predictionPort, defaultStrategy, cryptoStrategy, "BAAM")
    }

    @Test
    fun `onPriceReceived stores price in repository`() {
        val price = marketPrice("BTC/USD")

        sut.onPriceReceived(price)

        assertEquals(listOf(price), priceRepository.history("BTC/USD"))
    }

    @Test
    fun `onPriceReceived does not send prediction`() {
        val price = marketPrice("BTC/USD")

        sut.onPriceReceived(price)

        verify(exactly = 0) { predictionPort.send(any()) }
    }

    @Test
    fun `sendPredictions uses crypto strategy for BTC and ETH`() {
        priceRepository.store(marketPrice("BTC/USD"))
        priceRepository.store(marketPrice("ETH/USD"))
        every { cryptoStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 2) { cryptoStrategy.predict(any(), any()) }
        verify(exactly = 0) { defaultStrategy.predict(any(), any()) }
    }

    @Test
    fun `sendPredictions uses default strategy for non-crypto instruments`() {
        priceRepository.store(marketPrice("AAPL"))
        priceRepository.store(marketPrice("MSFT"))
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 2) { defaultStrategy.predict(any(), any()) }
        verify(exactly = 0) { cryptoStrategy.predict(any(), any()) }
    }

    @Test
    fun `sendPredictions skips instruments with no price data`() {
        priceRepository.store(marketPrice("AAPL"))
        every { defaultStrategy.predict(any(), any()) } returns Direction.DOWN
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 1) { predictionPort.send(any()) }
        verify { predictionPort.send(match { it.symbol == "AAPL" }) }
    }

    @Test
    fun `sendPredictions sets team name correctly`() {
        priceRepository.store(marketPrice("AAPL"))
        val capturedPredictions = mutableListOf<Prediction>()
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(capture(capturedPredictions)) } just runs

        sut.sendPredictions()

        assertEquals("BAAM", capturedPredictions.first().team)
    }

    @Test
    fun `sendPredictions sends predictions for all 8 instruments when all have data`() {
        PredictionApplicationService.INSTRUMENTS.forEach { symbol ->
            priceRepository.store(marketPrice(symbol))
        }
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { cryptoStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 8) { predictionPort.send(any()) }
    }
}
