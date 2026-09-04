# bank-app-demo

Event-driven microservices demo. Contract-first: `docs/api/openapi.yaml` is the source of truth
for all endpoints — see @docs/api/openapi.yaml and @docs/adr/0001-architecture-overview.md.

## Stack

Java 21 (Temurin), Spring Boot 3.4.5, Gradle 8.13 wrapper per service (`.\gradlew.bat` on Windows).
Infra via `docker-compose.yml`: PostgreSQL `bank_db`, Redis, RabbitMQ.
Secrets live in `.env` (gitignored); `.env.example` is the template.

## Services

| Path | Language | Port | Base route |
| --- | --- | --- | --- |
| `services/card-service` | Kotlin | 8081 | `/api/v1/cards` |
| `services/credit-service` | Java + Lombok | 8082 | `/api/v1/credits` |
| `services/ai-collateral-service` | Java + Lombok | 8083 | `/api/v1/collateral` |

## Conventions

- Package root is `com.bankapp.<servicename>`, layered as `controller` / `service` / `repository` /
  `domain` / `dto` / `config` / `exception`.
- Every service has a `@RestControllerAdvice` returning a compact JSON error body
  (`status`, `error`, `message`). Never leak upstream provider payloads or stack traces to clients.
- Money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`.

## Service-specific gotchas

- **card-service**: Kotlin data classes are `final`, so the Redis cache needs its own `ObjectMapper`
  with default typing enabled for `com.bankapp.*`; otherwise cached lists deserialize to
  `LinkedHashMap` and `GET /api/v1/cards` returns 500. HTTP JSON must stay free of `@class`.
- **credit-service**: annuity at 8.5% base rate, collateral adds 70% of its value to the approved
  limit, `APPROVED` when payment <= 40% of income. Publishes `CreditCalculatedEvent` to exchange
  `bank.events` with routing key `credit.calculated`.
- **ai-collateral-service**: Spring Boot does not read `.env`, so an `EnvironmentPostProcessor`
  loads it. Gemini model and retry budget are externalised (`GEMINI_MODEL`, `GEMINI_MAX_ATTEMPTS`,
  `GEMINI_RETRY_DELAY_MS`, `GEMINI_FALLBACK_VALUE_EUR`). Transient failures (5xx, 429, network) are
  retried with exponential backoff, then degrade to a heuristic estimate with a WARN log.

## Commands

```powershell
cd services/<service-name>
.\gradlew.bat compileJava   # or compileKotlin
.\gradlew.bat bootRun
```

`bootRun` in `ai-collateral-service` injects the repo-root `.env` into the process environment.
