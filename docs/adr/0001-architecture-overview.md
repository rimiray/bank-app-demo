# ADR 0001: Event-Driven & Contract-First Microservices Architecture

## Context
The goal is to design a resilient and scalable core for Card Management and Credit Engine capabilities, mirroring real-world banking standards.

## Decision Drivers
* Parallel development across Web and Mobile? teams.
* Async event processing for heavy credit calculations without blocking client UI.
* Integration of Multimodal AI (Gemini Flash Vision) for alternative risk assessment (Collateral Evaluation).

## Decisions
1. **Contract-First Approach:** OpenAPI 3.0 specification (`openapi.yaml`) serves as the single source of truth for all client-server communication.
2. **JVM Polyglot Backend:**
   * **Card Service (Kotlin):** Handles card issuance, balances, and transactional rules (Redis + Postgres).
   * **Credit Service (Java 21):** Handles annuity math, scoring, and publishes events via **RabbitMQ**.
3. **Event-Driven Integration:** RabbitMQ is used as the message broker for async decoupling (`TransactionCreatedEvent`, `CreditCalculatedEvent`).
4. **AI-Assisted Evaluation:** Integration with Gemini 1.5 Flash Vision API to parse images and recommend credit limits based on collateral items.

## Status
Accepted.