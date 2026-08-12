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
$scriptDir = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location).Path 'scripts'
} else {
    $PSScriptRoot
}
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
$document = $response.Content | ConvertFrom-Json

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

$canonicalJson = ConvertTo-CanonicalObject $document | ConvertTo-Json -Depth 100 -Compress
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($outPath, $canonicalJson + [Environment]::NewLine, $utf8NoBom)

Write-Host "Exported OpenAPI document to $outPath"
