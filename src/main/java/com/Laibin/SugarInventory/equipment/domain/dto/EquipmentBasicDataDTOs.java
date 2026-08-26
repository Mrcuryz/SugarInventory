package com.Laibin.SugarInventory.equipment.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

public final class EquipmentBasicDataDTOs {
    private EquipmentBasicDataDTOs() {
    }

    @Data
    public static class EquipmentBasicQueryDTO {
        private String keyword;
        private Boolean enabled;
        private Integer page = 1;
        private Integer size = 10;
    }

    @Data
    public static class EquipmentUnitSaveDTO {
        @NotBlank
        @Size(max = 20)
        private String unitCode;
        @NotBlank
        @Size(max = 100)
        private String unitName;
        private Integer sortOrder = 0;
        private Boolean enabled = true;
    }

    @Data
    public static class EquipmentCategorySaveDTO {
        @NotBlank
        @Size(max = 100)
        private String categoryName;
        @NotBlank
        @Size(max = 10)
        private String majorCode;
        @Min(0)
        @Max(9999)
        private Integer defaultSequenceStart;
        @Min(0)
        @Max(9999)
        private Integer defaultSequenceEnd;
        private Integer sortOrder = 0;
        private Boolean enabled = true;

        @AssertTrue(message = "默认号段必须同时填写且起点不能大于终点")
        public boolean isRangeValid() {
            if (defaultSequenceStart == null && defaultSequenceEnd == null) return true;
            return defaultSequenceStart != null && defaultSequenceEnd != null
                    && defaultSequenceStart <= defaultSequenceEnd;
        }
    }

    @Data
    public static class EquipmentManufacturerSaveDTO {
        @NotBlank
        @Size(max = 30)
        private String manufacturerCode;
        @NotBlank
        @Size(max = 150)
        private String manufacturerName;
        @Size(max = 255)
        private String address;
        @Size(max = 50)
        private String contactPerson;
        @Size(max = 50)
        private String phone;
        @Size(max = 50)
        private String fax;
        @Size(max = 1000)
        private String remark;
    }

    @Data
    public static class EquipmentTypeSaveDTO {
        @NotBlank
        @Size(max = 100)
        private String typeName;
        private Integer sortOrder = 0;
        private Boolean enabled = true;
    }

    @Data
    public static class EquipmentCodeRuleSaveDTO {
        @NotBlank
        @Size(max = 20)
        private String companyCode = "LBYX";
        @NotNull
        private Integer unitId;
        @NotNull
        private Integer categoryId;
        @NotBlank
        @Size(max = 10)
        private String sectionCode;
        @NotNull
        @Min(0)
        @Max(9999)
        private Integer sequenceStart;
        @NotNull
        @Min(0)
        @Max(9999)
        private Integer sequenceEnd;
        @NotNull
        @Min(0)
        @Max(10000)
        private Integer nextSequence;
        private Boolean enabled = true;

        @AssertTrue(message = "编号游标必须位于号段起点和终点后一位之间")
        public boolean isRangeValid() {
            return sequenceStart != null && sequenceEnd != null && nextSequence != null
                    && sequenceStart <= sequenceEnd
                    && nextSequence >= sequenceStart && nextSequence <= sequenceEnd + 1;
        }
    }

    @Data
    public static class EquipmentEnabledUpdateDTO {
        @NotNull
        private Boolean enabled;
    }
}
