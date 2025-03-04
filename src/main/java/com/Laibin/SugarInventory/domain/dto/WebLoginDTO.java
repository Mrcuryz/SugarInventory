package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WebLoginDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String password;
}
