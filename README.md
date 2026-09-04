[![CI](https://github.com/rimiray/bank-app-demo/actions/workflows/ci.yml/badge.svg)](https://github.com/rimiray/bank-app-demo/actions/workflows/ci.yml)

# bank-app-demo
Event-Driven Microservices Architecture Demo (Spring Boot Java/Kotlin, React, RabbitMQ, Redis, PostgreSQL, Gemini API) for engineering practice.

## Services

| Service | Stack | Port | Responsibility |
| --- | --- | --- | --- |
| `card-service` | Kotlin, Spring Boot 3 | 8081 | Card issuance, balances, transactional rules (Postgres + Redis) |
| `credit-service` | Java 21, Spring Boot 3 | 8082 | Annuity math, scoring, `CreditCalculatedEvent` via RabbitMQ |
| `ai-collateral-service` | Java 21, Spring Boot 3 | 8083 | Collateral photo appraisal via `gemini-3.1-flash-lite` Vision API |

## Getting started

```bash
cp .env.example .env   # then set GEMINI_API_KEY
docker compose up -d
```

Each service is built with its own Gradle wrapper:

```bash
cd services/<service-name>
./gradlew bootRun
```

## Configuration

The Gemini model is externalised, so switching generations needs no code change:

```
GEMINI_API_KEY=<your key>
GEMINI_MODEL=gemini-3.1-flash-lite
```

`ai-collateral-service` retries transient Gemini failures (`5xx`, `429`, network errors) with
exponential backoff — `GEMINI_MAX_ATTEMPTS` (default 3) and `GEMINI_RETRY_DELAY_MS` (default 800).
Once retries are exhausted it logs a WARN and falls back to a heuristic collateral estimate rather
than failing the request.
