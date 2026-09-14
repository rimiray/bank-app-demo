# ADR 0002: Kotlin Multiplatform Shared Logic + Native Compose Android UI

## Status

Accepted.

## Context

`bank-app-demo` already exposes a contract-first backend (OpenAPI at `docs/api/openapi.yaml`)
for cards, credit scoring, and AI collateral evaluation. The web dashboard is the first client.
We now need a mobile client — **ZBK Credit Companion** — that talks to the same APIs without
forking business rules or DTO shapes per platform.

Decision drivers:

* Fast **Android MVP** time-to-market for demos and interviews.
* Keep a single source of truth for HTTP contracts and credit-related validation next to the
  existing JVM/Kotlin backend skills in the team.
* Leave a clean path to share that logic with iOS later, without locking the UI into a
  cross-platform widget toolkit for the first release.

## Decision

We adopt **Kotlin Multiplatform (KMP)** for shared non-UI code and **native Jetpack Compose**
for the Android UI of ZBK Credit Companion.

| Layer | Choice | Responsibility |
| --- | --- | --- |
| **shared** (KMP) | Kotlin Multiplatform | Network contracts (**Ktor Client**), DTOs (**kotlinx.serialization**), business rules (e.g. credit term validation aligned with credit-service `1..120` months) |
| **androidApp** | Jetpack Compose (native) | Screens, navigation, theming, platform UX — no shared UI for MVP |
| **iOS / CMP** | Deferred | No iOS target and no Compose Multiplatform UI in this ADR’s scope |

OpenAPI remains the server contract source of truth; shared DTOs and client calls are generated
or hand-maintained to match `docs/api/openapi.yaml`, not a parallel mobile-only schema.

## Consequences

### Positive

* Less duplication of API models and validation when a second platform appears later.
* One place to evolve client-side rules that must stay consistent with the backend
  (amounts, term bounds, error mapping).
* Native Compose keeps Android UX and performance unconstrained by a shared UI abstraction.
* Android MVP can ship without waiting on iOS or Compose Multiplatform maturity in this repo.

### Negative / trade-offs

* Shared module must stay **UI-free**; presentation state stays in the Android app.
* iOS will need a separate UI stack later (SwiftUI or CMP) even if it reuses `shared`.
* Team must keep KMP tooling (Gradle multiplatform, expect/actual where needed) in sync with
  Android Studio / AGP versions.

### Explicitly out of scope (for now)

* iOS application target.
* Compose Multiplatform shared UI.
* Replacing the React web dashboard with KMP.

When those become goals, a follow-up ADR should revisit UI sharing vs. continuing native UIs
on top of the same `shared` module.
