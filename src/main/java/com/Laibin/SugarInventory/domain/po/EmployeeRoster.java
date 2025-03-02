package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * @since 2025-02-20
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
@TableName("employee_roster")
public class EmployeeRoster extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String employeeId;
    private String name;
    private String mobile;
    private String department;
    private String position;
    private String status;
    private String roleCode;
    private LocalDateTime createdAt;
}
