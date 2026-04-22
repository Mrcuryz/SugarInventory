package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "角色查询 DTO")
public class RoleQueryDTO {
    @Schema(description = "角色关键字，匹配名称或编码")
    private String keyword;

    @Schema(description = "角色状态", example = "ENABLED")
    private String status;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer size = 10;
}
