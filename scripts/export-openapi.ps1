param(
    [string]$BaseUrl = $env:WAREHOUSE_API_BASE_URL,
    [string]$OutFile = "docs/openapi.json",
    [string]$Token = $env:WAREHOUSE_API_TOKEN
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = "http://localhost:8080"
}

$BaseUrl = $BaseUrl.TrimEnd("/")
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path (Join-Path $scriptDir "..")
$outPath = Join-Path $projectRoot $OutFile
$outDir = Split-Path -Parent $outPath

if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}

$headers = @{ Accept = "application/json" }
if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $headers.Authorization = "Bearer $Token"
}

$response = Invoke-WebRequest -Uri "$BaseUrl/v3/api-docs" -Headers $headers -TimeoutSec 30 -UseBasicParsing
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($outPath, $response.Content, $utf8NoBom)

Write-Host "Exported OpenAPI document to $outPath"
