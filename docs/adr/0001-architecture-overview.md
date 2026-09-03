# ADR 0001: Event-Driven & Contract-First Microservices Architecture

## Context
The goal is to design a resilient and scalable core for Card Management and Credit Engine capabilities, mirroring real-world banking standards.

## Decision Drivers
* Parallel development across Web and Mobile? teams.
* Async event processing for heavy credit calculations without blocking client UI.
* Integration of Multimodal AI (Gemini Flash-Lite Vision) for alternative risk assessment (Collateral Evaluation).

## Decisions
1. **Contract-First Approach:** OpenAPI 3.0 specification (`openapi.yaml`) serves as the single source of truth for all client-server communication.
2. **JVM Polyglot Backend:**
   * **Card Service (Kotlin):** Handles card issuance, balances, and transactional rules (Redis + Postgres).
   * **Credit Service (Java 21):** Handles annuity math, scoring, and publishes events via **RabbitMQ**.
3. **Event-Driven Integration:** RabbitMQ is used as the message broker for async decoupling (`TransactionCreatedEvent`, `CreditCalculatedEvent`).
4. **AI-Assisted Evaluation:** Integration with the `gemini-3.1-flash-lite` Vision API to parse images and recommend credit limits based on collateral items.
   * The model id is externalised via `GEMINI_MODEL`, so upgrades require no code change.
   * Gemini 1.5 and 2.5 generations are no longer served for newly issued API keys, so `gemini-3.1-flash-lite` is the baseline for this project.
   * Collateral Service degrades gracefully: transient failures (`5xx`, `429`, transport errors) are retried with exponential backoff, and once retries are exhausted — or on non-retryable `400`/`404` — it logs a WARN and returns a heuristic collateral estimate instead of failing the credit flow.

## Status
Accepted.