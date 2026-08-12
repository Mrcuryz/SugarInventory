param(
    [string]$OpenApiPath = "docs/openapi.json"
)

$ErrorActionPreference = "Stop"
$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    Join-Path (Get-Location).Path 'scripts'
} else {
    $PSScriptRoot
}
$root = Split-Path -Parent $scriptRoot
$gatewaySource = Join-Path $root "src/main/java/com/Laibin/SugarInventory/agent/internal/service/impl/McpInternalAgentToolGatewayService.java"
$openApiFile = Join-Path $root $OpenApiPath

if (-not (Test-Path -LiteralPath $openApiFile)) {
    throw "OpenAPI snapshot is missing: $openApiFile"
}

$source = Get-Content -LiteralPath $gatewaySource -Raw -Encoding UTF8
$openApi = Get-Content -LiteralPath $openApiFile -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedPaths = [regex]::Matches($source, 'entry\("[^"]+", "([^"]+)"\)') |
    ForEach-Object { $_.Groups[1].Value -split '; ' } |
    Sort-Object -Unique
$actualPaths = @($openApi.paths.PSObject.Properties.Name)
$missing = @($expectedPaths | Where-Object { -not $actualPaths.Contains($_) })

if ($missing.Count -gt 0) {
    throw "OpenAPI is missing Agent Gateway paths: $($missing -join ', ')"
}

Write-Host "Agent OpenAPI/Registry check passed: $($expectedPaths.Count) upstream paths are present."
