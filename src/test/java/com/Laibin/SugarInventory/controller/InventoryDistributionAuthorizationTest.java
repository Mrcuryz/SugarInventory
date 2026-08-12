package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDistributionAuthorizationTest {
    @Test
    void distributionRequiresInventoryViewPermission() throws Exception {
        PreAuthorize authorization = InventoryController.class
                .getMethod("getInventoryDistribution", InventoryDistributionQueryDTO.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('inventory:view')");
    }
}
