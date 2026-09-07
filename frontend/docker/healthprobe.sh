#!/bin/sh
set -eu

OUT=/var/cache/nginx/health.json

CARD_HEALTH="${CARD_HEALTH_URL:-${CARD_SERVICE_URL:-http://card-service:8081}/actuator/health}"
CREDIT_HEALTH="${CREDIT_HEALTH_URL:-${CREDIT_SERVICE_URL:-http://credit-service:8082}/actuator/health}"
COLLATERAL_HEALTH="${COLLATERAL_HEALTH_URL:-${COLLATERAL_SERVICE_URL:-http://ai-collateral-service:8083}/actuator/health}"

REDIS_HOST_PROBE="${REDIS_HOST:-redis}"
REDIS_PORT_PROBE="${REDIS_PORT:-6379}"
POSTGRES_HOST_PROBE="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT_PROBE="${POSTGRES_PORT:-5432}"

RABBIT_HEALTH="${RABBITMQ_HEALTH_URL:-}"
RABBIT_USER="${RABBITMQ_USER:-bank_guest}"
RABBIT_PASS="${RABBITMQ_PASSWORD:-bank_guest}"

probe_http() {
  if curl -sf "$1" >/dev/null 2>&1; then
    echo true
  else
    echo false
  fi
}

probe_tcp() {
  if nc -z -w 1 "$1" "$2" >/dev/null 2>&1; then
    echo true
  else
    echo false
  fi
}

write_health() {
  cards=$(probe_http "$CARD_HEALTH")
  credit=$(probe_http "$CREDIT_HEALTH")
  collateral=$(probe_http "$COLLATERAL_HEALTH")
  redis=$(probe_tcp "$REDIS_HOST_PROBE" "$REDIS_PORT_PROBE")
  postgres=$(probe_tcp "$POSTGRES_HOST_PROBE" "$POSTGRES_PORT_PROBE")

  if [ -n "$RABBIT_HEALTH" ]; then
    rabbitmq=$(probe_http "$RABBIT_HEALTH")
  else
    rabbitmq=$(probe_http "http://${RABBIT_USER}:${RABBIT_PASS}@rabbitmq:15672/api/overview")
  fi

  # When infra is managed externally (Neon/Upstash/CloudAMQP), TCP probes from the
  # frontend container often cannot reach private hosts — treat apps-up as enough
  # for the Architecture tab when SKIP_INFRA_PROBES=true.
  if [ "${SKIP_INFRA_PROBES:-false}" = "true" ]; then
    redis=true
    postgres=true
    rabbitmq=true
  fi

  cat >"$OUT" <<EOF
{"checkedAt":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","services":{"cards":{"name":"Card Service","port":8081,"up":${cards}},"credit":{"name":"Credit Service","port":8082,"up":${credit}},"collateral":{"name":"AI Collateral","port":8083,"up":${collateral}},"redis":{"name":"Redis","port":6379,"up":${redis}},"rabbitmq":{"name":"RabbitMQ","port":5672,"up":${rabbitmq}},"postgres":{"name":"PostgreSQL","port":5432,"up":${postgres}}}}
EOF
}

write_health || true

while true; do
  write_health || true
  sleep 5
done
