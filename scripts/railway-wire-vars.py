import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAILWAY = shutil.which("railway") or shutil.which("railway.cmd")
if not RAILWAY:
    raise SystemExit("railway CLI not found on PATH")


def railway(*args: str) -> None:
    redacted = list(args)
    for i, a in enumerate(redacted):
        if a.startswith("GEMINI_API_KEY="):
            redacted[i] = "GEMINI_API_KEY=***"
    print(">", "railway", *redacted)
    proc = subprocess.run([RAILWAY, *args], cwd=ROOT, capture_output=True, text=True)
    if proc.stdout.strip():
        print(proc.stdout.strip())
    if proc.returncode != 0:
        print(proc.stderr)
        raise SystemExit(proc.returncode)


def set_var(service: str, pair: str) -> None:
    railway("variable", "set", pair, "--service", service, "--skip-deploys")


def main() -> None:
    env_file = ROOT / ".env"
    gemini_key = ""
    gemini_model = "gemini-3.1-flash-lite"
    credit_rate = "8.5"
    if env_file.exists():
        text = env_file.read_text(encoding="utf-8")
        m = re.search(r"^GEMINI_API_KEY=(.+)$", text, re.M)
        if m:
            gemini_key = m.group(1).strip().strip('"').strip("'")
        m = re.search(r"^GEMINI_MODEL=(.+)$", text, re.M)
        if m:
            gemini_model = m.group(1).strip()
        m = re.search(r"^CREDIT_ANNUAL_INTEREST_RATE=(.+)$", text, re.M)
        if m:
            credit_rate = m.group(1).strip()

    # Explicit PORT so ${{service.PORT}} resolves for frontend private URLs.
    # Railway injects PORT at runtime, but cross-service refs need a stored var.
    set_var("card-service", "PORT=8081")
    set_var("credit-service", "PORT=8082")
    set_var("ai-collateral-service", "PORT=8083")

    for pair in [
        "PGHOST=${{Postgres.PGHOST}}",
        "PGPORT=${{Postgres.PGPORT}}",
        "PGDATABASE=${{Postgres.PGDATABASE}}",
        "PGUSER=${{Postgres.PGUSER}}",
        "PGPASSWORD=${{Postgres.PGPASSWORD}}",
        "SPRING_DATA_REDIS_URL=${{Redis.REDIS_URL}}",
        "REDIS_HOST=${{Redis.REDISHOST}}",
        "REDIS_PORT=${{Redis.REDISPORT}}",
        "REDIS_PASSWORD=${{Redis.REDIS_PASSWORD}}",
    ]:
        set_var("card-service", pair)

    for pair in [
        "PGHOST=${{Postgres.PGHOST}}",
        "PGPORT=${{Postgres.PGPORT}}",
        "PGDATABASE=${{Postgres.PGDATABASE}}",
        "PGUSER=${{Postgres.PGUSER}}",
        "PGPASSWORD=${{Postgres.PGPASSWORD}}",
        "RABBITMQ_HOST=${{rabbitmq.RAILWAY_PRIVATE_DOMAIN}}",
        "RABBITMQ_PORT=5672",
        "RABBITMQ_USER=bank_guest",
        "RABBITMQ_PASSWORD=bank_guest",
        "RABBITMQ_VHOST=/",
        "RABBITMQ_SSL=false",
        f"CREDIT_ANNUAL_INTEREST_RATE={credit_rate}",
    ]:
        set_var("credit-service", pair)

    set_var("ai-collateral-service", f"GEMINI_MODEL={gemini_model}")
    if gemini_key and "YOUR_" not in gemini_key.upper():
        set_var("ai-collateral-service", f"GEMINI_API_KEY={gemini_key}")
    else:
        print("WARN: GEMINI_API_KEY missing/placeholder — fallback heuristic will be used")

    for pair in [
        "CARD_SERVICE_URL=http://${{card-service.RAILWAY_PRIVATE_DOMAIN}}:${{card-service.PORT}}",
        "CREDIT_SERVICE_URL=http://${{credit-service.RAILWAY_PRIVATE_DOMAIN}}:${{credit-service.PORT}}",
        "COLLATERAL_SERVICE_URL=http://${{ai-collateral-service.RAILWAY_PRIVATE_DOMAIN}}:${{ai-collateral-service.PORT}}",
        "NGINX_RESOLVER=[fd12::10]",
        "SKIP_INFRA_PROBES=true",
    ]:
        set_var("frontend", pair)

    print("Variables wired.")


if __name__ == "__main__":
    main()
