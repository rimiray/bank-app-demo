# Deploy full ZBK stack to the linked Railway project (imperative CLI — works on Windows).
# Prefer this over `railway config apply` (IaC TS version-check is broken under Windows npm CLI).
#
# Usage:
#   railway login
#   .\scripts\railway-up.ps1

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$Repo = "rimiray/bank-app-demo"
$Branch = "main"
$ProjectName = if ($env:RAILWAY_PROJECT_NAME) { $env:RAILWAY_PROJECT_NAME } else { "zbk-bank-demo" }

function Invoke-Railway {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
  & railway @Args
  if ($LASTEXITCODE -ne 0) {
    throw "railway $($Args -join ' ') failed with exit $LASTEXITCODE"
  }
}

function Get-ServiceNames {
  $raw = railway service list --json 2>&1 | Out-String
  if ($LASTEXITCODE -ne 0) { return @() }
  try {
    $parsed = $raw | ConvertFrom-Json
  } catch {
    return @()
  }
  # CLI may return an array of services or { services: [...] }
  if ($parsed -is [System.Array]) {
    return @($parsed | ForEach-Object { $_.name })
  }
  if ($parsed.services) {
    return @($parsed.services | ForEach-Object { $_.name })
  }
  if ($parsed.edges) {
    return @($parsed.edges | ForEach-Object { $_.node.name })
  }
  return @()
}

function Ensure-LinkedProject {
  $status = railway status 2>&1 | Out-String
  if ($status -match "No linked project|not linked") {
    Write-Host "Creating project '$ProjectName' (interactive link fails when project list is empty)..."
    Invoke-Railway init --name $ProjectName --json
  } else {
    Write-Host $status.Trim()
  }
}

function Ensure-Database([string]$Engine) {
  $names = Get-ServiceNames
  # Railway database services are often titled Postgres / Redis
  $match = $names | Where-Object { $_ -match "(?i)^$Engine$" -or $_ -match "(?i)$Engine" }
  if ($match) {
    Write-Host "  · $Engine already present ($($match -join ', '))"
    return
  }
  Write-Host "  · adding $Engine..."
  Invoke-Railway add --database $Engine --json
}

function Ensure-ImageService([string]$Name, [string]$Image, [string[]]$Variables) {
  $names = Get-ServiceNames
  if ($names -contains $Name) {
    Write-Host "  · $Name already present"
    return
  }
  Write-Host "  · adding $Name from image $Image..."
  $args = @("add", "--image", $Image, "--service", $Name, "--json")
  foreach ($v in $Variables) { $args += @("--variables", $v) }
  Invoke-Railway @args
}

function Ensure-RepoService([string]$Name, [string]$RootDirectory) {
  $names = Get-ServiceNames
  if ($names -contains $Name) {
    Write-Host "  · $Name already present — updating root directory"
  } else {
    Write-Host "  · adding $Name from GitHub $Repo ($RootDirectory)..."
    Invoke-Railway add --repo $Repo --branch $Branch --service $Name --json
  }
  # Force Dockerfile build from subdirectory (not Railpack on repo root)
  Invoke-Railway environment edit `
    --service-config $Name "source.rootDirectory" $RootDirectory `
    --service-config $Name "build.builder" "DOCKERFILE" `
    --service-config $Name "build.dockerfilePath" "Dockerfile" `
    --message "configure $Name monorepo root + Dockerfile"
}

function Set-Var([string]$Service, [string]$Pair) {
  Invoke-Railway variable set $Pair --service $Service --skip-deploys
}

Write-Host "==> Auth"
$who = railway whoami 2>&1 | Out-String
if ($who -match "Unauthorized|Please login") {
  Invoke-Railway login
} else {
  Write-Host $who.Trim()
}

Ensure-LinkedProject

Write-Host "==> Data plane"
Ensure-Database postgres
Ensure-Database redis
Ensure-ImageService "rabbitmq" "rabbitmq:3-management-alpine" @(
  "RABBITMQ_DEFAULT_USER=bank_guest",
  "RABBITMQ_DEFAULT_PASS=bank_guest"
)

# Discover actual DB service names (Railway may capitalize them)
$all = Get-ServiceNames
$PgName = ($all | Where-Object { $_ -match '(?i)postgres' } | Select-Object -First 1)
$RedisName = ($all | Where-Object { $_ -match '(?i)redis' } | Select-Object -First 1)
if (-not $PgName) { throw "Postgres service not found after add" }
if (-not $RedisName) { throw "Redis service not found after add" }
Write-Host "Using Postgres='$PgName', Redis='$RedisName'"

Write-Host "==> App services"
Ensure-RepoService "card-service" "/services/card-service"
Ensure-RepoService "credit-service" "/services/credit-service"
Ensure-RepoService "ai-collateral-service" "/services/ai-collateral-service"
Ensure-RepoService "frontend" "/frontend"

Write-Host "==> Wiring variables"
# card-service
Set-Var card-service "PGHOST=`${{$PgName.PGHOST}}"
Set-Var card-service "PGPORT=`${{$PgName.PGPORT}}"
Set-Var card-service "PGDATABASE=`${{$PgName.PGDATABASE}}"
Set-Var card-service "PGUSER=`${{$PgName.PGUSER}}"
Set-Var card-service "PGPASSWORD=`${{$PgName.PGPASSWORD}}"
Set-Var card-service "SPRING_DATA_REDIS_URL=`${{$RedisName.REDIS_URL}}"
Set-Var card-service "REDIS_HOST=`${{$RedisName.REDISHOST}}"
Set-Var card-service "REDIS_PORT=`${{$RedisName.REDISPORT}}"
Set-Var card-service "REDIS_PASSWORD=`${{$RedisName.REDIS_PASSWORD}}"
Invoke-Railway environment edit `
  --service-config card-service "deploy.healthcheckPath" "/actuator/health" `
  --service-config card-service "deploy.healthcheckTimeout" "120" `
  --message "card-service healthcheck"

# credit-service
Set-Var credit-service "PGHOST=`${{$PgName.PGHOST}}"
Set-Var credit-service "PGPORT=`${{$PgName.PGPORT}}"
Set-Var credit-service "PGDATABASE=`${{$PgName.PGDATABASE}}"
Set-Var credit-service "PGUSER=`${{$PgName.PGUSER}}"
Set-Var credit-service "PGPASSWORD=`${{$PgName.PGPASSWORD}}"
Set-Var credit-service "RABBITMQ_HOST=`${{rabbitmq.RAILWAY_PRIVATE_DOMAIN}}"
Set-Var credit-service "RABBITMQ_PORT=5672"
Set-Var credit-service "RABBITMQ_USER=bank_guest"
Set-Var credit-service "RABBITMQ_PASSWORD=bank_guest"
Set-Var credit-service "RABBITMQ_VHOST=/"
Set-Var credit-service "RABBITMQ_SSL=false"
Invoke-Railway environment edit `
  --service-config credit-service "deploy.healthcheckPath" "/actuator/health" `
  --service-config credit-service "deploy.healthcheckTimeout" "120" `
  --message "credit-service healthcheck"

# ai-collateral-service
Set-Var ai-collateral-service "GEMINI_MODEL=gemini-3.1-flash-lite"
Invoke-Railway environment edit `
  --service-config ai-collateral-service "deploy.healthcheckPath" "/actuator/health" `
  --service-config ai-collateral-service "deploy.healthcheckTimeout" "90" `
  --message "ai-collateral healthcheck"

# frontend → private backends
Set-Var frontend "CARD_SERVICE_URL=http://`${{card-service.RAILWAY_PRIVATE_DOMAIN}}:`${{card-service.PORT}}"
Set-Var frontend "CREDIT_SERVICE_URL=http://`${{credit-service.RAILWAY_PRIVATE_DOMAIN}}:`${{credit-service.PORT}}"
Set-Var frontend "COLLATERAL_SERVICE_URL=http://`${{ai-collateral-service.RAILWAY_PRIVATE_DOMAIN}}:`${{ai-collateral-service.PORT}}"
Set-Var frontend "SKIP_INFRA_PROBES=true"
Invoke-Railway environment edit `
  --service-config frontend "deploy.healthcheckPath" "/api/health" `
  --service-config frontend "deploy.healthcheckTimeout" "60" `
  --message "frontend healthcheck"

Write-Host "==> Public domain for frontend"
railway domain --service frontend 2>&1 | Out-Host

Write-Host ""
Write-Host "Done. Open the Railway canvas — you should see postgres/redis/rabbitmq + 4 apps."
Write-Host "Delete any old root 'bank-app-demo' Railpack service if it is still there."
Write-Host "Optional: railway variable set GEMINI_API_KEY=... --service ai-collateral-service"
Write-Host "When frontend is healthy, paste its URL into README Live Demo."
