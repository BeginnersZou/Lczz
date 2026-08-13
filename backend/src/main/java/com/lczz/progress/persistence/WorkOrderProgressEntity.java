package com.lczz.progress.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("work_order_progress")
public class WorkOrderProgressEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long installerUserId;
    private String progressType;
    private String description;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getInstallerUserId() { return installerUserId; }
    public void setInstallerUserId(Long installerUserId) { this.installerUserId = installerUserId; }
    public String getProgressType() { return progressType; }
    public void setProgressType(String progressType) { this.progressType = progressType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
