# Live deployment (Railway)

Railway is the primary target: multi-service Dockerfiles, managed Postgres + Redis,
private networking between services. **RabbitMQ is not a managed add-on on Railway’s
free/hobby path** — use [CloudAMQP](https://www.cloudamqp.com/) **Little Lemur** (free
tier) and set `RABBITMQ_*` / `RABBITMQ_SSL=true` (port `5671`) on `credit-service`.

## Architecture on Railway

| Service | Origin | Notes |
| --- | --- | --- |
| `postgres` | Railway plugin | Share one DB; both card & credit use `DB_URL` |
| `redis` | Railway plugin | `REDIS_HOST` / `REDIS_PASSWORD` for card-service |
| `rabbitmq` | **CloudAMQP free** | External AMQP for `CreditCalculatedEvent` |
| `card-service` | `services/card-service/Dockerfile` | Public optional; private OK if only frontend calls it |
| `credit-service` | `services/credit-service/Dockerfile` | Needs CloudAMQP + Postgres |
| `ai-collateral-service` | `services/ai-collateral-service/Dockerfile` | `GEMINI_API_KEY` |
| `frontend` | `frontend/Dockerfile` | **Public** URL → README Live Demo; nginx proxies to backends |

## One-time setup

1. Create a [Railway](https://railway.app) project from this GitHub repo (or `railway init`).
2. Add **Postgres** and **Redis** plugins; copy connection variables into each service.
3. Create a CloudAMQP Little Lemur instance; map host/user/password/vhost into `credit-service`
   (`RABBITMQ_PORT=5671`, `RABBITMQ_SSL=true`, `RABBITMQ_VHOST=<vhost>`).
4. Deploy each Dockerfile service. Wire frontend env:

```text
CARD_SERVICE_URL=http://card-service.railway.internal:8081
CREDIT_SERVICE_URL=http://credit-service.railway.internal:8082
COLLATERAL_SERVICE_URL=http://ai-collateral-service.railway.internal:8083
SKIP_INFRA_PROBES=true
PORT=8080
NGINX_PORT=8080
```

(Use Railway’s current private DNS / service variable references if the hostname
scheme differs in your workspace.)

5. Generate a public domain on `frontend` and paste it into README **Live Demo**.

## Seed data

On empty DBs, `DemoDataSeeder` loads 3 cards and 1 approved credit application automatically.

## Local vs live `.env`

| Variable | Local Compose | Live |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://postgres:5432/bank_db` | Railway/Neon JDBC URL |
| `REDIS_HOST` | `redis` | Railway Redis host |
| `RABBITMQ_HOST` | `rabbitmq` | CloudAMQP host |
| `RABBITMQ_SSL` | `false` | `true` |
| Backend URLs for nginx | Compose DNS names | Railway private URLs |
