# PRD — BAAM price-seer

**Project:** Hackathon Oracle predictor
**Team:** BAAM
**Date:** 2026-04-27
**Stack:** Kotlin · Spring Boot 4 · Kafka · Protobuf

---

## Goal

Build a service that:
1. Consumes real-time 1-minute OHLC candle data from Kafka topic `market-prices`
2. Runs a prediction strategy to decide UP or DOWN for each instrument
3. Publishes predictions to Kafka topic `predictions` within the first ~30 s of each minute
4. Maximises accuracy over the rolling 100-minute scoring window for all 8 instruments

---

## Feature 1 — Kafka Subscription to `market-prices`

### Summary

Subscribe to the `market-prices` Kafka topic, deserialise Protobuf messages, and store
the latest OHLC candle per instrument in memory for downstream use by prediction strategies.

### Kafka Details

| Property            | Value                    |
|---------------------|--------------------------|
| Broker              | `192.168.8.244:9092`     |
| Topic               | `market-prices`          |
| Format              | Protobuf (binary)        |
| Message rate        | ~8 messages/minute (one per instrument) |
| Message key         | instrument symbol (UTF-8 bytes) |
| Consumer group      | `priceseer-consumer`     |
| Auto offset reset   | `latest`                 |

### Protobuf Schema

```protobuf
syntax = "proto3";

package oracle;

message MarketPrice {
  string symbol    = 1;  // e.g. "BTC/USD"
  string timestamp = 2;  // ISO-8601 UTC, e.g. "2026-04-27T12:05:00Z"
  string interval  = 3;  // always "1min"
  double open      = 4;
  double high      = 5;
  double low       = 6;
  double close     = 7;
}
```

### Acceptance Criteria

- [ ] Spring Kafka `@KafkaListener` consumes from `market-prices` topic
- [ ] Raw bytes are deserialised into a `MarketPrice` proto (or equivalent Kotlin data class)
- [ ] Received price is stored in a thread-safe in-memory structure (`ConcurrentHashMap<String, MarketPrice>`) keyed by symbol
- [ ] On each received message, `PredictionService` is triggered to evaluate and send a prediction for that instrument
- [ ] Deserialization errors are caught and logged (message is skipped, no crash)
- [ ] Consumer group is `priceseer-consumer`, auto-offset-reset = `latest`
- [ ] Application logs symbol + timestamp + close price on each received message

### Configuration (`application.properties`)

```properties
spring.kafka.bootstrap-servers=192.168.8.244:9092
spring.kafka.consumer.group-id=priceseer-consumer
spring.kafka.consumer.auto-offset-reset=latest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer
```

### Implementation Notes

- The `oracle-proto` repo must be cloned and the generated Java/Kotlin sources added to the
  build, **or** the proto schema can be manually implemented as a lightweight Protobuf encoder
  (see hackathon guide for a raw-byte Python example — same approach works in Kotlin/JVM).
- `open` and `close` come from two consecutive 1-minute candles — they will never be equal
  (the Oracle guarantees a real spread).
- The consumer should NOT commit the offset until the prediction has been sent (or at least
  attempted) to avoid missed windows on restart.

---

## Feature 2 — Prediction Producer to `predictions`

### Summary

After each price event, produce a `Prediction` Protobuf message to the `predictions` topic
within the first 30 s of the current UTC minute.

### Kafka Details

| Property  | Value                |
|-----------|----------------------|
| Topic     | `predictions`        |
| Format    | Protobuf (binary)    |
| Message key | instrument symbol  |

### Protobuf Schema

```protobuf
syntax = "proto3";

package oracle;

enum Direction {
  UP   = 0;
  DOWN = 1;
}

message Prediction {
  string    team      = 1;  // max 30 chars — "BAAM"
  string    symbol    = 2;  // e.g. "BTC/USD"
  string    timestamp = 3;  // current UTC minute, e.g. "2026-04-27T12:05:00Z"
  Direction direction = 4;
}
```

### Acceptance Criteria

- [ ] `KafkaTemplate<String, ByteArray>` sends prediction immediately after price is received
- [ ] Timestamp is current UTC minute (seconds and millis zeroed, `Z` suffix)
- [ ] Team name is read from `app.team-name` property (default `BAAM`)
- [ ] Duplicate protection: track last sent minute per symbol; skip if already sent for this minute
- [ ] Send errors are logged with full context (symbol, direction, timestamp)

---

## Feature 3 — Prediction Strategy (Baseline)

### Summary

Implement a pluggable strategy interface with an initial **momentum** baseline strategy.

### Strategy Interface

```kotlin
interface PredictionStrategy {
    fun predict(symbol: String, history: List<MarketPrice>): Direction
}
```

### Baseline Strategy — Last-candle Momentum

Predict the same direction as the previous candle's movement:
- If `close > open` of the **received** candle → predict **UP** for next minute
- If `close <= open` → predict **DOWN**

This is the simplest possible strategy and establishes a ~50% baseline.

### Acceptance Criteria

- [ ] Strategy is selected via `app.strategy` property (default `momentum`)
- [ ] Strategy is Spring `@Component` implementing `PredictionStrategy`
- [ ] History of at least last 10 candles per symbol is kept in memory for advanced strategies
- [ ] Strategy result (UP/DOWN + reasoning) is logged at DEBUG level

---

## Feature 4 — Oracle API Health Check

### Summary

Poll `http://192.168.8.244:8080/api/status` on startup and log the result. Expose our
own `/actuator/health` endpoint showing Oracle connectivity.

### Acceptance Criteria

- [ ] On startup, call Oracle `/api/status` and log response
- [ ] If Oracle is unreachable, log a WARNING (do not crash)
- [ ] Spring Actuator health indicator for Oracle connectivity

---

## Non-Functional Requirements

| Requirement      | Target                                                 |
|------------------|--------------------------------------------------------|
| Prediction latency | < 5 s from receiving a price message                |
| Memory           | < 256 MB JVM heap                                      |
| Startup time     | < 30 s to first Kafka message consumed                 |
| Uptime           | No OOM or uncaught exceptions during the hackathon     |

---

---

## Feature 5 — Persistent Storage (PostgreSQL)

### Summary

Replace the in-memory `InMemoryPriceRepository` with a PostgreSQL-backed repository so that
price history and sent-prediction state survive application restarts. This allows the app to
resume scoring immediately after a crash or redeploy without losing the 100-minute candle window.

### Infrastructure

Local PostgreSQL instance via Docker Compose (port `15432` to avoid conflicts):

```bash
docker compose up -d          # start
docker compose down           # stop
docker compose down -v        # stop + wipe data
```

Connection string (local dev): `jdbc:postgresql://localhost:15432/priceseer`

### Database Schema

Two tables managed via Flyway migrations:

```sql
-- stores received OHLC candles, one row per symbol + timestamp
CREATE TABLE market_price (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20)      NOT NULL,
    ts          TIMESTAMPTZ      NOT NULL,
    interval    VARCHAR(10)      NOT NULL,
    open        DOUBLE PRECISION NOT NULL,
    high        DOUBLE PRECISION NOT NULL,
    low         DOUBLE PRECISION NOT NULL,
    close       DOUBLE PRECISION NOT NULL,
    received_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (symbol, ts)
);

CREATE INDEX ON market_price (symbol, ts DESC);

-- tracks last prediction sent per symbol + minute (dedup guard)
CREATE TABLE sent_prediction (
    id        BIGSERIAL PRIMARY KEY,
    symbol    VARCHAR(20)  NOT NULL,
    minute    TIMESTAMPTZ  NOT NULL,
    direction VARCHAR(10)  NOT NULL,
    sent_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (symbol, minute)
);
```

### Acceptance Criteria

- [ ] PostgreSQL datasource configured via `application.properties` (local) and Spring profile overrides (prod)
- [ ] Flyway runs migrations on startup; schema is created automatically
- [ ] `JdbcPriceRepository` implements `PriceRepository` port (replaces `InMemoryPriceRepository`)
  - `store(price)` upserts into `market_price` (conflict on `symbol, ts` → ignore)
  - `history(symbol)` returns last N candles ordered by `ts DESC` (N from `app.history-size`)
  - `latest(symbol)` returns the single most-recent candle
- [ ] `JdbcSentPredictionRepository` replaces the `ConcurrentHashMap` dedup in `PredictionApplicationService`
  - `tryMarkSent(symbol, minute)` inserts into `sent_prediction`; returns `true` if inserted, `false` on duplicate
- [ ] `PredictionApplicationService` uses the new dedup repository — no in-memory map
- [ ] Integration tests use Testcontainers `PostgreSQLContainer` (not the local Docker Compose instance)
- [ ] Health check (`/actuator/health`) includes datasource liveness

### Configuration

```properties
# application.properties (local dev)
spring.datasource.url=jdbc:postgresql://localhost:15432/priceseer
spring.datasource.username=priceseer
spring.datasource.password=priceseer
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
app.history-size=120
```

### Implementation Notes

- Use `spring-boot-starter-jdbc` + plain `JdbcTemplate` — no JPA/Hibernate overhead.
- `UNIQUE (symbol, ts)` + `INSERT ... ON CONFLICT DO NOTHING` makes `store` idempotent.
- `tryMarkSent` uses `INSERT ... ON CONFLICT DO NOTHING` and checks `UPDATE COUNT == 1`.
- Keep `InMemoryPriceRepository` as a `@Profile("test")` fallback so unit tests don't need a DB.
- Testcontainers `@ServiceConnection` auto-wires the datasource URL in integration tests.

### Source Layout (additions)

```
infrastructure/
  persistence/
    JdbcPriceRepository.kt          — implements PriceRepository
    JdbcSentPredictionRepository.kt — implements new SentPredictionRepository port
src/main/resources/
  db/migration/
    V1__create_market_price.sql
    V2__create_sent_prediction.sql
```

---

## Out of Scope (for now)

- Multiple consumer instances / partitioning
- ML-based prediction models (future feature)
- Frontend / dashboard (stretch goal)
