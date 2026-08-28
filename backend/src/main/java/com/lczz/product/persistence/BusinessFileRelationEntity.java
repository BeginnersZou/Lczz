package com.lczz.product.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("business_file_relation")
public class BusinessFileRelationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private Long businessId;
    private String usageType;
    private Long fileId;
    private Integer sortOrder;
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
