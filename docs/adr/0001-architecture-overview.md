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

## Explicit Architectural Trade-offs & Decisions

These choices are deliberate for a teaching PoC. Each subsection states what we do today,
why that is the right trade-off *now*, and which roadmap quarter revisits it.

### 1. Direct Gateway vs. API Gateway

**Current decision:** There is no Spring Cloud Gateway (or Kong/NGINX edge) in the demo
stand. Browsers talk to backends through the **Vite dev proxy** (`frontend/vite.config.ts`)
and, in a local run, via direct service ports (`8081` / `8082` / `8083`).

**Why now:** An API Gateway adds another JVM process, routing config, and failure mode. For a
PoC we optimise for “clone → `docker compose up` → `bootRun` / `npm run dev`” with minimal
infra cost. Cross-cutting concerns (auth, rate limits, unified base path) are not yet required
to demonstrate cards, credit math, RabbitMQ publish, or Gemini collateral evaluation.

**When it changes:** Introduce a real edge gateway in **[Roadmap Q1](../ROADMAP.md#q1--edge--api-composition)** —
routing, authn/authz, and request correlation move out of the Vite proxy into Spring Cloud
Gateway (or equivalent) while OpenAPI remains the contract source of truth.

### 2. Event-Driven Async Gap

**Current decision:** `credit-service` publishes `CreditCalculatedEvent` to RabbitMQ exchange
`bank.events` with routing key `credit.calculated`, but **card balance / limit changes on
credit issuance are not consumed asynchronously**. The demo path is UI orchestration:
`POST /api/v1/credits/calculate` → operator confirms → `POST /api/v1/cards/{id}/apply-credit`.
There is **no distributed SAGA** tying approval to disbursement.

**Why now:** A full credit→card SAGA (outbox, idempotent consumer, compensations) would obscure
the learning goals—annuity math, OpenAPI contract, and a visible event publish—behind
distributed-transaction machinery. Explicit UI steps keep the demo scenario predictable and
easy to step through in a review.

**When it changes:** In **[Roadmap Q3](../ROADMAP.md#q3--event-driven-completion--event-sourcing)**
add a `card-service` consumer for `CreditCalculatedEvent`, then harden with outbox/SAGA (and
evaluate Event Sourcing for monetary mutations) so production disbursement is broker-driven,
not UI-driven.

### 3. AI Fallback Strategy

**Current decision:** Collateral vision uses **`gemini-3.1-flash-lite`** (model id via
`GEMINI_MODEL`). Resilience today is **retry-with-exponential-backoff** on transient failures
(`5xx`, `429`, network), then a **heuristic collateral estimate** with a WARN log—so the credit
flow stays available when Gemini is down or misconfigured.

**Why this model:** At development time, newly issued Google AI API keys could not reliably use
older 2.5-generation (or earlier) Flash models; `gemini-3.1-flash-lite` is the current
generation available for new projects and is externalised so upgrades need no code change.

**Why no Circuit Breaker yet:** Retry + heuristic fallback is enough for a low-traffic PoC with
no SLA pressure. A Circuit Breaker (e.g. **Resilience4j**) adds state, tuning, and ops surface
we have not needed under demo load.

**When it changes:** Add a Circuit Breaker (e.g. Resilience4j) in
**[Roadmap Q3](../ROADMAP.md#q3--event-driven-completion--event-sourcing)** once we face real
provider outage/latency patterns or commit to an availability SLA for collateral evaluation.
Retry + heuristic fallback remain the first resilience tier; the model stays configurable via
`GEMINI_*`.

### 4. Mobile Strategy

**Current decision:** **Mobile-First API & PWA/BFF**, not Kotlin Multiplatform from day one.
Backends already expose a clean REST/OpenAPI contract; the React SPA can evolve into a PWA (or
sit behind a thin BFF) without a separate native app.

**Why now:** Standing up KMP shared modules (Android/iOS + possible JVM sharing) is real
architectural weight—toolchains, CI matrix, API surface ownership. For a PoC with one web
client, that complexity would not buy a second shippable surface.

**When it changes:** Treat KMP as a strategic initiative in
**[Roadmap Q2](../ROADMAP.md#q2--mobile-strategy-kmp)** once a concrete native client would
otherwise duplicate domain rules; until then, keep one contract and one web client path.
