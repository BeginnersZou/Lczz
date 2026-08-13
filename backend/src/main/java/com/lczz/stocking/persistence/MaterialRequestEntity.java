package com.lczz.stocking.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("material_request")
public class MaterialRequestEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestNo;
    private Long orderId;
    private Long installerUserId;
    private String requestStatus;
    private String remark;
    private LocalDateTime submittedAt;
    private Long completedBy;
    private LocalDateTime completedAt;
    private Long voidedBy;
    private LocalDateTime voidedAt;
    private String voidReason;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getInstallerUserId() { return installerUserId; }
    public void setInstallerUserId(Long installerUserId) { this.installerUserId = installerUserId; }
    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getCompletedBy() { return completedBy; }
    public void setCompletedBy(Long completedBy) { this.completedBy = completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getVoidedBy() { return voidedBy; }
    public void setVoidedBy(Long voidedBy) { this.voidedBy = voidedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
