package com.lczz.auth.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_role")
public class RoleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String roleName;
    private Boolean enabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
