package com.Laibin.SugarInventory.domain.po;

import com.Laibin.SugarInventory.domain.enumObject.BindMethod;
import com.Laibin.SugarInventory.domain.enumObject.BindStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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
@TableName("user")
public class User extends BaseEntity implements UserDetails {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    private String openid;
    @TableField("role_code")
    private String roleCode;
    private String employeeId;
    private LocalDateTime createdAt;
    private BindStatus bindStatus;
    private BindMethod bindMethod;
    private String loginType;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.roleCode == null) {
            return Collections.emptyList();
        }
        // 将角色代码转换为 Spring Security 格式（如 ROLE_ADMIN）
        String authority = "ROLE_" + this.roleCode;
        return Collections.singleton(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return employeeId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return false;
    }

    @Override
    public boolean isAccountNonLocked() {
        return false;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
