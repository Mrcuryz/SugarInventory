package com.Laibin.SugarInventory.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    @Test
    void openApiSnapshotUsesDeploymentRelativeServerUrl() {
        var openApi = new SwaggerConfig().customOpenAPI();

        assertThat(openApi.getServers()).hasSize(1);
        assertThat(openApi.getServers().get(0).getUrl()).isEqualTo("/");
    }
}
