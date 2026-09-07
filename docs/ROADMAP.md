# 12-Month Technical Roadmap

Plan to evolve this PoC into a production-shaped Business Banking platform.
Quarters are planning horizons for a small team — not a technology wishlist.
Each initiative ties back to an explicit trade-off in
[ADR 0001](adr/0001-architecture-overview.md).

---

## Q1 — Core & Security

| Initiative | Business effect | Main technical risk |
| --- | --- | --- |
| **OAuth2/OIDC + API Gateway** (Spring Cloud Gateway replacing the Vite proxy) — closes [Direct Gateway vs. API Gateway](adr/0001-architecture-overview.md#1-direct-gateway-vs-api-gateway) | One secured edge for web/mobile clients; banking-grade access control before any money API is exposed beyond the demo stand. | Gateway misrouting or auth config can lock out all three services at once; needs careful cutover from direct `:8081–8083` access. |
| **Secrets management & rate limiting** at the edge (no secrets in git; throttle abuse on `/credits`, `/cards`, `/collateral`) — same ADR trade-off + security baseline deferred in the PoC | Reduces credential-leak and brute-force risk once the demo leaves a laptop. | Over-aggressive limits break legitimate credit+collateral flows; secret rotation must not break Gemini/`GEMINI_*` boot. |

---

## Q2 — Mobile BFF & KMP Core

| Initiative | Business effect | Main technical risk |
| --- | --- | --- |
| **Mobile BFF** (thin aggregation/session layer for native clients) — advances [Mobile Strategy](adr/0001-architecture-overview.md#4-mobile-strategy) | Mobile can ship UX-specific payloads without overloading core services or the React SPA contract. | BFF becomes a second “god” API if domain rules leak out of `credit-service` / `card-service`. |
| **KMP shared module** (annuity math, request validation shared by Android/iOS — and optionally JVM) — same [Mobile Strategy](adr/0001-architecture-overview.md#4-mobile-strategy) ADR | One calculation/validation source of truth across clients; fewer “app shows different payment than backend” incidents. | Dual toolchains/CI cost; divergence if KMP and `CreditService.annuityPayment` are not kept in lockstep. |

---

## Q3 — Risk Engine & Event Sourcing

| Initiative | Business effect | Main technical risk |
| --- | --- | --- |
| **Event-driven disbursement**: `card-service` consumes `CreditCalculatedEvent` (`bank.events` / `credit.calculated`) — closes [Event-Driven Async Gap](adr/0001-architecture-overview.md#2-event-driven-async-gap) | Credit approval credits the card without UI orchestration; fewer operator errors, path ready for straight-through processing. | Duplicate/out-of-order events can double-disburse without idempotent handlers + outbox/SAGA discipline. |
| **Richer scoring + event-sourced credit decision history** (append-only audit of score inputs/outputs) — builds on the same async/risk ADR track and Contract-First credit API | Auditable “why was this limit approved?” for bank compliance and dispute handling. | Event schema evolution and storage growth; replaying history must not mutate live card balances unexpectedly. |
| **Gemini Circuit Breaker** (e.g. Resilience4j) on top of retry + heuristic fallback — [AI Fallback Strategy](adr/0001-architecture-overview.md#3-ai-fallback-strategy) | Protects credit SLA when the Vision provider degrades under real traffic. | Bad breaker thresholds can pin the service on heuristic estimates too long (or flap open/closed). |

---

## Q4 — Observability & Scale

| Initiative | Business effect | Main technical risk |
| --- | --- | --- |
| **OpenTelemetry** tracing across gateway → card / credit / collateral (and RabbitMQ publish/consume) — needed because ADR chose **polyglot microservices + events** over a monolith | Faster incident localization when a calculate→disburse→collateral chain fails in production. | Trace noise/cost; poor sampling hides the rare money-path failures that matter most. |
| **DORA metrics** (deployment frequency, lead time, change failure rate, MTTR) — operationalises delivery after PoC shortcuts in ADR/Engineering Standards | Leadership sees whether the team ships safely, not just whether the demo works on stage. | Vanity metrics if pipelines are gamed; needs honest incident tagging. |
| **SonarQube + Pact** (static quality gates; consumer/provider contract tests beside OpenAPI) — reinforces [Contract-First](adr/0001-architecture-overview.md#decisions) and catches drift OpenAPI lint alone misses | Fewer silent breaking changes between services/clients; quality bar visible before release. | Noisy Sonar debt or brittle Pact suites that block merges without real risk — needs curated quality profiles. |
