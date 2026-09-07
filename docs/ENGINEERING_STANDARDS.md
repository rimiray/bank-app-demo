# Engineering Standards — bank-app-demo

Standards for the Business Banking engineering team. This document is the working
agreement for how we merge code — not a generic checklist. Examples refer to this
repository’s current layout (`services/*`, `docs/api/openapi.yaml`, `.github/workflows/ci.yml`).

---

## 1. Definition of Done

A change is ready to merge into `main` only when **all** of the following are true:

| Gate | Expectation |
| --- | --- |
| **Business-rule tests** | New or changed money/credit/card rules have automated tests that pass locally and in CI (e.g. annuity boundaries in `AnnuityPaymentTest`, purchase/close rules in `CardServicePurchaseTest` / `CardServiceCloseTest`). |
| **OpenAPI sync** | If any public HTTP surface changed, `docs/api/openapi.yaml` is updated in the **same** PR (see [API Contract-First Policy](#4-api-contract-first-policy)). |
| **ADR** | If the change introduces or revises an architectural trade-off, update `docs/adr/` (see `0001-architecture-overview.md`) and/or `docs/ROADMAP.md`. Pure bugfixes need no ADR. |
| **CI green** | `.github/workflows/ci.yml` is green on the PR: `card-service`, `credit-service`, `ai-collateral-service`, `frontend`, `contract-lint`. |
| **Code review** | At least one approving review against the [Code Review Checklist](#3-code-review-checklist) below. Author does not self-approve. |

Do not merge with skipped/failed tests, “fix later” contract drift, or secrets in the diff.

---

## 2. Branching Strategy

**Target process for the team: GitFlow** (`main` / `develop` / `feature/*` / `release/*`).

| Branch | Role |
| --- | --- |
| `main` | Production-ready; tagged releases only. Protected: CI + review required. |
| `develop` | Integration branch for the next release. |
| `feature/<ticket>-short-name` | Branched from `develop`; merges back via PR. |
| `release/x.y` | Stabilisation cut from `develop`; bugfixes only; merges to `main` and back to `develop`. |
| `hotfix/<ticket>` | Urgent fix from `main`; merges to `main` and `develop`. |

**Why GitFlow (not trunk-based) for this domain:** Business Banking work typically needs
predictable release trains, change windows, and an audit-friendly separation between
“integrated but not released” and “in production”. GitFlow’s `release/*` and protected
`main` map cleanly to those compliance expectations.

> **Honest note on this PoC:** the current commit history on GitHub is closer to a
> simplified `main` + feature pushes. Treat GitFlow above as the **target operating
> model for a real team**, not as a description of every past commit in this demo repo.
> CI already listens to both `main` and `develop` to support that transition.

---

## 3. Code Review Checklist

Use these questions on every PR that touches services or the API contract.

1. **Money is `BigDecimal` only — never `double`/`float`.**  
   Check service math and DTOs. Reference: `CreditService.annuityPayment` uses
   `MathContext` + `RoundingMode.HALF_UP` and `setScale(2, …)`; card mutations in
   `CardService.topUp` / `purchase` / `applyCredit` take and store `BigDecimal`.  
   *Reject* any new monetary field typed as `Double`/`Float` or compared with `==` on
   floating binary values. In tests, assert with `isEqualByComparingTo` /
   `compareTo`, as in `AnnuityPaymentTest`.

2. **Payment endpoints and double-submit risk (`topup` / `purchase`).**  
   Today `CardService.topUp` and `purchase` always append a `Transaction` and mutate
   balance/debt — there is **no** `Idempotency-Key` (or equivalent) yet. In review:  
   - Does the PR make retries/double-clicks worse (e.g. fire-and-forget from UI without
     disable-on-submit)?  
   - If the PR claims safe retries, it must introduce an idempotency mechanism (key +
     stored outcome) or explicitly document why the gap remains.  
   - Prefer tests that call the same operation twice and state the expected ledger effect.

3. **Errors go through `@RestControllerAdvice` — no stack traces to clients.**  
   Each service has a `GlobalExceptionHandler` returning a compact `ApiError`
   (`status`, `error`, `message`, …) — see
   `card-service/.../GlobalExceptionHandler.kt`,
   `credit-service/.../GlobalExceptionHandler.java`,
   `ai-collateral-service/.../GlobalExceptionHandler.java`.  
   *Reject* handlers that put `ex.printStackTrace()` output, Gemini raw payloads, or
   JDBC internals into the HTTP body. Logs may keep full detail; responses stay opaque.

4. **OpenAPI and implementation ship together (Contract-First).**  
   If the PR adds/changes paths such as `/api/v1/cards/{cardId}/apply-credit` or
   `/api/v1/credits/calculate`, `docs/api/openapi.yaml` must change in the same PR.
   `contract-lint` in CI must stay green. See [§4](#4-api-contract-first-policy).

5. **Secrets never land in git.**  
   Real keys/passwords belong in `.env` (gitignored) or CI/runtime env vars.
   Templates only: `.env.example`. Gemini is loaded via `GEMINI_API_KEY` /
   `EnvFileEnvironmentPostProcessor` in `ai-collateral-service` — never hard-code a key
   in source. Flag accidental `.env`, connection strings with passwords, or private keys
   in the diff.

6. **Cache / side effects after mutating writes.**  
   Card list is Redis-cached (`@Cacheable` / `@CacheEvict` on `CardService`). A PR that
   mutates cards must evict `cards` (as `issueCard`, `topUp`, `purchase`, etc. already do)
   or justify why stale reads are acceptable. See `CardServiceCacheEvictTest`.

7. **Domain invariants for cards stay enforced.**  
   Closing with `activeDebt > 0` or `balance < 0` must remain rejected
   (`CannotCloseCardException` → HTTP 400); insufficient purchase funds → 402 and **no**
   debt change (`CardServicePurchaseTest`, `CardControllerHttpStatusTest`). Do not weaken
   these rules without an ADR and product sign-off.

---

## 4. API Contract-First Policy

1. Propose or update the change in **`docs/api/openapi.yaml` first** (or in the same commit
   series before implementation is considered complete).
2. Implement against that contract in the owning service (`card-service`, `credit-service`,
   `ai-collateral-service`) and keep DTO field names/types aligned.
3. Frontend clients (`frontend/src/api/*`) follow the published contract — they do not invent
   undocumented fields.
4. **PRs that change behaviour without a matching OpenAPI delta are not accepted.**
   Conversely, contract-only PRs without a follow-up implementation issue/PR should be rare
   and explicitly labelled as “contract preview”.

CI enforces structural validity via the `contract-lint` job (`@redocly/cli` + `redocly.yaml`).

---

## 5. Testing Pyramid

Guidance for a feature the size of **credit-service** (annuity + scoring + event publish +
HTTP calculate):

```
        /\
       / e2e \          ← few (1–2): UI happy path only
      /--------\
     / integ.   \       ← few (1–3): one per critical flow
    /------------\
   / unit tests   \     ← many (dozens ok): pure rules, fast
  /----------------\
```

| Layer | How many (realistic) | What belongs here | Examples in this repo |
| --- | --- | --- | --- |
| **Unit** | **Many** — prefer covering branches and money edge cases | Pure domain math, validation, event payload shape with mocked collaborators | `AnnuityPaymentTest` (term 1/120, term ≤ 0, HALF_UP scale), `CreditCalculatedEventPublishingTest` (mocked `RabbitTemplate`), card purchase/close MockK tests |
| **Integration** | **One (or a few) per critical flow** | HTTP + real DB (Testcontainers Postgres), app wiring | `CreditCalculateIntegrationTest` — `POST /api/v1/credits/calculate` persists `CreditApplication` |
| **E2E** | **Minimal** — happy path through UI | Manual or thin automated smoke: open dashboard → calculate credit → apply to card | Not automated in CI today; keep it that way unless a regression is chronic. Do **not** drive Gemini live or full multi-service SAGA in default CI |

**Rules of thumb**

- Prefer a new **unit** test over a new integration test when the bug is in a formula or
  branch (`termMonths <= 0`, insufficient funds).
- Add/extend an **integration** test when the risk is wiring: JPA mapping, HTTP status
  mapping, “did it actually hit the DB?”.
- Keep **e2e** scarce: slow, brittle, and expensive. One happy-path demo script is enough
  for PoC reviews; expand only with a clear failure pattern CI missed.

Live calls to Gemini must never be the default CI path (see `ai-collateral-service` job —
no real `GEMINI_API_KEY`). Tag or profile any future live provider tests so they stay opt-in.
