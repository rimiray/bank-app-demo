# Deploy the full ZBK stack to Railway from Infrastructure as Code.
# Requires: railway CLI logged in + project linked (`railway link`).
#
# Do NOT use "Deploy from GitHub" on the repo root with Railpack —
# that builds package.json as a Node app and fails. Use this script instead.

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

Write-Host "==> Checking Railway auth..."
$who = railway whoami 2>&1 | Out-String
if ($who -match "Unauthorized|Please login") {
  Write-Host "Not logged in. Opening browser login..."
  railway login
} else {
  Write-Host $who.Trim()
}

Write-Host "==> Ensuring project is linked..."
$status = railway status 2>&1 | Out-String
if ($status -match "Unauthorized|No linked project|not linked|No project") {
  Write-Host "Link this folder to a Railway project (create new or select existing)..."
  railway link
}

Write-Host "==> Installing IaC SDK in .railway/..."
Push-Location .railway
npm.cmd install
Pop-Location

Write-Host "==> Planning stack from .railway/railway.ts..."
railway config plan

Write-Host "==> Applying stack (Postgres, Redis, RabbitMQ, 4 apps)..."
railway config apply --yes

Write-Host "==> Generating public domain for frontend..."
railway domain --service frontend

Write-Host ""
Write-Host "Done. Open the frontend domain from the output above."
Write-Host "In the Railway canvas you should see many services — not one 'bank-app-demo' root service."
Write-Host "Optional: railway variable set GEMINI_API_KEY=... --service ai-collateral-service"
Write-Host "Paste the URL into README Live Demo when healthy."
