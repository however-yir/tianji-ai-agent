[CmdletBinding()]
param(
    [switch]$WithNacos,
    [switch]$WithSearch,
    [switch]$Detach,
    [switch]$ResetEnv
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path (Join-Path $scriptDir "..")
Set-Location $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker is not installed or not in PATH."
}

$useComposeV2 = $false
docker compose version *> $null
if ($LASTEXITCODE -eq 0) {
    $useComposeV2 = $true
} elseif (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    throw "docker compose (v2) or docker-compose (v1) is required."
}

if (-not (Test-Path ".env.example")) {
    throw ".env.example not found in project root."
}

if ($ResetEnv -or -not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env" -Force
    Write-Host "Created .env from .env.example"
} else {
    Write-Host ".env already exists. Keeping current file (use -ResetEnv to overwrite)."
}

$composeArgs = @("-f", "docker-compose.dev.yml")

if ($WithNacos) {
    $composeArgs += @("--profile", "nacos")
}

if ($WithSearch) {
    $composeArgs += @("--profile", "search")
}

$composeArgs += @("up", "--build")

if ($Detach) {
    $composeArgs += "-d"
}

if ($useComposeV2) {
    Write-Host ("Running: docker compose " + ($composeArgs -join " "))
    & docker compose @composeArgs
} else {
    Write-Host ("Running: docker-compose " + ($composeArgs -join " "))
    & docker-compose @composeArgs
}

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Services are ready (or starting) at:"
Write-Host "- Frontend: http://127.0.0.1:5173"
Write-Host "- Backend:  http://127.0.0.1:8094"
Write-Host "- Hot API:  http://127.0.0.1:8094/session/hot"
