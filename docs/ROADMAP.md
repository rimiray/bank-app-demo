# Product & Architecture Roadmap

Quarterly targets for evolving this PoC toward production-shaped banking platform capabilities.
Dates are planning horizons, not hard delivery commitments.

## Q1 — Edge & API composition

- Introduce an **API Gateway** (e.g. Spring Cloud Gateway) in front of `card-service`,
  `credit-service`, and `ai-collateral-service`.
- Move cross-cutting concerns (routing, authn/authz, rate limits, request IDs) out of the
  Vite dev proxy and into the edge layer.
- Keep OpenAPI (`docs/api/openapi.yaml`) as the contract source of truth behind the gateway.

## Q2 — Mobile strategy (KMP)

- Evaluate **Kotlin Multiplatform (KMP)** for a shared domain/client module used by Android/iOS
  (and optionally shared validation DTOs with the JVM backend).
- Until then, continue with the **Mobile-First REST API + PWA/BFF** path: the existing OpenAPI
  contract is already mobile-consumable; the React frontend can harden into a PWA without a
  separate native codebase.
- Decision gate: invest in KMP once there is a concrete second client (native app) that would
  otherwise duplicate business rules.

## Q3 — Event-driven completion & Event Sourcing

- Close the async gap: add a **card-service consumer** for `CreditCalculatedEvent`
  (`bank.events` / `credit.calculated`) so credit disbursement is driven by the broker, not by
  UI orchestration (`calculate` → `apply-credit`).
- Introduce a lightweight **SAGA / outbox** pattern for credit approval → card balance update
  with idempotent handlers and compensating actions on failure.
- Evaluate **Event Sourcing** (or at least an append-only audit log of monetary events) for
  card balance and debt mutations once volume and audit requirements justify the operational cost.
- Harden third-party AI calls with a **Circuit Breaker** (e.g. Resilience4j) in front of Gemini
  once real outage/latency patterns or an availability SLA justify it beyond retry + heuristic
  fallback.
