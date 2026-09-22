# ZBK bank-app-demo — Technical Deep Dive

This document is a self-contained engineering brief for a senior engineer (or another LLM)
who must reason about the codebase without guessing. Every version, path, routing key, and
default below was taken from the repository as it exists now. Where README or ADR disagree
with the code, the discrepancy is called out explicitly.

Companion docs (do not treat this as a replacement):

| Doc | Role |
| --- | --- |
| [README.md](../README.md) | Showcase / quick start |
| [docs/adr/0001-architecture-overview.md](adr/0001-architecture-overview.md) | Decision log + trade-offs |
| [docs/api/openapi.yaml](api/openapi.yaml) | HTTP contract source of truth |
| [docs/DEPLOY.md](DEPLOY.md) | Railway / Compose ops notes |
| [docs/ROADMAP.md](ROADMAP.md) | 12-month Q1–Q4 plan |
| [docs/ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md) | DoD / review checklist |

---

## 1. High-level architecture

### What runs where

**Local Compose** (`docker-compose.yml`) and **Railway** (script / `.railway/railway.ts`) both aim
at the same logical topology: one SPA edge, three Spring Boot apps, Postgres, Redis, RabbitMQ.

```text
Browser
  └─► frontend (:5173)
         │  Vite (dev) or Nginx (Docker/Railway)
         ├─► GET/POST /api/v1/cards*        → card-service (:8081)
         ├─► POST /api/v1/credits/*         → credit-service (:8082)
         ├─► POST /api/v1/collateral/*      → ai-collateral-service (:8083)
         └─► GET /api/health                → static JSON (healthprobe) or Vite plugin

card-service  ──JDBC──► PostgreSQL (bank_db)
              ──Redis──► Redis (cache name "cards", TTL 10m)

credit-service ──JDBC──► PostgreSQL (same bank_db, different tables)
               ──AMQP──► RabbitMQ exchange bank.events / routing key credit.calculated
                         (publish only — no consumer in this repo)

ai-collateral-service ──HTTPS──► Google Gemini generateContent
                      (no DB, no Redis, no RabbitMQ)
```

On Railway the browser only sees the **frontend** public domain. Backends are reached via
Nginx using private DNS (`${{service.RAILWAY_PRIVATE_DOMAIN}}:${{service.PORT}}`). Compose
uses Docker service DNS (`http://card-service:8081`, etc.).

### Sync vs async — what is request/response today

| Interaction | Pattern | Notes |
| --- | --- | --- |
| UI → any `/api/v1/...` | **Synchronous HTTP** | Browser waits for JSON |
| card-service ↔ Postgres / Redis | Sync | Cache aside on GET list |
| credit-service ↔ Postgres | Sync | Persist before publish |
| credit-service → RabbitMQ | **Fire-and-forget publish** | `convertAndSend` after calculate; no ack handling in app code; no `@RabbitListener` anywhere |
| ai-collateral → Gemini | Sync HTTP with retries | On exhaustion → heuristic **HTTP 200** fallback to caller |
| UI “disburse credit onto card” | **Sync UI orchestration** | After calculate, user clicks Apply → `POST .../apply-credit` on card-service. **Not** driven by the Rabbit event |

So the only true async boundary in production code is the **outbound** `CreditCalculatedEvent`.
Nothing in this repository consumes it. Card mutation after scoring is explicitly a second HTTP
call from the React dashboard.

### Docs vs reality (architecture)

1. **ADR §1** says there is *“no Spring Cloud Gateway (or Kong/NGINX edge)”* and browsers talk via
   Vite proxy / direct ports. **Code:** Docker/Railway frontend **is** an Nginx reverse proxy
   (`frontend/nginx.conf.template`). Dev still uses Vite proxy (`vite.config.ts`). README’s
   Mermaid label “Vite Dev Proxy / Nginx” is accurate; the ADR wording is outdated for container
   deploy.
2. **OpenAPI** `servers[0].url` is `http://localhost:8080/api/v1` (“Local Gateway”). **No process
   listens on 8080** in Compose or Railway — services are 8081/8082/8083 behind 5173.
3. **ADR** lists both `TransactionCreatedEvent` and `CreditCalculatedEvent`. **Only
   `CreditCalculatedEvent` exists** under `services/`; card-service never publishes events.

---

## 2. Services

### 2.1 card-service

**Path:** `services/card-service`  
**Owns:** plastic-card lifecycle, balances, credit-limit headroom on the card, debt/loan principal,
transaction ledger rows.  
**Does not own:** credit scoring, Gemini appraisal, RabbitMQ publishing.

#### Stack (from `build.gradle.kts`)

| Item | Value |
| --- | --- |
| Language | Kotlin **1.9.25** (`jvm` + `plugin.spring` + `plugin.jpa`) |
| JVM | toolchain **21** |
| Spring Boot | **3.4.5** |
| Dependency management | `io.spring.dependency-management` **1.1.7** (Boot BOM) |
| Gradle wrapper | **8.13** |
| Key starters | web, data-jpa, cache, data-redis, validation, actuator |
| Extra | `jackson-module-kotlin`, `kotlin-reflect`, PostgreSQL runtime |
| Test | spring-boot-starter-test, `mockk` **1.13.13**, `springmockk` **4.0.2** |
| Port | `8081` (`PORT` / `SERVER_PORT`) |

#### Domain model

**`Card`** (`cards`): `id` (UUID string), `cardNumberMasked`, `balance`, `creditLimit` (default
`5000.00`), `activeDebt`, `loanPrincipal`, `currency` (`EUR`), `status` (`ACTIVE` \| `CLOSED`),
`createdAt`. Money columns scale 2.

**`Transaction`** (`transactions`): `id`, `cardId`, `amount`, `type`
(`TOPUP` \| `PURCHASE` \| `CREDIT_DISBURSEMENT`), `createdAt`.

**Invariants enforced in `CardService` (not DB CHECK beyond transaction type migrator):**

- Mutating ops on non-ACTIVE cards → treated as not found (`CardNotFoundException` → 404).
- **Purchase available funds** = `balance + max(0, creditLimit - activeDebt)`. If amount exceeds
  that → `InsufficientFundsException` → **402**. Otherwise: drain balance first; shortfall
  increases `activeDebt`. **`creditLimit` is not reduced when debt grows** — unused revolving
  headroom shrinks via `activeDebt` (so post–apply-credit cash+debt does not inflate spend power).
- **Top-up** with `activeDebt > 0`: debt (and `loanPrincipal`) paid first; remainder → balance.
- **Close:** ACTIVE only; refuse if `activeDebt > 0` or `balance < 0`.
- **Delete:** refuse if debt > 0 or balance < 0; deletes transactions then card (CLOSED ok if clean).
- **Apply credit:** add disbursement to `balance`, `activeDebt`, and `loanPrincipal`;
  `creditLimit = max(current, approvedCreditLimit)`.

Schema: Hibernate `ddl-auto: update` plus JDBC `ApplicationRunner` migrators for `created_at`,
`loan_principal`, and transaction type CHECK. **No Flyway/Liquibase.**

#### Public HTTP API (`CardController` → `/api/v1/cards`)

| Method | Path | Body | Success | Business rejects |
| --- | --- | --- | --- | --- |
| GET | `/` | — | 200 `CardResponse[]` | — |
| POST | `/` | — | **201** card | — |
| POST | `/{id}/topup` | `AmountRequest` `@DecimalMin("0.01")` | 200 | 404 inactive/missing; 400 validation |
| POST | `/{id}/purchase` | same | 200 | **402** insufficient; 404 |
| POST | `/{id}/apply-credit` | `disbursementAmount`, `approvedCreditLimit` | 200 | 404; 400 validation |
| POST | `/{id}/close` | — | 200 | **400** `CannotCloseCardException`; 404 |
| DELETE | `/{id}` | — | **204** | **400** `CannotDeleteCardException`; 404 |

Error body (`ApiError`): `timestamp`, `status`, `error`, `message`, `path`.

#### Persistence & Redis

- Shared Postgres DB `bank_db` with credit-service (separate tables).
- Redis cache name **`cards`**, key prefix `card-service::cards::`, TTL **10 minutes**,
  `@Cacheable` on `getAllCards`, `@CacheEvict(allEntries=true)` on every mutation, plus
  `CardsCacheEvictOnStartup`. Custom Redis `ObjectMapper` with typing for `com.bankapp.*`
  (Kotlin data classes are final — without this, GET list can 500 after deserialize).

#### Seed

`DemoDataSeeder` when `count() == 0`: three ACTIVE EUR cards ending `4242` / `1881` / `9012`
(balances 1250 / 80.50 / 3000; one carries debt 420 / loan 400).

#### Interactions

Calls **no other microservice**. Called by frontend (and conceptually by any client of OpenAPI).

---

### 2.2 credit-service

**Path:** `services/credit-service`  
**Owns:** annuity scoring, approval/rejection, persistence of applications, publishing
`CreditCalculatedEvent`.  
**Does not own:** card balances, Gemini vision, consuming its own events.

#### Stack

| Item | Value |
| --- | --- |
| Language | Java **21** + Lombok |
| Spring Boot | **3.4.5** |
| Starters | web, data-jpa, **amqp**, validation, actuator |
| DB | PostgreSQL |
| Test extras | Testcontainers `junit-jupiter` + `postgresql` |
| Port | **8082** |

#### Domain

**`CreditApplication`** (`credit_applications`): UUID `id`, `requestedAmount`, `monthlyIncome`,
`termMonths`, `aiCollateralValueEur`, `monthlyPayment`, `interestRate`, `approvedLimit`,
`status` (`APPROVED` \| `REJECTED`), `createdAt`.

#### Scoring rules (`CreditService`)

Defaults from config: `app.credit.annual-interest-rate` = **`8.5`** (env
`CREDIT_ANNUAL_INTEREST_RATE`).

1. Monthly rate `r = annual/100/12`. Annuity  
   `M = P · r(1+r)^n / ((1+r)^n − 1)` (scale 2, `HALF_UP`); term must be **1..120**.
2. `approvedLimit = requestedAmount + (aiCollateralValueEur × 0.70)` if collateral > 0  
   (`COLLATERAL_LTV = 0.70`).
3. **APPROVED** iff `monthlyPayment ≤ monthlyIncome × 0.40` (`MAX_PAYMENT_TO_INCOME`), else
   **REJECTED**.
4. Persist row, then publish event.

#### HTTP

`POST /api/v1/credits/calculate` with validated
`requestedAmount`, `monthlyIncome`, `termMonths` (1–120), optional `aiCollateralValueEur` →
`CreditCalculationResponse`.

Errors: Map with `timestamp`, `status`, `error`, `message` (**no `path`** — differs from
card-service). Validation / `IllegalArgumentException` → 400; else 500.

#### RabbitMQ

Declared in config / `RabbitConfig`:

| Setting | Value |
| --- | --- |
| Exchange | **`bank.events`** (topic, durable) |
| Queue | **`credit.scoring.queue`** (durable, bound) |
| Routing key | **`credit.calculated`** |
| Payload | `CreditCalculatedEvent`: `applicationId`, `requestedAmount`, `monthlyPayment`, `interestRate`, `approvedLimit`, `status`, `createdAt` |
| Converter | Jackson JSON + JavaTimeModule |

**No `@RabbitListener` in the repository.** The queue exists so the broker topology is ready for
a future consumer (Roadmap Q3); today the message is effectively unused by application logic.

#### Seed

One APPROVED application (`11111111-1111-1111-1111-111111111111`) when table empty: 10000 / 3500 /
24 months / collateral 150 → payment `455.23`, rate `8.50`, limit `10105.00`.

#### Interactions

- Sync: Postgres.
- Async out: RabbitMQ publish after calculate.
- Does **not** call card-service or ai-collateral-service. Collateral value arrives only if the
  **UI** embeds it in the calculate request.

---

### 2.3 ai-collateral-service

**Path:** `services/ai-collateral-service`  
**Owns:** image → market-value appraisal (Gemini) with resilient fallback.  
**Does not own:** approval decisions, card state, persistence.

#### Stack

| Item | Value |
| --- | --- |
| Java | **21**, Spring Boot **3.4.5**, web + actuator + Lombok |
| No | JPA, Redis, AMQP |
| Port | **8083** |
| Multipart | max **10MB** |
| RestTemplate | connect **15s**, read **60s** |

`.env` at repo root is loaded via `EnvFileEnvironmentPostProcessor` (Boot itself does not read
`.env`). `bootRun` also injects that file into the process environment.

#### HTTP

`POST /api/v1/collateral/evaluate` multipart part **`file`** →
`CollateralEvaluationResponse`: `objectDetected`, `condition`, `estimatedValueEur`,
`maxCreditLimitEur`.

#### Gemini integration (factual)

| Setting | Default |
| --- | --- |
| Model | `gemini-3.1-flash-lite` (`GEMINI_MODEL`) |
| Base URL | `https://generativelanguage.googleapis.com/v1beta/models` |
| Endpoint | `{base}/{model}:generateContent?key=...` |
| Max attempts | `GEMINI_MAX_ATTEMPTS` default **3** |
| Initial delay | `GEMINI_RETRY_DELAY_MS` default **800** ms; delay = `initial × 2^(attempt-1)` |
| Fallback value | `GEMINI_FALLBACK_VALUE_EUR` default **500.00** |

Request body: Gemini `contents[0].parts` = text system prompt + `inline_data` (base64 image).
Prompt demands raw JSON keys matching the response DTO.

Retryable failures (5xx / 429 / network via `GeminiApiException.isRetryable`) retry inside
`callGemini`. When attempts are exhausted **or** key missing **or** non-retryable failure bubbles
to `evaluate()`, the service logs WARN and returns **`fallbackEvaluation`** — still a successful
controller response, not a hard 502 to the SPA:

- `objectDetected`: filename-based or `"Unidentified collateral item"`
- `condition`: `"AI appraisal unavailable, manual review required"`
- `estimatedValueEur`: configured fallback
- `maxCreditLimitEur`: `estimated × 0.70` (`CREDIT_LIMIT_RATIO`)

If Gemini JSON omits `maxCreditLimitEur`, the same 0.70 ratio is applied server-side.

**How this feeds credit:** the UI posts Gemini’s **`estimatedValueEur`** as
`aiCollateralValueEur` into credit calculate. Credit then applies **another** 0.70 LTV to that
figure when computing `approvedLimit`. The collateral DTO’s `maxCreditLimitEur` is informational
for the UI; it is **not** what credit-service reads.

#### Errors

`ApiError`: `status`, `error`, `message` only. Empty file → 400. Gemini failures that escape
fallback path can surface as 4xx/5xx via `GeminiApiException`, but the evaluate happy-path after
retries prefers fallback.

#### Tests

**No test sources** under this service. CI runs `./gradlew build` only with empty
`GEMINI_API_KEY`.

---

### 2.4 frontend

**Path:** `frontend/` — React **19**, TypeScript **~5.7**, Vite **^6.2**, Tailwind **^3.4.17**,
Node **22** in Docker/CI.

#### Role

Operator dashboard for the three domains (cards / credit+AI / live health). Not a BFF: it calls
backend APIs through Vite (dev) or Nginx (prod).

#### Tabs & API usage

| Tab | Component | Calls |
| --- | --- | --- |
| Shell | `App.tsx` | `GET /api/v1/cards` on mount; lifts `cards` + `selectedCardId` |
| My Cards | `CardsTab` | GET refresh; POST issue/topup/purchase/close; DELETE card |
| Credit & AI | `CreditTab` | POST collateral evaluate → POST credits calculate → POST cards apply-credit |
| Architecture | `ArchitectureTab` | `GET /api/health` on mount, every **8s**, and on Re-check |

Inactive tabs stay mounted (`hidden`) so card selection survives tab switches.

#### State

Plain **`useState` only** — no Redux, Context store, or React Query. Errors/notices are local
strings per tab.

#### Typography tokens (post design pass)

Declared once in `src/index.css` `:root` and wired in `tailwind.config.js` (theme **replace**, not
extend, for family/size/weight):

- Fonts: **IBM Plex Sans** (UI), **IBM Plex Mono** (`.figure` = mono + `tnum`/`lnum`) — Google Fonts
  weights 400–700 only.
- Scale: 12/16, 14/20, 16/24, 20/28, 24/32, 32/40.
- Weights: 400 / 500 / 600 / 700.

#### Edge routing

- **Dev:** `vite.config.ts` proxies `/api/v1/cards|credits|collateral` to 8081–8083; custom plugin
  serves `/api/health`.
- **Prod container:** `nginx.conf.template` — SPA fallback; `/api/health` →
  `/var/cache/nginx/health.json` written by `docker/healthprobe.sh` every 5s; path-prefix proxy to
  `CARD_SERVICE_URL` / `CREDIT_SERVICE_URL` / `COLLATERAL_SERVICE_URL`. Collateral body limit
  **12m**, cards/credits **10m**. Nginx vars use **single** `$uri` / `$host` (doubled `$$` breaks
  modern image `envsubst`).

---

## 3. Event-driven layer

```text
credit-service.CreditService.calculate
    → persist CreditApplication
    → rabbitTemplate.convertAndSend(
          exchange = "bank.events",
          routingKey = "credit.calculated",
          CreditCalculatedEvent { … }
      )

credit.scoring.queue  ← bound to that key
    → (no application consumer)
```

**Where events end and UI begins:** scoring is finished when HTTP calculate returns. Applying money
to a card is a **separate intentional operator action** in `CreditTab.onApplyToCard` →
`POST /api/v1/cards/{id}/apply-credit` with `disbursementAmount = requestedAmount` and
`approvedCreditLimit = result.approvedLimit`. A future consumer could replace that click (Roadmap
Q3); today the event is observability / future-proofing, not orchestration.

---

## 4. AI integration (end-to-end)

1. User uploads image on Credit tab step 1.
2. Frontend `FormData` → `POST /api/v1/collateral/evaluate`.
3. Service validates file; if no API key → immediate heuristic (500 EUR default).
4. Else Base64 + MIME → Gemini; retry transient errors up to 3 times with 800ms, 1600ms, …
5. Parse JSON (strip markdown fences if present); ensure limit via ×0.70 if needed.
6. On terminal Gemini failure → same heuristic object, **still 200 to UI**.
7. Step 2: UI sends `aiCollateralValueEur = estimatedValueEur` into credit calculate.
8. Credit boosts `approvedLimit` by collateral × 0.70 and decides APPROVED/REJECTED vs 40% DTI.
9. Optional: Apply to card mutates card balances/debt/limit synchronously.

---

## 5. Build & CI/CD

### Local / image builds

| Unit | Build |
| --- | --- |
| Each JVM service | `./gradlew bootJar` (Docker: `-x test --no-daemon`); Temurin **21** JDK build → JRE run; artifact `app.jar` |
| Frontend | `npm ci` && `npm run build` (`tsc -b && vite build`) in `node:22-alpine`; served by `nginx:1.27-alpine` |

### GitHub Actions (`.github/workflows/ci.yml`)

Triggers: push/PR to `main` or `develop`. Concurrency cancels older runs on the same ref.

| Job | What it runs | Failure means |
| --- | --- | --- |
| `card-and-credit` (matrix) | `./gradlew build test` per service | Compile or unit/integration tests fail |
| `ai-collateral-service` | `./gradlew build` with `GEMINI_API_KEY=""` | Compile/packaging fail (**no tests**) |
| `frontend` | `npm ci` + `npm run build` | Typecheck or Vite build fail |
| `contract-lint` | `npx @redocly/cli@1 lint docs/api/openapi.yaml` | OpenAPI invalid |

Jobs are **parallel** (`needs` absent). Matrix `fail-fast: false`. Whether merge is blocked depends
on GitHub branch protection (not defined in-repo).

---

## 6. Testing

### card-service (MockK / Spring MockMvc — **no Testcontainers**)

| Class | Cases |
| --- | --- |
| `CardServicePurchaseTest` | Insufficient → exception, no save; debt-at-limit / unused-headroom cases; over-balance purchase zeros balance and raises debt |
| `CardServiceCloseTest` | Reject close on debt; reject on negative balance; close when clean |
| `CardServiceCacheEvictTest` | Issue card evicts Redis `cards` cache |
| `CardControllerHttpStatusTest` | HTTP **402** on purchase; HTTP **400** on close with debt |

### credit-service

| Class | Cases | Infra |
| --- | --- | --- |
| `AnnuityPaymentTest` | 1-month & 120-month annuity; term 0/negative throw; HALF_UP scale 2; rate sensitivity | Unit |
| `CreditInterestRateConfigurationTest` | Loads annual rate from properties | Spring |
| `CreditCalculatedEventPublishingTest` | Publishes to `bank.events` / `credit.calculated` with expected fields | Mock Rabbit |
| `CreditCalculateIntegrationTest` | Happy path persists APPROVED; term 0 → 400 | **Testcontainers** `postgres:16-alpine` (Windows DOCKER_HOST npipe quirk in Gradle) |

### ai-collateral-service

**None.**

### frontend

**No unit/e2e test suite** in `package.json` (only build).

### Gaps (intentional honesty)

- No consumer test for Rabbit (nothing to consume).
- No contract tests (Pact) yet — Roadmap Q4.
- No live Gemini tests in CI.
- No Playwright/Cypress in CI.
- Purchase/top-up Redis interaction covered only lightly via cache-evict on issue.
- Shared-DB multi-service integration not tested as one Compose stack in CI.

---

## 7. Deploy & infrastructure

### Compose (`docker-compose.yml`)

Postgres **16**, Redis **7**, RabbitMQ **3-management**, three app images, frontend. Volumes for
postgres/redis/rabbitmq data. Frontend depends on healthy backends + infra; **frontend itself has
no healthcheck**. `ai-collateral-service` has **no `depends_on`**.

### Railway

Desired state in `.railway/railway.ts`: managed Postgres + Redis, RabbitMQ **Docker image**
`rabbitmq:3-management-alpine`, four GitHub+Dockerfile app services, healthchecks, env wiring.
Operational path on Windows: **`scripts/railway-up.ps1`** (imperative CLI) because
`railway config apply` / IaC version probe is broken under the Windows npm CLI (see DEPLOY.md).

Script steps: auth → ensure project (`zbk-bank-demo`) → add DB/Redis/Rabbit → add four repo
services with `source.rootDirectory` + `DOCKERFILE` → set `${{Service.VAR}}` references →
domain on frontend.

**Plan limit:** full stack is **7** Railway services; Free (3) / Trial (5) are insufficient → Hobby.

Redeploy: Postgres/Redis volumes persist on Railway plugins; app containers rebuild from GitHub
`main`. Seeders run only when tables are **empty** — existing data is kept. Empty new DB gets
3 cards + 1 credit application.

### Environment map (non-secret names)

From `.env.example` + service YAML: `POSTGRES_*`, `DB_*`, `REDIS_*`, `RABBITMQ_*`,
`GEMINI_*`, plus Railway `PG*` / `PORT`. Frontend edge: `NGINX_PORT`/`PORT`,
`CARD_SERVICE_URL`, `CREDIT_SERVICE_URL`, `COLLATERAL_SERVICE_URL`, `SKIP_INFRA_PROBES`.

---

## 8. Key decisions and alternatives

Rewritten from ADR with current code context:

### No API Gateway product

**Chosen:** path-based proxy at the SPA edge (Vite or Nginx).  
**Alternatives:** Spring Cloud Gateway / Kong.  
**Trade-off:** Gateway would centralize auth, rate limits, and a single OpenAPI base URL (`:8080`
in the contract) but adds another always-on process. Revisit when OAuth2 and rate limiting land
(Roadmap Q1) — then the OpenAPI server URL finally matches reality.

### RabbitMQ instead of Kafka

**Chosen:** RabbitMQ topic exchange for a single credit event.  
**Alternatives:** Kafka / outbox-only DB.  
**Trade-off:** Rabbit is enough for demo fan-out and Compose one-liners; Kafka would be heavier
ops for one event type. Revisit if event sourcing / multi-consumer audit (Q3) needs log retention
semantics.

### Publish event but UI-orchestrated disbursement

**Chosen:** teach the async pattern without shipping a fragile half-SAGA.  
**Alternatives:** immediate consumer that calls card-service; choreography with compensating
transactions.  
**Trade-off:** Today an operator can calculate and never apply (event still fires). Revisit when
a real disbursement SLA exists — then a consumer + idempotency keys are mandatory.

### Gemini flash-lite + heuristic fallback

**Chosen:** `gemini-3.1-flash-lite`, 3 attempts, then fixed EUR estimate.  
**Alternatives:** always-fail hard; heavier Gemini Pro; local vision model.  
**Trade-off:** Demo credit flow stays usable offline/keyless; estimates can be wrong by design.
Revisit with Circuit Breaker + human review queue (Roadmap Q3) before any regulated use.

### Mobile: PWA/BFF later, not KMP day-one

**Chosen:** React SPA only.  
**Alternatives:** Kotlin Multiplatform shared client now.  
**Trade-off:** Faster PoC; KMP waits until domain APIs stabilize (Roadmap Q2).

---

## 9. Known demo boundaries

Intentionally **absent** (not unfinished accidents):

- Authentication / authorization / multi-tenant “active user” (OpenAPI says “active user”; API is open)
- PCI tokenization / real PAN issuance (masks only)
- API Gateway product & rate limiting
- TLS termination story beyond host platform defaults
- Consumer for `CreditCalculatedEvent` / SAGA / outbox
- Circuit breaker around Gemini
- Flyway versioned migrations (Hibernate `update` + ad-hoc JDBC migrators)
- Unified error envelope across services
- Production secrets manager (repo uses `.env` / Railway variables)
- Mobile native clients
- Horizontal multi-instance cache coherence testing
- Formal SLA, observability stack (OTel — Roadmap Q4)
- Live public demo: https://zbk-banking.up.railway.app (`PUBLIC_FRONTEND_DOMAIN`)

---

## 10. Glossary

| Term | Meaning in this project |
| --- | --- |
| **activeDebt** | Outstanding amount owed on a card from over-limit purchases and/or credit disbursement |
| **loanPrincipal** | Principal associated with disbursed credit; reduced when top-ups pay down debt |
| **creditLimit** | Revolving facility ceiling; unused headroom is `max(0, creditLimit - activeDebt)`; raised by apply-credit; not decremented when debt rises |
| **CardStatus** | `ACTIVE` or `CLOSED` |
| **Annuity** | Fixed monthly payment for principal at annual rate over `termMonths` |
| **DTI gate** | Approval if monthly payment ≤ 40% of stated monthly income |
| **Collateral LTV** | 70% of AI `estimatedValueEur` added into `approvedLimit` |
| **Collateral evaluation** | Image appraisal returning object, condition, EUR value, and derived max limit |
| **Heuristic fallback** | Fixed `GEMINI_FALLBACK_VALUE_EUR` appraisal when Gemini is unavailable |
| **CreditCalculatedEvent** | JSON message published after calculate; currently unconsumed |
| **apply-credit** | Card API that posts disbursement onto balance/debt/loan and bumps limit |
| **cards cache** | Redis entry holding the full card list for GET `/api/v1/cards` |
| **figure** | Frontend mono + tabular-nums utility for money/PAN/ports |
| **bank.events** | RabbitMQ topic exchange used by credit-service |
| **credit.calculated** | Routing key for scoring results |
| **Plastic card** | Dashboard metaphor for a `Card` row (not a physical issuing network) |

---

## 11. Discrepancy checklist (docs ↔ code)

Use this as a trust signal that the deep dive was written from sources, not memory:

1. ADR denies an NGINX edge; Compose/Railway frontend **is** Nginx.
2. ADR mentions `TransactionCreatedEvent`; **code has no such type/publisher**.
3. OpenAPI base URL `:8080` gateway; **no gateway service** on that port.
4. Error JSON shapes **differ** across the three JVM services (`path` only on card; collateral omits
   `timestamp`).
5. README seed claims match code (3 cards + 1 APPROVED application); ADR async-gap narrative
   matches UI orchestration — but the “event-driven” label is easy to over-read: **publish ≠
   consume**.
6. CI “tests all services” intuition fails for **ai-collateral** (build-only) and frontend
   (build-only).
7. Architecture SVG draws an edge collateral → credit; **runtime has no service-to-service call**
   — only the browser stitches them.

When those drift further, update this file in the same PR as the behavior change.
