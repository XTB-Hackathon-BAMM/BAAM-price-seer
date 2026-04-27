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
import pl.bamm.priceseer.domain.port.SentPredictionRepository
import pl.bamm.priceseer.infrastructure.persistence.InMemoryPriceRepository
import pl.bamm.priceseer.fixtures.marketPrice
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class PredictionApplicationServiceTest {

    private lateinit var priceRepository: InMemoryPriceRepository
    private val predictionPort = mockk<PredictionPort>()
    private val sentPredictionRepository = mockk<SentPredictionRepository>()
    private val defaultStrategy = mockk<PredictionStrategy>()
    private val cryptoStrategy = mockk<PredictionStrategy>()
    private val forexStrategy = mockk<PredictionStrategy>()

    private lateinit var sut: PredictionApplicationService

    @BeforeEach
    fun setUp() {
        priceRepository = InMemoryPriceRepository()
        sut = PredictionApplicationService(
            priceRepository, predictionPort, sentPredictionRepository,
            defaultStrategy, cryptoStrategy, "BAAM"
        )
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
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        every { cryptoStrategy.predict(any(), any()) } returns Direction.DOWN
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { forexStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 2) { cryptoStrategy.predict(any(), any()) }
    }

    @Test
    fun `sendPredictions uses forex strategy for forex instruments`() {
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { cryptoStrategy.predict(any(), any()) } returns Direction.UP
        every { forexStrategy.predict(any(), any()) } returns Direction.DOWN
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 3) { forexStrategy.predict(any(), any()) }
    }

    @Test
    fun `sendPredictions uses default strategy for non-crypto non-forex instruments`() {
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { cryptoStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { forexStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        // 8 total - 2 crypto - 3 forex = 3 default (AAPL, MSFT, XAU/USD)
        verify(exactly = 3) { defaultStrategy.predict(any(), any()) }
    }

    @Test
    fun `sendPredictions defaults to UP for instruments with no price data`() {
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 8) { predictionPort.send(any()) }
        verify(exactly = 8) { predictionPort.send(match { it.direction == Direction.UP }) }
    }

    @Test
    fun `sendPredictions sets team name correctly`() {
        priceRepository.store(marketPrice("AAPL"))
        val capturedPredictions = mutableListOf<Prediction>()
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
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
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { forexStrategy.predict(any(), any()) } returns Direction.UP
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 8) { predictionPort.send(any()) }
    }

    @Test
    fun `sendPredictions skips already sent predictions for same minute`() {
        priceRepository.store(marketPrice("AAPL"))
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns false

        sut.sendPredictions()

        verify(exactly = 0) { predictionPort.send(any()) }
    }
}
