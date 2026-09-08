import json
import subprocess
import tempfile
from pathlib import Path

ENV_ID = "949470c5-3af1-4827-a909-a3319849f3f0"
MUT = """mutation($serviceId: String!, $environmentId: String, $input: ServiceInstanceUpdateInput!) {
  serviceInstanceUpdate(serviceId: $serviceId, environmentId: $environmentId, input: $input)
}
"""

SERVICES = [
    (
        "d15ea0f4-515c-4a69-8381-ada281c40dd9",
        {
            "rootDirectory": "/services/card-service",
            "dockerfilePath": "Dockerfile",
            "healthcheckPath": "/actuator/health",
            "healthcheckTimeout": 120,
        },
    ),
    (
        "85988595-3d0e-47f6-9ac8-4d7e5390e024",
        {
            "rootDirectory": "/services/credit-service",
            "dockerfilePath": "Dockerfile",
            "healthcheckPath": "/actuator/health",
            "healthcheckTimeout": 120,
        },
    ),
    (
        "640fbfc9-b50f-445c-b2a0-d3c546f95d07",
        {
            "rootDirectory": "/services/ai-collateral-service",
            "dockerfilePath": "Dockerfile",
            "healthcheckPath": "/actuator/health",
            "healthcheckTimeout": 90,
        },
    ),
    (
        "4924bc97-a619-4d07-8d65-876a784e8c64",
        {
            "rootDirectory": "/frontend",
            "dockerfilePath": "Dockerfile",
            "healthcheckPath": "/api/health",
            "healthcheckTimeout": 60,
        },
    ),
]


def main() -> None:
    mut = Path(tempfile.gettempdir()) / "railway-mut.graphql"
    mut.write_text(MUT, encoding="ascii")
    for service_id, input_obj in SERVICES:
        vars_path = Path(tempfile.gettempdir()) / f"railway-vars-{service_id}.json"
        vars_path.write_text(
            json.dumps(
                {
                    "serviceId": service_id,
                    "environmentId": ENV_ID,
                    "input": input_obj,
                }
            ),
            encoding="ascii",
        )
        print(f"==> update {service_id} {input_obj.get('rootDirectory')}")
        proc = subprocess.run(
            [
                "railway.cmd",
                "api",
                "--file",
                str(mut),
                "--variables",
                f"@{vars_path}",
            ],
            capture_output=True,
            text=True,
            check=False,
            shell=True,
        )
        print(proc.stdout or proc.stderr)
        if proc.returncode != 0:
            raise SystemExit(proc.returncode)


if __name__ == "__main__":
    main()
