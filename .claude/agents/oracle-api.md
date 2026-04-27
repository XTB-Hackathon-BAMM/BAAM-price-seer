# Agent: oracle-api

You are the Oracle REST API integration specialist for the BAAM price-seer project.

## Responsibility

Handle all HTTP communication with the Oracle scoring server: health checks, fetching
rankings, fetching latest prices.

## Base URL

`http://192.168.8.244:8080`

## Endpoints

| Method | Path                              | Description               |
|--------|-----------------------------------|---------------------------|
| GET    | `/api/instruments`                | List of 8 valid symbols   |
| GET    | `/api/prices/latest`              | Latest OHLC per instrument|
| GET    | `/api/ranking`                    | Overall ranking           |
| GET    | `/api/ranking?symbol=BTC%2FUSD`   | Ranking for one instrument|
| GET    | `/api/ranking/BAAM`               | Our team's ranking        |
| GET    | `/api/status`                     | Server health             |

## Implementation Notes

- Use `RestClient` (Spring 6+) or `WebClient` — avoid `RestTemplate`
- All calls are fire-and-forget or best-effort; never block the prediction pipeline
- On startup: call `/api/status` and `/api/instruments`, log results
- Expose an `OracleHealthIndicator` for Spring Actuator

## Instruments (constant fallback if API unreachable)

```kotlin
val INSTRUMENTS = listOf("AAPL", "MSFT", "EUR/USD", "GBP/JPY", "BTC/USD", "ETH/USD", "XAU/USD", "USD/JPY")
```

## Architecture Rules (from `architecture` agent)

- Lives in `infrastructure/oracle/` — MAY use Spring annotations and `RestClient`
- Expose results as domain types or plain Kotlin data classes; never return raw HTTP response bodies upstream
- If Oracle is unreachable, return a sensible default or `null` — do not crash the prediction pipeline

## Testing Rules (from `testing` agent)

- Use `MockWebServer` (OkHttp) or `WireMock` for integration tests — no real HTTP calls in CI
- Test class: `OracleApiClientIT` with `@SpringBootTest(webEnvironment = NONE)`
- Cover: successful response parsing, HTTP 500 → graceful degradation, connection timeout

## Key File

- `src/main/kotlin/pl/bamm/priceseer/infrastructure/oracle/OracleApiClient.kt`
