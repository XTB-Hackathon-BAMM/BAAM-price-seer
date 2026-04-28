# BAMM-price-seer 🔮

## Description

A vibe-coded hackathon project built at **XTB** to explore agentic AI-assisted development.
The application subscribes to real-time financial price data from Kafka and sends directional predictions
(UP / DOWN) back to Kafka. An Oracle server scores predictions against actual price movements every minute
across 8 financial instruments.

The entire codebase — backend, frontend, tests, infrastructure — was built collaboratively with AI coding agents
as part of an internal hackathon aimed at familiarizing the team with the new agentic development workflow.

### 📊 Tracked instruments

| Category    | Instruments                      |
|-------------|----------------------------------|
| Crypto      | `BTC/USD`, `ETH/USD`             |
| Forex       | `EUR/USD`, `GBP/JPY`, `USD/JPY`  |
| Stocks      | `XTB`, `CDR`                     |
| Commodities | `XAU/USD`                        |

### 🧠 Prediction strategies

Each instrument category has a tailored prediction strategy using technical analysis indicators:
- **Crypto** — volatility regime detection, ATR-filtered momentum, RSI, Doji candle detection, BTC as a leading indicator for ETH.
- **Forex** — per-pair sub-strategies with session-awareness (Asian/NY), counter-trend logic, and stock-market risk proxies.
- **Gold** — PM fix counter-trend, SMA trend filter, and risk-off proxy during the New York session.
- **Stocks** — session-phase logic (opening gap, morning cross-signal, lunch mean-reversion, closing momentum).
- **Momentum** — simple last-candle direction fallback.

### 🏆 Scoring rules

- One prediction per instrument per minute — duplicates are silently ignored, only the first counts.
- Missing prediction counts as wrong — the denominator is always 100 (last 100 minutes).
- `close > open` -> UP wins; `close < open` -> DOWN wins; `|diff| < 0.0001` -> UP wins.
- Overall accuracy = total correct / 800 (8 instruments x 100 minutes).

## 🚀 Getting Started

These instructions will get you a copy of the project up and running on your local machine
for development and testing purposes.

### Prerequisites

- [Java 21](https://openjdk.org/projects/jdk/21/) (JDK, for building the backend)
- [Docker](https://www.docker.com/get-started) (for PostgreSQL via Docker Compose)
- [Node.js 20+](https://nodejs.org/) (for the React frontend)
- Network access to the hackathon Oracle server (`192.168.8.244`) for live data

### Cloning

```bash
git clone git@git.corp.xtb.com:xserver-team/warsztaty/BAAM-price-seer.git
```

Protobuf schemas live in a separate repository:
```bash
git clone git@git.corp.xtb.com:xserver-team/warsztaty/oracle-proto.git
```

### Building

Backend (Kotlin / Spring Boot):
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build
```

Frontend (React / Vite):
```bash
cd frontend
npm install
npm run build
```

### Running the application

#### 1. Start the database

```bash
docker compose up -d
```
This starts a PostgreSQL 16 container on port `15432` with Flyway-managed schema migrations.

#### 2. Start the backend

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew bootRun
```
The backend connects to the Oracle Kafka broker at `192.168.8.244:9092`, consumes prices from the
`market-prices` topic, and produces predictions to the `predictions` topic every minute.

#### 3. Start the frontend

```bash
cd frontend
npm run dev
```
The Vite dev server starts at `http://localhost:5173` and proxies `/api/*` requests to the Oracle server.
You must be on the hackathon network for live data.

### Running tests

Unit tests (no infrastructure required):
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew test
```

Integration tests use Testcontainers (Kafka on port `9093`, PostgreSQL on port `15433`),
so they can run safely alongside Docker Compose services without port conflicts.

## 🏗 Architecture

The backend follows a **DDD / Clean Architecture** layered structure:

```
src/main/kotlin/pl/bamm/priceseer/
  PriceseerApplication.kt              — Spring Boot entry point
  domain/
    model/                              — MarketPrice, Prediction, Direction
    port/                               — PredictionStrategy, PriceRepository, PredictionPort, SentPredictionRepository
  application/
    PredictionApplicationService.kt     — orchestrates price storage + prediction dispatch
    strategy/                           — CryptoStrategy, ForexStrategy, GoldStrategy, StockStrategy, MomentumStrategy
  infrastructure/
    kafka/consumer/                     — MarketPriceConsumer, ProtobufDecoder
    kafka/producer/                     — PredictionProducer, ProtobufEncoder
    persistence/                        — JdbcPriceRepository, JdbcSentPredictionRepository, InMemory* variants
    scheduling/                         — PredictionScheduler (cron-triggered every minute)
    config/                             — ClockConfig
```

- **Domain layer** has zero Spring/Kafka/IO imports — pure Kotlin classes and interfaces only.
- **Application layer** depends only on domain ports; never on infrastructure classes directly.
- **Infrastructure layer** implements domain ports and owns all framework annotations.

### Frontend

A React + TypeScript dashboard built with Vite, featuring:
- **Live ranking** — real-time team leaderboard from the Oracle API
- **Instrument grid** — per-instrument cards showing latest prices and prediction status
- **Price timeline** — candlestick chart with prediction markers (powered by Recharts)
- **Hype meter** — accuracy-driven hype level with audio crossfade between `chill.mp3`, `hype.mp3`, and `max-hype.mp3`

## 🌐 Infrastructure

| Service      | Address                                      |
|--------------|----------------------------------------------|
| Oracle API   | `http://192.168.8.244:8080`                  |
| Dashboard    | `http://192.168.8.244:8080/dashboard.html`   |
| Swagger      | `http://192.168.8.244:8080/swagger.html`     |
| Kafka broker | `192.168.8.244:9092`                         |
| Kafka UI     | `http://192.168.8.244:8081`                  |
| PostgreSQL   | `localhost:15432` (Docker Compose)            |
| Frontend     | `http://localhost:5173` (Vite dev)            |

### Oracle API endpoints

```bash
curl http://192.168.8.244:8080/api/instruments       # list instruments
curl http://192.168.8.244:8080/api/prices/latest      # latest prices
curl http://192.168.8.244:8080/api/ranking             # overall ranking
curl http://192.168.8.244:8080/api/ranking/BAMM        # our team ranking
curl http://192.168.8.244:8080/api/status              # service status
```

## ⚙️ Configuration

### Application properties

Key settings in `src/main/resources/application.properties`:

| Property                         | Default                                       | Description                        |
|----------------------------------|-----------------------------------------------|------------------------------------|
| `spring.kafka.bootstrap-servers` | `192.168.8.244:9092`                          | Oracle Kafka broker                |
| `spring.datasource.url`         | `jdbc:postgresql://localhost:15432/priceseer`  | PostgreSQL connection              |
| `app.team-name`                  | `Bamm`                                        | Team name sent with predictions    |
| `app.history-size`               | `120`                                         | Number of candles kept per instrument |

### Database

Schema is managed by Flyway migrations in `src/main/resources/db/migration/`:
- `V1__create_market_price.sql` — OHLC candle storage with upsert support
- `V2__create_sent_prediction.sql` — per-minute deduplication tracking

To connect directly to the running database:
```bash
PGPASSWORD=priceseer psql -h localhost -p 15432 -U priceseer -d priceseer
```

### Profiles

| Profile       | Effect                                                    |
|---------------|-----------------------------------------------------------|
| *(default)*   | JDBC repositories + Flyway + PostgreSQL                   |
| `in-memory`   | In-memory repositories, no database required              |
| `test`        | Testcontainers ports (Kafka `9093`, PostgreSQL `15433`)   |

## 🛠 Built with

- [Kotlin](https://kotlinlang.org/) — modern JVM language with concise syntax and null safety
- [Spring Boot 4](https://spring.io/projects/spring-boot) — production-ready application framework
- [Spring Kafka](https://spring.io/projects/spring-kafka) — Kafka producer/consumer integration
- [Protocol Buffers](https://protobuf.dev/) — efficient binary serialization for Kafka messages
- [PostgreSQL 16](https://www.postgresql.org/) — relational database for price and prediction storage
- [Flyway](https://flywaydb.org/) — database migration management
- [React 19](https://react.dev/) — frontend UI library
- [Vite](https://vite.dev/) — fast frontend build tool
- [Recharts](https://recharts.org/) — composable charting library for React
- [TanStack Query](https://tanstack.com/query) — data fetching and caching for React
- [Testcontainers](https://testcontainers.com/) — throwaway Docker containers for integration tests
- [MockK](https://mockk.io/) — Kotlin-first mocking library
- [Docker](https://www.docker.com/) — containerized PostgreSQL for local development

## 👥 Developers

Built with AI at the XTB Hackathon by team **BAMM**:

- **Bartlomiej Perkowski** — [bartlomiej-perkowski-xtb](https://git.corp.xtb.com/bartlomiej-perkowski-xtb)
- **Anastazja Trusinska** — [anastazja-trusinska-xtb](https://git.corp.xtb.com/anastazja-trusinska-xtb)
- **Michal Kusmidrowicz** — [ninjarlz](https://github.com/ninjarlz)
- **Michal Bareja** — [jrmichael](https://git.corp.xtb.com/jrmichael)

> The team name **BAMM** comes from the first letters of the authors' names. The project was vibe-coded
> end-to-end with agentic AI assistants — from architecture decisions through strategy implementation
> to the React dashboard.

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
