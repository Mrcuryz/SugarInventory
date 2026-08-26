package com.Laibin.SugarInventory.equipment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class EquipmentBasicDataVOs {
    private EquipmentBasicDataVOs() {
    }

    @Data
    @NoArgsConstructor
    public static class EquipmentUnitVO {
        private Integer id;
        private String unitCode;
        private String unitName;
        private Integer sortOrder;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    public static class EquipmentCategoryVO {
        private Integer id;
        private String categoryName;
        private String majorCode;
        private Integer defaultSequenceStart;
        private Integer defaultSequenceEnd;
        private Integer sortOrder;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    public static class EquipmentManufacturerVO {
        private Integer id;
        private String manufacturerCode;
        private String manufacturerName;
        private String address;
        private String contactPerson;
        private String phone;
        private String fax;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    public static class EquipmentTypeVO {
        private Integer id;
        private String typeName;
        private Integer sortOrder;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    public static class EquipmentCodeRuleVO {
        private Integer id;
        private String companyCode;
        private Integer unitId;
        private String unitCode;
        private String unitName;
        private Integer categoryId;
        private String categoryName;
        private String majorCode;
        private String sectionCode;
        private Integer sequenceStart;
        private Integer sequenceEnd;
        private Integer nextSequence;
        private Boolean enabled;
        private Integer version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentOptionVO {
        private Integer id;
        private String code;
        private String name;
        private Boolean enabled;
    }
}
