# Live deployment on Railway

## Why `railway link` failed

```
Failed to prompt for options
Available options can not be empty
```

Your workspace had **no usable (non-deleted) projects** for the interactive picker, so the
CLI prompt got an empty list. Fix: create a project non-interactively:

```powershell
railway init --name zbk-bank-demo
```

(or just run `.\scripts\railway-up.ps1`, which does this for you).

## Plan limits (why the script stops mid-way)

This stack needs **7 Railway services**:

`Postgres` · `Redis` · `rabbitmq` · `card-service` · `credit-service` ·
`ai-collateral-service` · `frontend`

| Plan | Max services / project | Enough for full stack? |
| --- | --- | --- |
| Free | 3 | No |
| Free Trial | 5 | No (script fails on the 6th) |
| Hobby ($5/mo) | 50 | Yes |

If you see `Free plan resource provision limit exceeded`, upgrade to **Hobby**, then re-run the
script (it is idempotent and will only add what is missing).

## Do not deploy the repo root

A single GitHub service on `/` with **Railpack** will fail (monorepo, not a Node app).
Delete that service. Use the script below instead.

## One script → full stack (cloud)

```powershell
railway login
# Upgrade to Hobby in the Railway dashboard if you are on Free/Trial
.\scripts\railway-up.ps1
```

Creates (if missing) and wires:

| Service | Source |
| --- | --- |
| Postgres / Redis | Railway databases |
| `rabbitmq` | Docker image |
| `card-service` / `credit-service` / `ai-collateral-service` / `frontend` | GitHub + Dockerfile in subdirectory |

Then generates a public domain for `frontend`.

Optional:

```powershell
railway variable set GEMINI_API_KEY=your_key --service ai-collateral-service
```

## Simplest local full stack (no Railway)

From the repo root:

```powershell
docker compose up --build
```

Dashboard: http://localhost:5173

## After Railway works

Paste the frontend URL into README **Live Demo**.

## Note on `.railway/railway.ts`

Kept as the desired-state description. The Windows CLI currently breaks `railway config apply`
(IaC SDK version probe). The PowerShell script uses the stable imperative CLI instead.
