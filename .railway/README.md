# Railway Infrastructure as Code

Defines the full ZBK stack (Postgres, Redis, RabbitMQ image, 3 JVM services, frontend).

```bash
railway login
railway link          # select/create project once
npm install
npm run railway:up    # plan + apply
railway domain --service frontend
```

See [docs/DEPLOY.md](../docs/DEPLOY.md).
