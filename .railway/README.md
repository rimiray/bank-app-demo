# Railway Infrastructure as Code

Defines the full ZBK stack (Postgres, Redis, RabbitMQ image, 3 JVM services, frontend).

```powershell
railway login
railway link
.\scripts\railway-up.ps1
```

SDK lives in this folder (`.railway/package.json`) so the **repo root is not a Node app**
and accidental Railpack deploys of `/` do not pick up a fake `package.json`.

See [docs/DEPLOY.md](../docs/DEPLOY.md).
