# Deploy the full ZBK stack to Railway from Infrastructure as Code.
# Requires: railway CLI logged in + project linked (`railway link`).

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

Write-Host "==> Checking Railway auth..."
try {
  railway whoami | Out-Host
} catch {
  Write-Host "Not logged in. Opening browser login..."
  railway login
}

Write-Host "==> Ensuring project is linked..."
$status = railway status 2>&1 | Out-String
if ($status -match "Unauthorized|No linked project|not linked") {
  Write-Host "Run interactive link (pick/create project)..."
  railway link
}

Write-Host "==> Installing IaC SDK..."
npm.cmd install

Write-Host "==> Planning..."
railway config plan

Write-Host "==> Applying stack (Postgres, Redis, RabbitMQ, 4 apps)..."
railway config apply --yes

Write-Host "==> Generating public domain for frontend..."
railway domain --service frontend

Write-Host ""
Write-Host "Done. Open the frontend domain from the output above."
Write-Host "Optional: railway variable set GEMINI_API_KEY=... --service ai-collateral-service"
Write-Host "Paste the URL into README Live Demo when healthy."
