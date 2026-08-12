param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,
    [string]$JarPath = '',
    [string]$TaskPrefix = 'LaibinInventoryHistory',
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'
$scriptRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
    (Get-Location).Path
} else {
    $PSScriptRoot
}
$rootCandidate = if (Test-Path -LiteralPath (Join-Path $scriptRoot 'pom.xml')) {
    $scriptRoot
} else {
    Join-Path $scriptRoot '..'
}
$root = (Resolve-Path -LiteralPath $rootCandidate).Path
if (-not (Test-Path -LiteralPath (Join-Path $root 'pom.xml'))) {
    throw "Unable to resolve project root from script or current directory: $scriptRoot"
}
$runner = Join-Path $root 'scripts\run-inventory-history-job.ps1'
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $root 'target\SugarInventory-1.0-SNAPSHOT.jar'
}
if (-not (Test-Path -LiteralPath $runner)) {
    throw "Inventory history runner was not found: $runner"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file was not found: $EnvFile"
}
if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Backend executable jar was not found: $JarPath"
}
$runner = (Resolve-Path -LiteralPath $runner).Path
$EnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path

$configKeys = @{}
foreach ($line in [System.IO.File]::ReadAllLines($EnvFile)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $configKeys[$Matches[1]] = $true
    }
}
foreach ($requiredKey in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    if (-not $configKeys.ContainsKey($requiredKey)) {
        throw "Environment file is missing required key: $requiredKey"
    }
}
if ($null -eq (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java runtime was not found.'
}

$powerShellCommand = Get-Command pwsh -ErrorAction SilentlyContinue
if ($null -eq $powerShellCommand) {
    $powerShellCommand = Get-Command powershell.exe -ErrorAction SilentlyContinue
}
if ($null -eq $powerShellCommand) {
    throw 'PowerShell 7 or Windows PowerShell was not found.'
}
$powerShellExecutable = $powerShellCommand.Source

if ($PreflightOnly) {
    [pscustomobject]@{
        Status = 'READY'
        PowerShellExecutable = $powerShellExecutable
        Runner = $runner
        EnvFile = $EnvFile
        JarPath = $JarPath
        CaptureTask = "$TaskPrefix-Capture"
        VerifyTask = "$TaskPrefix-Verify"
    } | ConvertTo-Json -Compress
    exit 0
}

$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$currentPrincipal = [Security.Principal.WindowsPrincipal]::new($currentIdentity)
if (-not $currentPrincipal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)) {
    throw 'Preflight passed. Run PowerShell as Administrator to register the inventory history tasks.'
}

$principal = New-ScheduledTaskPrincipal `
    -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType S4U `
    -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 20)

function Register-InventoryHistoryTask {
    param(
        [string]$Name,
        [string]$Mode,
        [datetime]$At
    )
    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$runner`"",
        '-Mode', $Mode,
        '-EnvFile', "`"$EnvFile`"",
        '-JarPath', "`"$JarPath`""
    ) -join ' '
    $action = New-ScheduledTaskAction -Execute $powerShellExecutable -Argument $arguments
    $trigger = New-ScheduledTaskTrigger -Daily -At $At
    Register-ScheduledTask `
        -TaskName $Name `
        -Action $action `
        -Trigger $trigger `
        -Settings $settings `
        -Principal $principal `
        -Force | Out-Null
}

Register-InventoryHistoryTask `
    -Name "$TaskPrefix-Capture" `
    -Mode 'Capture' `
    -At ([datetime]'00:00')
Register-InventoryHistoryTask `
    -Name "$TaskPrefix-Verify" `
    -Mode 'Verify' `
    -At ([datetime]'00:15')

Write-Host "Registered tasks: $TaskPrefix-Capture, $TaskPrefix-Verify"
Write-Host 'Connect task results to monitoring: exit code 2 is a technical failure; exit code 3 is a data-quality block.'
