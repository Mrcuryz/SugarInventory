package com.Laibin.SugarInventory.equipment;

import com.Laibin.SugarInventory.equipment.controller.EquipmentAssetController;
import com.Laibin.SugarInventory.equipment.controller.EquipmentBasicDataController;
import com.Laibin.SugarInventory.equipment.controller.EquipmentRepairController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentControllerPermissionTest {
    @Test
    void assetAndRepairWritesUseDedicatedPermissions() {
        assertPermission(EquipmentAssetController.class, "create", "hasAuthority('equipment:asset:create')");
        assertPermission(EquipmentAssetController.class, "update", "hasAuthority('equipment:asset:update')");
        assertPermission(EquipmentAssetController.class, "delete", "hasAuthority('equipment:asset:delete')");
        assertPermission(EquipmentAssetController.class, "export", "hasAuthority('equipment:asset:export')");
        assertPermission(EquipmentRepairController.class, "create", "hasAuthority('equipment:repair:create')");
        assertPermission(EquipmentRepairController.class, "update", "hasAuthority('equipment:repair:update')");
        assertPermission(EquipmentRepairController.class, "delete", "hasAuthority('equipment:repair:delete')");
        assertPermission(EquipmentRepairController.class, "export", "hasAuthority('equipment:repair:export')");
    }

    @Test
    void codeRuleChangesRequireStrongerPermissionThanOtherBasicData() {
        assertPermission(EquipmentBasicDataController.class, "createUnit", "hasAuthority('equipment:config:manage')");
        assertPermission(EquipmentBasicDataController.class, "createManufacturer", "hasAuthority('equipment:config:manage')");
        assertPermission(EquipmentBasicDataController.class, "createCodeRule", "hasAuthority('equipment:code-rule:manage')");
        assertPermission(EquipmentBasicDataController.class, "updateCodeRule", "hasAuthority('equipment:code-rule:manage')");
        assertPermission(EquipmentBasicDataController.class, "deleteCodeRule", "hasAuthority('equipment:code-rule:manage')");
    }

    private void assertPermission(Class<?> type, String methodName, String expression) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
}
