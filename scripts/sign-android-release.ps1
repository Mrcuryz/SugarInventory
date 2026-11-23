param(
    [string]$BuildTools = "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0",
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputApk
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (-not $OutputApk) { throw 'Specify -OutputApk for the signed release APK.' }
$outputPath = [IO.Path]::GetFullPath($OutputApk)
if (Test-Path -LiteralPath $outputPath) { throw 'Output exists; choose a new path.' }
$keyPath = Join-Path $repo 'dt50-app/.signing/laibin-warehouse-trial-v2.jks'
$passwordPath = Join-Path $repo 'dt50-app/.signing/laibin-warehouse-trial-v2-password.dpapi.txt'
$inputApk = Join-Path $repo 'dt50-app/app/build/outputs/apk/release/app-release-unsigned.apk'
$portableKey = Join-Path $repo 'dt50-app/.signing/release-transfer.p12'
if (-not (Test-Path -LiteralPath $keyPath) -and (Test-Path -LiteralPath $portableKey)) {
    $keyPath = $portableKey
    $passwordPath = Join-Path $repo 'dt50-app/.signing/release-transfer.dpapi.txt'
}
$java = Join-Path $JavaHome 'bin/java.exe'
$signer = Join-Path $BuildTools 'lib/apksigner.jar'
foreach ($path in @($java, $signer, $keyPath, $inputApk)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing required file: $path" }
}
$remember = -not (Test-Path -LiteralPath $passwordPath)
$secure = if ($remember) {
    Read-Host '请输入签名文件的口令（本机首次使用）' -AsSecureString
} else {
    ConvertTo-SecureString ((Get-Content -LiteralPath $passwordPath -Raw -Encoding utf8).Trim())
}
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:LAIBIN_UPDATE_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    New-Item -ItemType Directory -Path (Split-Path $outputPath -Parent) -Force | Out-Null
    & $java -jar $signer sign --ks $keyPath --ks-key-alias laibin-warehouse-trial-v2 --ks-pass env:LAIBIN_UPDATE_SIGNING_PASSWORD --key-pass env:LAIBIN_UPDATE_SIGNING_PASSWORD --out $outputPath $inputApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    & $java -jar $signer verify --verbose --print-certs $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    if ($remember) {
        ConvertFrom-SecureString $secure | Set-Content -LiteralPath $passwordPath -Encoding ascii
    }
} finally {
    Remove-Item Env:LAIBIN_UPDATE_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secure.Dispose()
}
Get-FileHash -LiteralPath $outputPath -Algorithm SHA256
