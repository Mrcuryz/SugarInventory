package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Setter
@Getter
@NoArgsConstructor
public class ScreenMeshUpdateDTO extends BaseDTO {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "mesh_name")
    private String meshName;

    @TableField(value = "description")
    private String description;
}
