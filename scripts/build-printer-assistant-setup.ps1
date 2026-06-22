param(
    [string]$Version = "1.0.0",
    [ValidateSet("auto", "bundle", "app-image", "exe")]
    [string]$PackageType = "auto"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BuildStamp = Get-Date -Format "yyyyMMddHHmmss"
$DeliverableRoot = Join-Path $ProjectRoot ("deliverables\\printer-assistant\\" + $BuildStamp)
$PortableDir = Join-Path $DeliverableRoot "portable"
$PortableLibDir = Join-Path $PortableDir "lib"
$InputDir = Join-Path $DeliverableRoot "jpackage-input"
$DistDir = Join-Path $DeliverableRoot "dist"
$DocsDir = Join-Path $DeliverableRoot "docs"
$JdkHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\\Program Files\\Java\\jdk-21" }
$JPackage = Join-Path $JdkHome "bin\\jpackage.exe"
$PlainJarPath = Join-Path $ProjectRoot "target\\SugarInventory-1.0-SNAPSHOT-printer-assistant.jar"
$PortableJarName = "LaibinPrinterAssistant.jar"
$PortableJarPath = Join-Path $PortableDir $PortableJarName
$StartBatPath = Join-Path $PortableDir "start-printer-assistant-gui.bat"
$SummaryPath = Join-Path $DeliverableRoot "delivery-summary.txt"
$SurefireReportPath = Join-Path $ProjectRoot "target\\surefire-reports\\TEST-com.Laibin.SugarInventory.printerassistant.service.LocalPrinterConfigServiceTest.xml"
$MainClass = "com.Laibin.SugarInventory.printerassistant.desktop.PrinterAssistantDesktopApplication"
$AppDisplayName = "Label Printer Assistant"
$MenuGroup = "Laibin Sugar"
$VendorName = "Laibin Sugar"
$AppDescription = "Laibin Sugar Printer Assistant"
$AdminGuide = "docs\\打印说明\\标签打印助手-管理员安装说明-2026-04-24.md"
$TroubleshootingGuide = "docs\\打印说明\\标签打印助手-常见问题排查-2026-04-24.md"
$PackagingGuide = "docs\\打印说明\\标签打印助手-setup打包后续步骤-2026-04-24.md"
$AppImageDir = Join-Path $DistDir $AppDisplayName

function New-Directory {
    param([string]$Path)
    if (!(Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-PrinterAssistantDependencies {
    param([string]$ReportPath)

    if (!(Test-Path -LiteralPath $ReportPath)) {
        throw "Surefire report not found: $ReportPath"
    }

    [xml]$report = Get-Content -LiteralPath $ReportPath -Raw
    $classPathProperty = $report.testsuite.properties.property |
        Where-Object { $_.name -eq "java.class.path" } |
        Select-Object -First 1

    if ($null -eq $classPathProperty) {
        throw "java.class.path property not found in surefire report: $ReportPath"
    }

    $entries = $classPathProperty.value -split ";"
    $skipPatterns = @(
        "\\junit\\",
        "\\mockito\\",
        "\\assertj\\",
        "\\hamcrest\\",
        "\\xmlunit\\",
        "\\opentest4j\\",
        "\\apiguardian\\",
        "\\byte-buddy-agent\\",
        "\\spring-boot-starter-test\\",
        "\\spring-boot-test\\",
        "\\spring-boot-test-autoconfigure\\",
        "\\json-path\\",
        "\\android-json\\"
    )

    $result = New-Object System.Collections.Generic.List[string]
    foreach ($entry in $entries) {
        if ([string]::IsNullOrWhiteSpace($entry)) {
            continue
        }
        if (!$entry.EndsWith(".jar")) {
            continue
        }
        if (!(Test-Path -LiteralPath $entry)) {
            continue
        }

        $shouldSkip = $false
        foreach ($pattern in $skipPatterns) {
            if ($entry -match $pattern) {
                $shouldSkip = $true
                break
            }
        }
        if ($shouldSkip) {
            continue
        }

        if (-not $result.Contains($entry)) {
            $result.Add($entry)
        }
    }

    return $result
}

New-Directory -Path $PortableDir
New-Directory -Path $PortableLibDir
New-Directory -Path $InputDir
New-Directory -Path $DistDir
New-Directory -Path $DocsDir

Push-Location $ProjectRoot
try {
    mvn -q clean "-Dtest=LocalPrinterConfigServiceTest" test
    mvn -q "-Pprinter-assistant" "-DskipTests" package

    if (!(Test-Path -LiteralPath $PlainJarPath)) {
        throw "printer assistant jar not found: $PlainJarPath"
    }

    $dependencies = Get-PrinterAssistantDependencies -ReportPath $SurefireReportPath
    if ($dependencies.Count -eq 0) {
        throw "No runtime dependencies resolved from surefire report."
    }

    Copy-Item -LiteralPath $PlainJarPath -Destination $PortableJarPath -Force
    Copy-Item -LiteralPath $PlainJarPath -Destination (Join-Path $InputDir $PortableJarName) -Force

    foreach ($dependency in $dependencies) {
        $fileName = Split-Path -Leaf $dependency
        Copy-Item -LiteralPath $dependency -Destination (Join-Path $PortableLibDir $fileName) -Force
        Copy-Item -LiteralPath $dependency -Destination (Join-Path $InputDir $fileName) -Force
    }

    @'
@echo off
setlocal
set SCRIPT_DIR=%~dp0
set JAVA_EXE=
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA_EXE set "JAVA_EXE=javaw"
start "" "%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "%SCRIPT_DIR%LaibinPrinterAssistant.jar;%SCRIPT_DIR%lib\*" com.Laibin.SugarInventory.printerassistant.desktop.PrinterAssistantDesktopApplication
endlocal
'@ | Set-Content -LiteralPath $StartBatPath -Encoding ASCII

    Copy-Item -LiteralPath $AdminGuide -Destination (Join-Path $DocsDir "printer-assistant-admin-guide.md") -Force
    Copy-Item -LiteralPath $TroubleshootingGuide -Destination (Join-Path $DocsDir "printer-assistant-troubleshooting.md") -Force
    Copy-Item -LiteralPath $PackagingGuide -Destination (Join-Path $DocsDir "printer-assistant-packaging-steps.md") -Force

    $appImageBuilt = $false
    $setupExeBuilt = $false
    $setupExePath = $null
    $hasJPackage = Test-Path -LiteralPath $JPackage

    if (($PackageType -eq "app-image" -or $PackageType -eq "exe") -and -not $hasJPackage) {
        throw "jpackage not found: $JPackage"
    }

    if ($PackageType -ne "bundle" -and $hasJPackage) {
        & $JPackage `
            --type app-image `
            --input $InputDir `
            --dest $DistDir `
            --name $AppDisplayName `
            --main-jar $PortableJarName `
            --main-class $MainClass `
            --app-version $Version `
            --vendor $VendorName `
            --description $AppDescription `
            --java-options "-Dfile.encoding=UTF-8"

        if ($LASTEXITCODE -ne 0) {
            throw "jpackage app-image build failed with exit code $LASTEXITCODE"
        }

        $appImageBuilt = Test-Path -LiteralPath $AppImageDir
    }

    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    $canBuildExe = ($null -ne $candle) -and ($null -ne $light)

    if ($PackageType -eq "exe") {
        & $JPackage `
            --type exe `
            --input $InputDir `
            --dest $DistDir `
            --name $AppDisplayName `
            --main-jar $PortableJarName `
            --main-class $MainClass `
            --app-version $Version `
            --vendor $VendorName `
            --description $AppDescription `
            --java-options "-Dfile.encoding=UTF-8" `
            --win-shortcut `
            --win-menu `
            --win-menu-group $MenuGroup

        if ($LASTEXITCODE -ne 0) {
            throw "jpackage exe build failed with exit code $LASTEXITCODE"
        }

        $setupExe = Get-ChildItem -LiteralPath $DistDir -File -Filter "*.exe" | Select-Object -First 1
        if ($null -eq $setupExe) {
            throw "jpackage reported success but no setup exe was found under: $DistDir"
        }

        $setupExeBuilt = $true
        $setupExePath = $setupExe.FullName
    }

    $summary = @(
        "Printer assistant deliverable root: $DeliverableRoot",
        "Portable bundle: $PortableDir",
        "Portable launcher: $StartBatPath",
        "Portable dependency count: $($dependencies.Count)",
        "app-image built: $appImageBuilt",
        "app-image path: $(if ($appImageBuilt) { $AppImageDir } else { '(not generated)' })",
        "setup.exe environment ready: $canBuildExe",
        "setup.exe built: $setupExeBuilt",
        "setup.exe path: $(if ($setupExePath) { $setupExePath } else { '(not generated)' })",
        "Installed app display name: $AppDisplayName",
        "Expected app entry after install: Desktop shortcut '$AppDisplayName' and Start Menu '$MenuGroup\\$AppDisplayName'",
        "Desktop shortcut requested: $($PackageType -eq 'exe')",
        "Start Menu entry requested: $($PackageType -eq 'exe')",
        "Docs directory: $DocsDir"
    )
    $summary -join [Environment]::NewLine | Set-Content -LiteralPath $SummaryPath -Encoding UTF8
    Write-Host ($summary -join [Environment]::NewLine)
}
finally {
    Pop-Location
}
