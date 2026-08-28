package com.lczz.common.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("operation_audit_log")
public class OperationAuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorUserId;
    private String operationType;
    private String businessType;
    private String businessId;
    private String requestId;
    private String beforeJson;
    private String afterJson;
    private String resultCode;
    private String clientIp;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
