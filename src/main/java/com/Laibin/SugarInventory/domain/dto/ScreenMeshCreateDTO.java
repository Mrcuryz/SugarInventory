package com.Laibin.SugarInventory.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Setter
@Getter
@NoArgsConstructor
public class ScreenMeshCreateDTO extends BaseDTO {
    @TableField(value = "mesh_name")
    private String meshName;

    @TableField(value = "description")
    private String description;

    @Override
    public Integer getId() {
        return 0;
    }
}
