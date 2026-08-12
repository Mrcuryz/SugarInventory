package com.Laibin.SugarInventory.inventoryhistory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryHistorySchedulingScriptContractTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void registrationSupportsWindowsPowerShellFallbackAndSafePreflight() throws IOException {
        String script = read("scripts/register-inventory-history-tasks.ps1");

        assertTrue(script.contains("[switch]$PreflightOnly"));
        assertTrue(script.contains("Get-Command pwsh -ErrorAction SilentlyContinue"));
        assertTrue(script.contains("Get-Command powershell.exe -ErrorAction SilentlyContinue"));
        assertTrue(script.contains("$powerShellExecutable = $powerShellCommand.Source"));
        assertTrue(script.contains("if ($PreflightOnly)"));
        assertTrue(script.contains("Status = 'READY'"));
        assertTrue(script.indexOf("if ($PreflightOnly)")
                < script.indexOf("WindowsIdentity]::GetCurrent()"));
        assertTrue(script.contains("(Resolve-Path -LiteralPath $EnvFile).Path"));
        assertTrue(script.contains("(Resolve-Path -LiteralPath $JarPath).Path"));
    }

    @Test
    void runnerResolvesProjectRootOutsideFileInvocationContext() throws IOException {
        String script = read("scripts/run-inventory-history-job.ps1");

        assertTrue(script.contains("[string]::IsNullOrWhiteSpace($PSScriptRoot)"));
        assertTrue(script.contains("(Get-Location).Path"));
        assertTrue(script.contains("Join-Path $root 'pom.xml'"));
        assertTrue(script.contains("(Resolve-Path -LiteralPath $EnvFile).Path"));
        assertTrue(script.contains("(Resolve-Path -LiteralPath $JarPath).Path"));
        assertTrue(script.contains("RandomNumberGenerator]::Create()"));
        assertTrue(script.contains("$generator.GetBytes($buffer)"));
        assertTrue(script.contains("$generator.Dispose()"));
        assertTrue(!script.contains("RandomNumberGenerator]::GetBytes("));
        assertTrue(!script.contains("[Convert]::ToHexString("));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
