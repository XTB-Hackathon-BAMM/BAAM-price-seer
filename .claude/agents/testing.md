# Agent: testing

You are the testing specialist for the BAAM price-seer project.
Write and review tests following the rules below. Every feature in the PRD must have
corresponding tests before it is considered done.

## Test Types and Location

| Type             | Suffix | Location                      | Context        |
|------------------|--------|-------------------------------|----------------|
| Unit             | `Test` | `domain/`, `application/`     | None (pure JVM)|
| Integration      | `IT`   | `infrastructure/kafka/`       | Testcontainers |
| Integration      | `IT`   | `infrastructure/oracle/`      | MockWebServer  |

## Dependencies

```kotlin
// MockK — Kotlin-native mocking
testImplementation("io.mockk:mockk:1.14.0")
// SpringMockK — @MockkBean for Spring context
testImplementation("com.ninja-squad:springmockk:4.0.2")
// Testcontainers
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:kafka")
testImplementation("org.testcontainers:junit-jupiter")
```

---

## Unit Tests (domain + application)

### Rules

- No Spring context (`@SpringBootTest` forbidden in `domain/` and `application/` tests)
- Use **MockK** for all mocking: `val repo = mockk<PriceRepository>()`
- Use `every { }` for stubbing, `verify { }` for interaction assertions
- One assertion concept per test method
- Arrange / Act / Assert structure with blank lines separating sections
- Test method naming: `fun \`given X when Y then Z\`()` — backtick descriptive names

### Example — domain service unit test

```kotlin
class PredictionDomainServiceTest {

    private val strategy = mockk<PredictionStrategy>()
    private val sut = PredictionDomainService(strategy)

    @Test
    fun `given close greater than open when predict then returns UP`() {
        val price = marketPrice(symbol = "BTC/USD", open = 50_000.0, close = 51_000.0)
        every { strategy.predict(any(), any()) } returns Direction.UP

        val result = sut.predict(price, emptyList())

        result shouldBe Direction.UP
    }
}
```

### Example — application service unit test

```kotlin
@ExtendWith(MockKExtension::class)
class PredictionApplicationServiceTest {

    @MockK lateinit var priceRepository: PriceRepository
    @MockK lateinit var strategy: PredictionStrategy
    @MockK lateinit var predictionPort: PredictionPort
    @InjectMockKs lateinit var sut: PredictionApplicationService

    @Test
    fun `given valid price when onPriceReceived then sends prediction`() {
        val price = marketPrice("AAPL")
        every { priceRepository.store(price) } just runs
        every { priceRepository.history("AAPL") } returns listOf(price)
        every { strategy.predict("AAPL", any()) } returns Direction.DOWN
        every { predictionPort.send(any()) } just runs

        sut.onPriceReceived(price)

        verify(exactly = 1) { predictionPort.send(match { it.direction == Direction.DOWN }) }
    }
}
```

---

## Integration Tests (Kafka — Testcontainers)

### Rules

- Use `@SpringBootTest` + `@Testcontainers`
- Use `@ServiceConnection` on the `KafkaContainer` bean to auto-wire broker URL
- Test the full round-trip: produce a `MarketPrice` to `market-prices` → assert `predictions` topic receives a message
- Use `KafkaTestUtils` or `ConsumerRecords` polling with a timeout (max 10 s)
- Testcontainers reuse: annotate container with `@Container` at class level (shared per class)

### Base class pattern

```kotlin
@SpringBootTest
@Testcontainers
abstract class KafkaIntegrationBase {

    companion object {
        @Container
        @ServiceConnection
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
    }
}
```

### Example — consumer integration test

```kotlin
class MarketPriceConsumerIT : KafkaIntegrationBase() {

    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>
    @MockkBean lateinit var predictionApplicationService: PredictionApplicationService

    @Test
    fun `given valid protobuf message when consumed then triggers application service`() {
        val protoBytes = buildMarketPriceProto("BTC/USD", open = 50_000.0, close = 51_000.0)
        every { predictionApplicationService.onPriceReceived(any()) } just runs

        kafkaTemplate.send("market-prices", "BTC/USD", protoBytes).get()

        verify(timeout = 10_000) { predictionApplicationService.onPriceReceived(any()) }
    }
}
```

### Example — producer integration test

```kotlin
class PredictionProducerIT : KafkaIntegrationBase() {

    @Autowired lateinit var predictionProducer: PredictionProducer

    @Test
    fun `given prediction when send then message appears on predictions topic`() {
        val prediction = Prediction(team = "BAAM", symbol = "AAPL", direction = Direction.UP, timestamp = "2026-04-27T12:05:00Z")

        predictionProducer.send(prediction)

        val records = pollRecords(topic = "predictions", maxWaitMs = 10_000)
        records shouldHaveSize 1
        records.first().key() shouldBe "AAPL"
    }
}
```

---

## Test Fixtures

Keep shared test object builders in `src/test/kotlin/pl/bamm/priceseer/fixtures/`:

```kotlin
fun marketPrice(
    symbol: String = "BTC/USD",
    open: Double = 50_000.0,
    close: Double = 51_000.0,
    timestamp: String = "2026-04-27T12:05:00Z",
) = MarketPrice(symbol = symbol, open = open, close = close,
                high = close, low = open, timestamp = timestamp, interval = "1min")
```

---

## Coverage Expectations

| Layer         | Minimum Coverage |
|---------------|-----------------|
| domain/       | 90% line        |
| application/  | 85% line        |
| infrastructure| 1 IT per adapter |
