# Configure Railway app services via GraphQL (environment edit ignores invalid Builder=DOCKERFILE).
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$EnvId = "949470c5-3af1-4827-a909-a3319849f3f0"
$Repo = "rimiray/bank-app-demo"
$Branch = "main"

function Invoke-ServiceUpdate([string]$ServiceId, [hashtable]$InputObj) {
  $vars = @{
    serviceId     = $ServiceId
    environmentId = $EnvId
    input         = $InputObj
  } | ConvertTo-Json -Compress -Depth 8
  $varsPath = Join-Path $env:TEMP "railway-vars-$ServiceId.json"
  $mutPath = Join-Path $env:TEMP "railway-mut.graphql"
  Set-Content -Encoding ascii -NoNewline -Path $varsPath -Value $vars
  Set-Content -Encoding ascii -Path $mutPath -Value @'
mutation($serviceId: String!, $environmentId: String, $input: ServiceInstanceUpdateInput!) {
  serviceInstanceUpdate(serviceId: $serviceId, environmentId: $environmentId, input: $input)
}
'@
  Write-Host "Updating service $ServiceId ..."
  railway api --file $mutPath --variables "@$varsPath"
  if ($LASTEXITCODE -ne 0) { throw "serviceInstanceUpdate failed for $ServiceId" }
}

function Get-Services {
  railway service list --json | ConvertFrom-Json
}

function Ensure-RepoService([string]$Name) {
  $list = Get-Services
  $existing = $list | Where-Object { $_.name -eq $Name } | Select-Object -First 1
  if ($existing) {
    Write-Host "Service $Name exists ($($existing.id))"
    return $existing.id
  }
  Write-Host "Adding $Name from GitHub..."
  $out = railway add --repo $Repo --branch $Branch --service $Name --json 2>&1 | Out-String
  Write-Host $out
  $list = Get-Services
  $created = $list | Where-Object { $_.name -eq $Name } | Select-Object -First 1
  if (-not $created) { throw "Failed to create $Name" }
  return $created.id
}

# Ensure apps exist
$cardId = Ensure-RepoService "card-service"
$creditId = Ensure-RepoService "credit-service"
$aiId = Ensure-RepoService "ai-collateral-service"
$feId = Ensure-RepoService "frontend"

Invoke-ServiceUpdate $cardId @{
  rootDirectory      = "/services/card-service"
  dockerfilePath     = "Dockerfile"
  healthcheckPath    = "/actuator/health"
  healthcheckTimeout = 120
}
Invoke-ServiceUpdate $creditId @{
  rootDirectory      = "/services/credit-service"
  dockerfilePath     = "Dockerfile"
  healthcheckPath    = "/actuator/health"
  healthcheckTimeout = 120
}
Invoke-ServiceUpdate $aiId @{
  rootDirectory      = "/services/ai-collateral-service"
  dockerfilePath     = "Dockerfile"
  healthcheckPath    = "/actuator/health"
  healthcheckTimeout = 90
}
Invoke-ServiceUpdate $feId @{
  rootDirectory      = "/frontend"
  dockerfilePath     = "Dockerfile"
  healthcheckPath    = "/api/health"
  healthcheckTimeout = 60
}

Write-Host "Config updates done."
