# pricing-position-service — event-driven position keeping & option pricing

[![CI](https://github.com/Chiheb-AouledAhmed/pricing-position-service/actions/workflows/ci.yml/badge.svg)](https://github.com/Chiheb-AouledAhmed/pricing-position-service/actions/workflows/ci.yml)

A small **Java 21 / Spring Boot 3** microservice of the kind found in a capital-markets back end:

- it **consumes executed trades from Kafka** and keeps **net positions** per instrument
  (weighted average cost, realised and unrealised P&L);
- it **re-publishes every position change** on a compacted Kafka topic, so downstream systems
  (risk, P&L, UI) can rebuild the latest state;
- it **prices European options** (Black-Scholes-Merton with dividend yield, full greeks) over REST,
  with a **Redis cache** in front of the pricer.

```
 execution system ──► [trades] ──► TradeConsumer ──► PositionService ──► [position-updates] (compacted)
                                                          │
                          REST  GET /api/positions ◄──────┘
                          REST  POST /api/pricing/options ──► PricingService (@Cacheable, Redis) ──► BlackScholes
```

## Design choices

| Concern | Choice | Why |
|---|---|---|
| Delivery semantics | Kafka at-least-once + **idempotent** processing (set of applied trade ids) | a redelivered trade must not be booked twice |
| Ordering | trades keyed by instrument | all trades of an instrument land on one partition and are applied in order |
| Concurrency | immutable `Position` record + `ConcurrentHashMap.compute` | atomic per instrument, different instruments in parallel, no explicit locks |
| Money | `BigDecimal` (scale 8, HALF_EVEN) for positions and P&L | no binary floating-point error in booked amounts |
| Pricing maths | `double`, closed-form greeks, normal CDF via `erfc` (~1e-15) | pricing is an approximation anyway; speed matters |
| Framework coupling | domain and pricing packages are plain Java | the business logic is unit-tested without Spring or Kafka |
| Bad messages | malformed trade is logged, counted and skipped | one poison message must not block the partition |
| Errors over REST | RFC 7807 `ProblemDetail`, 400 on invalid input | standard, machine-readable errors |

**Position maths (weighted average cost).** A trade in the direction of the position updates the
average cost; a trade against it realises `(price − averageCost) × closedQty × direction` and leaves
the average cost unchanged; a trade larger than the position flips it, and the remainder opens at the
trade price.

## API

```bash
# price an option
curl -s localhost:8080/api/pricing/options -H 'Content-Type: application/json' -d '{
  "type":"CALL","spot":100,"strike":100,"maturityYears":1,"rate":0.05,"dividendYield":0,"volatility":0.2}'
# {"price":10.4505835...,"delta":0.6368...,"gamma":0.0187...,"vega":0.3752...,"theta":-0.0176...,"rho":0.5323...,...}

# send a trade (published on Kafka, applied asynchronously like any upstream trade)
curl -s localhost:8080/api/trades -H 'Content-Type: application/json' -d '{
  "tradeId":"T-1","instrument":"FR0000120271","side":"BUY","quantity":300,"price":60.10}'

curl -s localhost:8080/api/positions
curl -s localhost:8080/api/positions/FR0000120271
```

`vega` and `rho` are per 1 point (1%), `theta` per calendar day.

## Run

```bash
docker compose up --build        # Kafka (KRaft) + Redis + the service on :8080
```

Without Docker: start Kafka and Redis locally, then `mvn spring-boot:run`
(`KAFKA_BOOTSTRAP`, `REDIS_HOST`, `CACHE_TYPE=simple` to run without Redis).

## Tests

```bash
mvn verify
```

- **BlackScholesTest** — reference prices, put-call parity (with dividends), every greek checked
  against finite differences, input validation.
- **PositionTest** — average cost, realised P&L, position flip, short positions.
- **PositionServiceTest** — duplicate trade ignored; 8 threads × 5,000 trades on 4 instruments,
  nothing lost.
- **PricingControllerTest** (`@WebMvcTest`) — JSON contract and 400 errors.
- **TradeFlowIntegrationTest** (`@SpringBootTest` + `@EmbeddedKafka`) — trades go through a real
  Kafka broker, a redelivered trade and a malformed message are handled, positions are served over
  REST and re-published on `position-updates`.

## What a production version would add

Persisting the applied-trade ids and positions (today they are in memory: after a restart they must
be rebuilt by replaying the trades topic from the beginning), a dead-letter topic, schema registry (Avro/Protobuf) instead of
JSON, market-data feed for unrealised P&L, and metrics on consumer lag.

## License

MIT
