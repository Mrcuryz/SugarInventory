#requires -Version 7.0
param(
    [Parameter(Mandatory, Position=0)][ValidateNotNullOrEmpty()][string]$Notes,
    [string]$OutputDirectory,
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' })
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo 'output/android-update/feed' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$buildTools = Join-Path $Sdk 'build-tools/34.0.0'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$python = (Get-Command python -CommandType Application | Select-Object -First 1).Source
$work = New-Item -ItemType Directory -Path (Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString()))
$apk = Join-Path $work 'release.apk'
$notesPath = Join-Path $work 'notes.txt'
Push-Location (Join-Path $repo 'dt50-app')
try {
    $Notes | Set-Content -LiteralPath $notesPath -Encoding utf8NoBOM
    & .\gradlew.bat testDebugUnitTest assembleRelease
    if ($LASTEXITCODE -ne 0) { throw '安卓构建失败。' }
    & (Join-Path $PSScriptRoot 'sign-android-release.ps1') -BuildTools $buildTools -OutputApk $apk
    & $python (Join-Path $PSScriptRoot 'package-android-update.py') --apk $apk --build-tools $buildTools --java $java --notes $notesPath --output $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw '生成更新目录失败。' }
    Write-Host "发布文件位于 $OutputDirectory/android"
} finally {
    Pop-Location
    foreach ($file in @($apk, "$apk.idsig", $notesPath)) {
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    if (@(Get-ChildItem -LiteralPath $work -Force).Count -eq 0) { Remove-Item -LiteralPath $work }
}
