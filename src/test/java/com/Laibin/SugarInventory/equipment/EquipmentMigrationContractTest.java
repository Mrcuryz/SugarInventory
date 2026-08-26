package com.Laibin.SugarInventory.equipment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentMigrationContractTest {
    @Test
    void migrationCreatesOnlyDesignedEquipmentTablesAndPermissions() throws Exception {
        String sql = Files.readString(Path.of("migrations", "2026-08-26-add-equipment-management-module.sql"), StandardCharsets.UTF_8);

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS `equipment_unit`",
                "CREATE TABLE IF NOT EXISTS `equipment_category`",
                "CREATE TABLE IF NOT EXISTS `equipment_manufacturer`",
                "CREATE TABLE IF NOT EXISTS `equipment_renovation_type`",
                "CREATE TABLE IF NOT EXISTS `equipment_repair_type`",
                "CREATE TABLE IF NOT EXISTS `equipment_code_rule`",
                "CREATE TABLE IF NOT EXISTS `equipment_asset`",
                "CREATE TABLE IF NOT EXISTS `equipment_repair_record`",
                "`parent_equipment_id`",
                "equipment:asset:view",
                "equipment:repair:view",
                "equipment:code-rule:manage");
        assertThat(sql).doesNotContain("equipment_inspection", "equipment_maintenance", "equipment_fault", "equipment_spare_part");
    }
}
