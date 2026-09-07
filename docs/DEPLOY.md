# Live deployment on Railway

One authoring file describes the whole stack: [`.railway/railway.ts`](../.railway/railway.ts).

| Resource | How it runs |
| --- | --- |
| `postgres` | Railway managed Postgres |
| `redis` | Railway managed Redis |
| `rabbitmq` | Docker image `rabbitmq:3-management-alpine` (no CloudAMQP account needed) |
| `card-service` / `credit-service` / `ai-collateral-service` | Dockerfile + GitHub root directory |
| `frontend` | Dockerfile; nginx proxies to private backend URLs |

## Prerequisites

1. [Railway CLI](https://docs.railway.com/guides/cli) installed (`npm i -g @railway/cli`).
2. GitHub repo `rimiray/bank-app-demo` connected to your Railway account (GitHub App).
3. Latest `main` pushed (CI green).

## One-command style deploy

From the repo root:

```powershell
# 1) Login once (browser)
railway login

# 2) Link this folder to a Railway project (create new or pick existing)
railway link

# 3) Install IaC SDK + apply the full graph (Postgres, Redis, RabbitMQ, 4 apps)
npm install
npm run railway:up

# 4) Public URL for the dashboard
npm run railway:domain

# 5) Optional: Gemini key for real Vision (otherwise heuristic fallback)
railway variable set GEMINI_API_KEY=your_key --service ai-collateral-service
```

Or run the helper script:

```powershell
.\scripts\railway-up.ps1
```

After apply finishes and deployments are healthy, put the frontend domain into README **Live Demo**.

## What went wrong with the first GitHub deploy?

A single service on **repo root** with **Railpack** cannot build this monorepo.
Use `.railway/railway.ts` (or create empty services with Root Directory + Dockerfile) instead.

## Seed data

Empty DBs get 3 demo cards and one approved credit application via `DemoDataSeeder`.

## Local vs Railway env

| Concern | Local Compose | Railway |
| --- | --- | --- |
| JDBC | `DB_URL=jdbc:postgresql://postgres:5432/bank_db` | `PGHOST` / `PG*` from Postgres plugin |
| Redis | `REDIS_HOST=redis` | `SPRING_DATA_REDIS_URL` from Redis plugin |
| RabbitMQ | compose service `rabbitmq` | private domain of `rabbitmq` service, port `5672` |
| Frontend → APIs | compose DNS names | `${{service.RAILWAY_PRIVATE_DOMAIN}}:${{service.PORT}}` |
