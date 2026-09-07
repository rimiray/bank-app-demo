import {
  defineRailway,
  github,
  group,
  image,
  postgres,
  project,
  redis,
  service,
} from "railway/iac";

const REPO = "rimiray/bank-app-demo";
const BRANCH = "main";

/**
 * One-file Railway project definition for the whole stack.
 *
 *   railway login
 *   railway link
 *   npm install
 *   npm run railway:up
 */
export default defineRailway(() => {
  const db = postgres("postgres");
  const cache = redis("redis");

  // Self-hosted broker (avoids a separate CloudAMQP account for the demo).
  const rabbitmq = service("rabbitmq", {
    source: image("rabbitmq:3-management-alpine"),
    env: {
      RABBITMQ_DEFAULT_USER: "bank_guest",
      RABBITMQ_DEFAULT_PASS: "bank_guest",
    },
  });

  const cardService = service("card-service", {
    source: github(REPO, {
      branch: BRANCH,
      rootDirectory: "services/card-service",
    }),
    build: {
      builder: "DOCKERFILE",
      dockerfilePath: "Dockerfile",
      watchPatterns: ["services/card-service/**"],
    },
    healthcheck: "/actuator/health",
    healthcheckTimeout: 120,
    env: {
      PGHOST: db.env.PGHOST,
      PGPORT: db.env.PGPORT,
      PGDATABASE: db.env.PGDATABASE,
      PGUSER: db.env.PGUSER,
      PGPASSWORD: db.env.PGPASSWORD,
      SPRING_DATA_REDIS_URL: cache.env.REDIS_URL,
      REDIS_HOST: cache.env.REDISHOST,
      REDIS_PORT: cache.env.REDISPORT,
      REDIS_PASSWORD: cache.env.REDIS_PASSWORD,
    },
  });

  const creditService = service("credit-service", {
    source: github(REPO, {
      branch: BRANCH,
      rootDirectory: "services/credit-service",
    }),
    build: {
      builder: "DOCKERFILE",
      dockerfilePath: "Dockerfile",
      watchPatterns: ["services/credit-service/**"],
    },
    healthcheck: "/actuator/health",
    healthcheckTimeout: 120,
    env: {
      PGHOST: db.env.PGHOST,
      PGPORT: db.env.PGPORT,
      PGDATABASE: db.env.PGDATABASE,
      PGUSER: db.env.PGUSER,
      PGPASSWORD: db.env.PGPASSWORD,
      RABBITMQ_HOST: rabbitmq.env.RAILWAY_PRIVATE_DOMAIN,
      RABBITMQ_PORT: "5672",
      RABBITMQ_USER: "bank_guest",
      RABBITMQ_PASSWORD: "bank_guest",
      RABBITMQ_VHOST: "/",
      RABBITMQ_SSL: "false",
    },
  });

  const collateralService = service("ai-collateral-service", {
    source: github(REPO, {
      branch: BRANCH,
      rootDirectory: "services/ai-collateral-service",
    }),
    build: {
      builder: "DOCKERFILE",
      dockerfilePath: "Dockerfile",
      watchPatterns: ["services/ai-collateral-service/**"],
    },
    healthcheck: "/actuator/health",
    healthcheckTimeout: 90,
    env: {
      // Override in Railway UI / CLI — do not commit real keys.
      GEMINI_API_KEY: "",
      GEMINI_MODEL: "gemini-3.1-flash-lite",
    },
  });

  const frontend = service("frontend", {
    source: github(REPO, {
      branch: BRANCH,
      rootDirectory: "frontend",
    }),
    build: {
      builder: "DOCKERFILE",
      dockerfilePath: "Dockerfile",
      watchPatterns: ["frontend/**"],
    },
    healthcheck: "/api/health",
    healthcheckTimeout: 60,
    env: {
      // Railway expands ${{service.*}} when the variable value is stored as this template.
      CARD_SERVICE_URL:
        "http://${{card-service.RAILWAY_PRIVATE_DOMAIN}}:${{card-service.PORT}}",
      CREDIT_SERVICE_URL:
        "http://${{credit-service.RAILWAY_PRIVATE_DOMAIN}}:${{credit-service.PORT}}",
      COLLATERAL_SERVICE_URL:
        "http://${{ai-collateral-service.RAILWAY_PRIVATE_DOMAIN}}:${{ai-collateral-service.PORT}}",
      SKIP_INFRA_PROBES: "true",
    },
  });

  return project("bank-app-demo", {
    resources: [
      group("Data", [db, cache, rabbitmq]),
      group("Apps", [cardService, creditService, collateralService, frontend]),
    ],
  });
});
