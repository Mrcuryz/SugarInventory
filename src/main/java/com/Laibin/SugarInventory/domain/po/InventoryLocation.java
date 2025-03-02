package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Getter
@Setter
public class InventoryLocation extends BaseEntity {
    @TableId
    private Integer id;

    @TableField("inventory_id")
    private Integer inventoryId;

    @TableField("coordinate_x")
    private Double coordinateX;

    @TableField("coordinate_y")

    private Double coordinateY;

    @TableField("quantity")
    private Integer quantity;
}
