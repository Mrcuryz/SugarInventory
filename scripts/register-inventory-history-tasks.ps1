param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,
    [string]$JarPath = '',
    [string]$TaskPrefix = 'LaibinInventoryHistory'
)

$ErrorActionPreference = 'Stop'
$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$currentPrincipal = [Security.Principal.WindowsPrincipal]::new($currentIdentity)
if (-not $currentPrincipal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)) {
    throw '请使用“以管理员身份运行”的 PowerShell 注册库存历史计划任务。'
}
$root = Split-Path -Parent $PSScriptRoot
$runner = Join-Path $PSScriptRoot 'run-inventory-history-job.ps1'
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $root 'target\SugarInventory-1.0-SNAPSHOT.jar'
}
if (-not (Test-Path -LiteralPath $runner)) {
    throw "任务执行脚本不存在：$runner"
}
if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "环境配置文件不存在：$EnvFile"
}
if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "后端可执行包不存在：$JarPath"
}

$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
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
    $action = New-ScheduledTaskAction -Execute $pwsh -Argument $arguments
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

Write-Host "已注册任务：$TaskPrefix-Capture、$TaskPrefix-Verify"
Write-Host '请把任务执行结果接入现有监控；退出码 2 为技术失败，3 为数据质量阻断。'
