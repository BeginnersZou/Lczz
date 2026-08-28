package com.lczz.auth.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_wechat_identity")
public class WechatIdentityEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String appId;
    private String openId;
    private String unionId;
    private Integer sessionVersion;
    private LocalDateTime lastLoginAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getOpenId() { return openId; }
    public void setOpenId(String openId) { this.openId = openId; }
    public String getUnionId() { return unionId; }
    public void setUnionId(String unionId) { this.unionId = unionId; }
    public Integer getSessionVersion() { return sessionVersion; }
    public void setSessionVersion(Integer sessionVersion) { this.sessionVersion = sessionVersion; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
