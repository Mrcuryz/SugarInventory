package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.controller.ProductImportCapabilitiesController;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImportCapabilitiesControllerTest {
    @Test void uploadFlagCannotAccidentallyEnableMatchingOrExecution() {
        var access=mock(ProductImportAccessService.class);
        var user=mock(LoginUser.class);
        for (boolean enabled : new boolean[]{false,true}) {
            var controller=new ProductImportCapabilitiesController(access,enabled,false);
            var capability=controller.capabilities(user,"session").getData();
            assertEquals(enabled,capability.uploadEnabled());
            assertFalse(capability.matchingEnabled()); assertFalse(capability.executionEnabled());
            assertEquals(500,capability.maxRows());
        }
        for (boolean enabled : new boolean[]{false,true}) {
            var capability=new ProductImportCapabilitiesController(access,enabled,true).capabilities(user,"session").getData();
            assertEquals(enabled,capability.matchingEnabled());
            assertFalse(capability.executionEnabled());
        }
        verify(access,times(4)).requireCurrent(user,"session");
    }

    @Test void requiresCurrentSessionAndPermissionEvenWhenDisabled() {
        var access=mock(ProductImportAccessService.class);
        when(access.requireCurrent(null,"session")).thenThrow(new BusinessException(403,"权限已撤回"));
        var controller=new ProductImportCapabilitiesController(access,false,false);
        assertThrows(BusinessException.class,()->controller.capabilities(null,"session"));
    }
}
