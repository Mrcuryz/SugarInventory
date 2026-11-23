#requires -Version 7.0
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$keys = Join-Path (Split-Path $PSScriptRoot -Parent) 'dt50-app/.signing'
$portableKey = Join-Path $keys 'release-transfer.p12'
if (Test-Path -LiteralPath $portableKey) { throw "已有交接文件：$portableKey" }
$sourceKey = Join-Path $keys 'laibin-warehouse-trial-v2.jks'
$sourcePassword = ConvertTo-SecureString ((Get-Content -LiteralPath (Join-Path $keys 'laibin-warehouse-trial-v2-password.dpapi.txt') -Raw).Trim())
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sourcePassword)
$tempKey = Join-Path $keys ("transfer-" + [guid]::NewGuid() + ".p12")
try {
    $env:LAIBIN_TRANSFER_SOURCE = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    Write-Host '请在 keytool 提示时设置并确认交接口令，第二台电脑首次签名时使用。'
    & (Join-Path $JavaHome 'bin/keytool.exe') -importkeystore -srckeystore $sourceKey -srcstorepass:env LAIBIN_TRANSFER_SOURCE -srcalias laibin-warehouse-trial-v2 -srckeypass:env LAIBIN_TRANSFER_SOURCE -deststoretype PKCS12 -destkeystore $tempKey
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $tempKey)) { throw '签名文件导出失败。' }
    Move-Item -LiteralPath $tempKey -Destination $portableKey
    Write-Host "交接文件：$portableKey"
} finally {
    Remove-Item Env:LAIBIN_TRANSFER_SOURCE -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $sourcePassword.Dispose()
    if (Test-Path -LiteralPath $tempKey) { Remove-Item -LiteralPath $tempKey }
}
