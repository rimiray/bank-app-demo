# Live deployment on Railway

One authoring file describes the whole stack: [`.railway/railway.ts`](../.railway/railway.ts).

| Resource | How it runs |
| --- | --- |
| `postgres` | Railway managed Postgres |
| `redis` | Railway managed Redis |
| `rabbitmq` | Docker image `rabbitmq:3-management-alpine` |
| `card-service` / `credit-service` / `ai-collateral-service` | Dockerfile + GitHub subdirectory |
| `frontend` | Dockerfile; nginx proxies to private backend URLs |

## Stop: do not deploy the repo root

If Railway shows **one** service named like the repo, Builder **Railpack**, and the log says
`Package-lock.json detected` / `No start command detected` — that is the wrong setup.

GitHub “Deploy this repo” on the **root** treats the monorepo as a single Node app. It will always fail.

**Fix:** delete that root service (or disconnect GitHub from it), then use the CLI flow below.

## One-command deploy (CLI + IaC)

```powershell
railway login
railway link                 # create/select project — once
.\scripts\railway-up.ps1
```

This applies `.railway/railway.ts` and creates **Postgres + Redis + RabbitMQ + 4 apps**.
Then it generates a public domain for `frontend`.

Optional Gemini key:

```powershell
railway variable set GEMINI_API_KEY=your_key --service ai-collateral-service
```

## Prerequisites

1. [Railway CLI](https://docs.railway.com/guides/cli) (`npm i -g @railway/cli`)
2. GitHub App connected so Railway can pull `rimiray/bank-app-demo`
3. Latest `main` pushed (CI green)

## Seed data

Empty DBs get 3 demo cards and one approved credit application via `DemoDataSeeder`.

## Local vs Railway env

| Concern | Local Compose | Railway |
| --- | --- | --- |
| JDBC | `DB_URL=jdbc:postgresql://postgres:5432/bank_db` | `PGHOST` / `PG*` from Postgres plugin |
| Redis | `REDIS_HOST=redis` | `SPRING_DATA_REDIS_URL` from Redis plugin |
| RabbitMQ | compose service `rabbitmq` | private domain of `rabbitmq` service, port `5672` |
| Frontend → APIs | compose DNS | `${{service.RAILWAY_PRIVATE_DOMAIN}}:${{service.PORT}}` |
