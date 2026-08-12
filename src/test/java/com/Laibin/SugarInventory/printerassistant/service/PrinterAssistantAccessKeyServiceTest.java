package com.Laibin.SugarInventory.printerassistant.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrinterAssistantAccessKeyServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesAndPersistsStablePerInstallationKey() throws Exception {
        Path keyPath = tempDir.resolve("access-key.txt");
        PrinterAssistantAccessKeyService first = new PrinterAssistantAccessKeyService(keyPath, null);

        String generated = first.getAccessKey();

        assertThat(generated).hasSizeGreaterThanOrEqualTo(PrinterAssistantAccessKeyService.MINIMUM_KEY_LENGTH);
        assertThat(Files.readString(keyPath)).isEqualTo(generated);
        assertThat(first.matches(generated)).isTrue();
        assertThat(first.matches(generated + "x")).isFalse();

        PrinterAssistantAccessKeyService restarted = new PrinterAssistantAccessKeyService(keyPath, null);
        assertThat(restarted.getAccessKey()).isEqualTo(generated);
    }

    @Test
    void configuredKeyMustMeetMinimumLength() {
        PrinterAssistantAccessKeyService service = new PrinterAssistantAccessKeyService(
                tempDir.resolve("unused.txt"),
                "too-short"
        );

        assertThatThrownBy(service::getAccessKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 24 个字符");
    }
}
