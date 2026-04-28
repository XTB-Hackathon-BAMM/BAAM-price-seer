# BAAM-price-seer — Claude Context

## Project Overview

Hackathon project that subscribes to real-time financial price data from Kafka and sends
directional predictions (UP/DOWN) back to Kafka. The Oracle server scores predictions
against actual price movements every minute for 8 financial instruments.

**Team name:** BAAM (max 30 chars — used as `team` field in predictions)

## Tech Stack

- **Language:** Kotlin (JVM 17)
- **Framework:** Spring Boot 4.0.6
- **Build:** Gradle 9.4.1 — always use `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew <task>`
- **Messaging:** Spring Kafka (producer + consumer)
- **Serialization:** Protobuf (google-protobuf)
- **Test:** JUnit 5 + MockK + Testcontainers (Kafka)

## Infrastructure

| Service      | Address                          |
|--------------|----------------------------------|
| Oracle API   | `http://192.168.8.244:8080`      |
| Dashboard    | `http://192.168.8.244:8080/dashboard.html` |
| Swagger      | `http://192.168.8.244:8080/swagger.html` |
| Kafka broker | `192.168.8.244:9092`             |
| Kafka UI     | `http://192.168.8.244:8081`      |

## Kafka Topics

| Topic          | Direction | Format   | Key                |
|----------------|-----------|----------|--------------------|
| `market-prices`| CONSUME   | Protobuf | instrument symbol  |
| `predictions`  | PRODUCE   | Protobuf | instrument symbol  |

## Instruments (exactly 8)

`XTB`, `CDR`, `EUR/USD`, `GBP/JPY`, `BTC/USD`, `ETH/USD`, `XAU/USD`, `USD/JPY`

## Key Business Rules

- Predictions must arrive **within the first ~55 seconds** of each minute (before Oracle
  fetches the close price at ~second 60).
- **One prediction per instrument per minute** — duplicates are silently ignored, only
  the first counts.
- **Missing prediction = wrong** — the denominator is always 100 (last 100 minutes).
- Score: `close > open` → UP wins; `close < open` → DOWN wins; `|diff| < 0.0001` → UP wins.
- Overall accuracy = total correct / 800 (8 instruments × 100 minutes).

## Protobuf Schemas

Clone from `git@git.corp.xtb.com:xserver-team/warsztaty/oracle-proto.git`.

**MarketPrice fields:** `symbol`, `timestamp` (ISO-8601 UTC), `interval` ("1min"),
`open`, `high`, `low`, `close` (all doubles).

**Prediction fields:** `team` (1), `symbol` (2), `timestamp` (3), `direction` (4 — enum UP=0/DOWN=1).

## Oracle API Endpoints

```bash
curl http://192.168.8.244:8080/api/instruments       # list instruments
curl http://192.168.8.244:8080/api/prices/latest     # latest prices
curl http://192.168.8.244:8080/api/ranking           # overall ranking
curl http://192.168.8.244:8080/api/ranking/BAAM      # our ranking
curl http://192.168.8.244:8080/api/status            # service status
```

## Source Layout (DDD)

```
src/main/kotlin/pl/bamm/priceseer/
  PriceseerApplication.kt
  domain/
    model/           — MarketPrice, Prediction, Direction (pure data, no Spring)
    port/            — PredictionStrategy interface, PriceRepository interface
    service/         — PredictionDomainService (pure business logic, no infra deps)
  application/
    PredictionApplicationService.kt  — orchestrates domain + infra ports
  infrastructure/
    kafka/
      consumer/MarketPriceConsumer.kt — @KafkaListener, delegates to application layer
      producer/PredictionProducer.kt  — KafkaTemplate, implements port
    oracle/
      OracleApiClient.kt             — REST client for Oracle scoring server
    persistence/
      InMemoryPriceRepository.kt     — ConcurrentHashMap impl of PriceRepository
    config/
      KafkaConfig.kt
      AppConfig.kt
```

```
src/test/kotlin/pl/bamm/priceseer/
  domain/            — pure unit tests (no Spring, no Kafka)
  application/       — unit tests with MockK mocks
  infrastructure/
    kafka/           — @SpringBootTest + Testcontainers Kafka
    oracle/          — @SpringBootTest + WireMock/MockWebServer
```

## Build & Run

```bash
# Build
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build

# Run
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bootRun

# Tests
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test
```

## Architecture Rules

See `.claude/agents/architecture.md` for full DDD + clean code rules. Summary:

- **Domain layer** has zero Spring/Kafka/IO imports — plain Kotlin classes and interfaces only
- **Application layer** depends only on domain ports; never on infrastructure classes directly
- **Infrastructure layer** implements domain ports and owns all framework annotations
- Functions ≤ 20 lines; classes have a single responsibility; no primitive obsession in domain
- No comments except for non-obvious invariants; let naming speak

## Testing Rules

See `.claude/agents/testing.md` for full rules. Summary:

- **Unit tests** (`domain/`, `application/`): JUnit 5 + MockK, no Spring context, fast
- **Integration tests** (`infrastructure/`): `@SpringBootTest` + Testcontainers Kafka container
- Every public domain method has a unit test; every Kafka listener has an integration test
- Test class naming: `<Subject>Test` (unit) / `<Subject>IT` (integration)

## Sub-Agents

See `.claude/agents/` for specialized agents:
- `architecture` — DDD layer rules, clean code standards, package structure
- `testing` — unit test patterns (MockK), integration test patterns (Testcontainers)
- `kafka-consumer` — Kafka consumer implementation details
- `prediction-engine` — prediction strategy logic
- `oracle-api` — Oracle REST API integration

## Docs

- `docs/HACKATHON_PARTICIPANT_GUIDE.md` — full hackathon rules
- `docs/PRD.md` — product requirements and feature specs
