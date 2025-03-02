package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "认证成功响应VO")
public class AuthVO {
    @Schema(description = "JWT Token", example = "eyJhbGciOiJIUzUxMiJ9.eyJzd...")
    private String token;

    @Schema(description = "用户姓名", example = "张三")
    private String name;

    @Schema(description = "角色代码", example = "ADMIN")
    private String roleCode;
}