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
    private val goldStrategy = mockk<PredictionStrategy>()

    private lateinit var sut: PredictionApplicationService

    @BeforeEach
    fun setUp() {
        priceRepository = InMemoryPriceRepository()
        sut = PredictionApplicationService(
            priceRepository, predictionPort, sentPredictionRepository,
            defaultStrategy, cryptoStrategy, forexStrategy, goldStrategy, "BAAM"
        )
    }

    private fun stubAllStrategies() {
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { cryptoStrategy.predict(any(), any()) } returns Direction.UP
        every { forexStrategy.predict(any(), any()) } returns Direction.UP
        every { goldStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { predictionPort.send(any()) } just runs
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#onPriceReceived} stores
     * the received {@link MarketPrice} in the {@link PriceRepository}.
     *
     * <p>Test data - Single {@link MarketPrice} for symbol {@code "BTC/USD"}.
     *
     * <p>Test expected result - Repository history for {@code "BTC/USD"} contains the stored price.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `onPriceReceived stores price in repository`() {
        val price = marketPrice("BTC/USD")

        sut.onPriceReceived(price)

        assertEquals(listOf(price), priceRepository.history("BTC/USD"))
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#onPriceReceived} does not
     * trigger any prediction sending — predictions are sent separately via scheduled invocation.
     *
     * <p>Test data - Single {@link MarketPrice} for symbol {@code "BTC/USD"}.
     *
     * <p>Test expected result - {@link PredictionPort#send} is never invoked.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `onPriceReceived does not send prediction`() {
        val price = marketPrice("BTC/USD")

        sut.onPriceReceived(price)

        verify(exactly = 0) { predictionPort.send(any()) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} routes
     * crypto instruments ({@code "BTC/USD"} and {@code "ETH/USD"}) to the crypto strategy.
     *
     * <p>Test data - All 8 instruments stored in the repository, all strategies stubbed to
     * return {@code Direction.UP}.
     *
     * <p>Test expected result - Crypto strategy is invoked exactly 2 times.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions uses crypto strategy for BTC and ETH`() {
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        stubAllStrategies()

        sut.sendPredictions()

        verify(exactly = 2) { cryptoStrategy.predict(any(), any()) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} routes
     * forex instruments ({@code "EUR/USD"}, {@code "GBP/JPY"}, {@code "USD/JPY"}) to the
     * forex strategy.
     *
     * <p>Test data - All 8 instruments stored in the repository, all strategies stubbed to
     * return {@code Direction.UP}.
     *
     * <p>Test expected result - Forex strategy is invoked exactly 3 times.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions uses forex strategy for forex instruments`() {
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        stubAllStrategies()

        sut.sendPredictions()

        verify(exactly = 3) { forexStrategy.predict(any(), any()) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} routes
     * the gold instrument ({@code "XAU/USD"}) to the gold strategy.
     *
     * <p>Test data - All 8 instruments stored in the repository, all strategies stubbed to
     * return {@code Direction.UP}.
     *
     * <p>Test expected result - Gold strategy is invoked exactly once with symbol {@code "XAU/USD"}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions uses gold strategy for XAU-USD`() {
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        stubAllStrategies()

        sut.sendPredictions()

        verify(exactly = 1) { goldStrategy.predict(eq("XAU/USD"), any()) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} routes
     * the remaining instruments ({@code "XTB"} and {@code "CDR"}) to the default strategy,
     * after crypto, forex, and gold instruments are handled by their respective strategies.
     *
     * <p>Test data - All 8 instruments stored in the repository, all strategies stubbed to
     * return {@code Direction.UP}.
     *
     * <p>Test expected result - Default strategy is invoked exactly 2 times.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions uses default strategy for XTB and CDR only`() {
        PredictionApplicationService.INSTRUMENTS.forEach { priceRepository.store(marketPrice(it)) }
        stubAllStrategies()

        sut.sendPredictions()

        // 8 total - 2 crypto - 3 forex - 1 gold = 2 default (XTB, CDR)
        verify(exactly = 2) { defaultStrategy.predict(any(), any()) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} defaults
     * to {@code Direction.UP} for all instruments when no price data is available in the repository.
     *
     * <p>Test data - Empty price repository, all 8 instruments with no stored history.
     *
     * <p>Test expected result - 8 predictions are sent, all with {@code Direction.UP}.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `sendPredictions defaults to UP for instruments with no price data`() {
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { predictionPort.send(any()) } just runs

        sut.sendPredictions()

        verify(exactly = 8) { predictionPort.send(any()) }
        verify(exactly = 8) { predictionPort.send(match { it.direction == Direction.UP }) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} sets
     * the team name to {@code "BAAM"} on all sent predictions.
     *
     * <p>Test data - Single {@link MarketPrice} for symbol {@code "XTB"}, default strategy
     * returning {@code Direction.UP}.
     *
     * <p>Test expected result - Captured prediction has team name {@code "BAAM"}.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions sets team name correctly`() {
        priceRepository.store(marketPrice("XTB"))
        val capturedPredictions = mutableListOf<Prediction>()
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns true
        every { predictionPort.send(capture(capturedPredictions)) } just runs

        sut.sendPredictions()

        assertEquals("BAAM", capturedPredictions.first().team)
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} sends
     * predictions for all 8 instruments when all have price data available.
     *
     * <p>Test data - All 8 instruments stored in the repository, all strategies stubbed.
     *
     * <p>Test expected result - {@link PredictionPort#send} is invoked exactly 8 times.
     *
     * <p>Test type - Positive.
     */
    @Test
    fun `sendPredictions sends predictions for all 8 instruments when all have data`() {
        PredictionApplicationService.INSTRUMENTS.forEach { symbol ->
            priceRepository.store(marketPrice(symbol))
        }
        stubAllStrategies()

        sut.sendPredictions()

        verify(exactly = 8) { predictionPort.send(any()) }
    }

    /**
     * Test purpose - Verify that {@link PredictionApplicationService#sendPredictions} skips
     * sending a prediction when {@link SentPredictionRepository#tryMarkSent} returns {@code false},
     * indicating the prediction for that symbol and minute was already sent.
     *
     * <p>Test data - Single {@link MarketPrice} for symbol {@code "XTB"}, default strategy
     * returning {@code Direction.UP}, {@code tryMarkSent} returning {@code false}.
     *
     * <p>Test expected result - {@link PredictionPort#send} is never invoked.
     *
     * <p>Test type - Negative.
     */
    @Test
    fun `sendPredictions skips already sent predictions for same minute`() {
        priceRepository.store(marketPrice("XTB"))
        every { defaultStrategy.predict(any(), any()) } returns Direction.UP
        every { sentPredictionRepository.tryMarkSent(any(), any(), any()) } returns false

        sut.sendPredictions()

        verify(exactly = 0) { predictionPort.send(any()) }
    }
}
