package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public final class AdministrationAgentVO {
    private AdministrationAgentVO() {}

    @Data @Builder
    public static class EmployeeRosterResult {
        private String dataScope;
        private long total;
        private int page;
        private int size;
        private List<EmployeeRow> records;
        private List<String> limitations;
    }

    @Data @Builder
    public static class EmployeeRow {
        private String employeeId;
        private String name;
        private String maskedMobile;
        private String department;
        private String position;
        private String status;
        private String roleCode;
    }

    @Data @Builder
    public static class RoleCatalogResult {
        private String dataScope;
        private long total;
        private int page;
        private int size;
        private List<RoleRow> records;
        private List<String> limitations;
    }

    @Data @Builder
    public static class RoleRow {
        private String roleName;
        private String roleCode;
        private String description;
        private String status;
        private Integer permissionCount;
        private Integer activeEmployeeCount;
    }

    @Data @Builder
    public static class RolePermissionSummaryResult {
        private String dataScope;
        private String roleName;
        private String roleCode;
        private String status;
        private Integer activeEmployeeCount;
        private Integer permissionCount;
        private List<PermissionRow> permissions;
        private List<String> limitations;
    }

    @Data @Builder
    public static class PermissionRow {
        private String permissionCode;
        private String permissionName;
        private String permissionGroup;
        private String description;
    }
}
