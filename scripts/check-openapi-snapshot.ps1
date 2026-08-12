param(
    [string]$BaseUrl = $env:WAREHOUSE_API_BASE_URL,
    [string]$OpenApiPath = 'docs/openapi.json',
    [string]$Token = $env:WAREHOUSE_API_TOKEN
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($BaseUrl)) { $BaseUrl = 'http://localhost:8080' }
$BaseUrl = $BaseUrl.TrimEnd('/')
$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location).Path 'scripts'
} else {
    $PSScriptRoot
}
$root = Split-Path -Parent $scriptRoot
$snapshotPath = Join-Path $root $OpenApiPath
if (-not (Test-Path -LiteralPath $snapshotPath)) { throw "OpenAPI snapshot is missing: $snapshotPath" }

function ConvertTo-CanonicalObject {
    param($Value)
    if ($null -eq $Value -or $Value -is [string] -or $Value.GetType().IsPrimitive -or $Value -is [decimal]) {
        return $Value
    }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        $ordered = [ordered]@{}
        foreach ($property in @($Value.PSObject.Properties | Sort-Object Name)) {
            $ordered[$property.Name] = ConvertTo-CanonicalObject $property.Value
        }
        return $ordered
    }
    if ($Value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | Sort-Object)) {
            $ordered[[string]$key] = ConvertTo-CanonicalObject $Value[$key]
        }
        return $ordered
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        return @($Value | ForEach-Object { ConvertTo-CanonicalObject $_ })
    }
    return $Value
}

$headers = @{ Accept = 'application/json' }
if (-not [string]::IsNullOrWhiteSpace($Token)) { $headers.Authorization = "Bearer $Token" }
$live = (Invoke-WebRequest -Uri "$BaseUrl/v3/api-docs" -Headers $headers -TimeoutSec 30 -UseBasicParsing).Content | ConvertFrom-Json
$snapshot = Get-Content -LiteralPath $snapshotPath -Raw -Encoding UTF8 | ConvertFrom-Json
$liveCanonical = ConvertTo-CanonicalObject $live | ConvertTo-Json -Depth 100 -Compress
$snapshotCanonical = ConvertTo-CanonicalObject $snapshot | ConvertTo-Json -Depth 100 -Compress
if ($liveCanonical -cne $snapshotCanonical) {
    $livePathCount = @($live.paths.PSObject.Properties).Count
    $snapshotPathCount = @($snapshot.paths.PSObject.Properties).Count
    throw "OpenAPI snapshot is stale: livePaths=$livePathCount snapshotPaths=$snapshotPathCount. Run scripts/export-openapi.ps1 against this build."
}

$pathCount = @($live.paths.PSObject.Properties).Count
Write-Host "OpenAPI snapshot check passed: $pathCount paths match the live application."
