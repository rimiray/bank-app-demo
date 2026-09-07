#!/bin/sh
set -eu

OUT=/var/cache/nginx/health.json
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
  cards=$(probe_http http://card-service:8081/actuator/health)
  credit=$(probe_http http://credit-service:8082/actuator/health)
  collateral=$(probe_http http://ai-collateral-service:8083/actuator/health)
  redis=$(probe_tcp redis 6379)
  postgres=$(probe_tcp postgres 5432)
  rabbitmq=$(probe_http "http://${RABBIT_USER}:${RABBIT_PASS}@rabbitmq:15672/api/overview")

  cat >"$OUT" <<EOF
{"checkedAt":"$(date -u +%Y-%m-%dT%H:%M:%SZ)","services":{"cards":{"name":"Card Service","port":8081,"up":${cards}},"credit":{"name":"Credit Service","port":8082,"up":${credit}},"collateral":{"name":"AI Collateral","port":8083,"up":${collateral}},"redis":{"name":"Redis","port":6379,"up":${redis}},"rabbitmq":{"name":"RabbitMQ","port":5672,"up":${rabbitmq}},"postgres":{"name":"PostgreSQL","port":5432,"up":${postgres}}}}
EOF
}

write_health || true

while true; do
  write_health || true
  sleep 5
done