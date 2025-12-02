package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 *
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
@TableName("permission")
public class Permission extends BaseEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField(value = "perm_code")
    private String permCode;
    @TableField(value = "perm_name")
    private String permName;
    private String description;
    private LocalDateTime createdAt;
}
