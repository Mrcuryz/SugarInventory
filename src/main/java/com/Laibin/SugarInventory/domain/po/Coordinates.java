package com.Laibin.SugarInventory.domain.po;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Coordinates {
    @NotNull(message = "X 坐标不能为空")
    private Double x;

    @NotNull(message = "Y 坐标不能为空")
    private Double y;
}
