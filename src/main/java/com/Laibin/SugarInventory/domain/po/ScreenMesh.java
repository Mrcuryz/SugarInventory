package com.Laibin.SugarInventory.domain.po;

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
public class ScreenMesh extends BaseEntity{
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "mesh_name")
    private String meshName;

    @TableField(value = "description")
    private String description;

    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    @TableField(value = "created_by")
    private Integer createdBy;

    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    @TableField(value = "updated_by")
    private Integer updatedBy;
}
