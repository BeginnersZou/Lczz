package com.lczz.stocking.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("material_request_item")
public class MaterialRequestItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long productId;
    private Long skuId;
    private String productCodeSnapshot;
    private String skuCodeSnapshot;
    private String productNameSnapshot;
    private String skuSpecSnapshot;
    private String modelSpecSnapshot;
    private String unitSnapshot;
    private BigDecimal displayPriceSnapshot;
    private BigDecimal requestedQuantity;
    private BigDecimal preparedQuantity;
    private String itemStatus;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public void setProductCodeSnapshot(String productCodeSnapshot) { this.productCodeSnapshot = productCodeSnapshot; }
    public String getSkuCodeSnapshot() { return skuCodeSnapshot; }
    public void setSkuCodeSnapshot(String skuCodeSnapshot) { this.skuCodeSnapshot = skuCodeSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }
    public String getSkuSpecSnapshot() { return skuSpecSnapshot; }
    public void setSkuSpecSnapshot(String skuSpecSnapshot) { this.skuSpecSnapshot = skuSpecSnapshot; }
    public String getModelSpecSnapshot() { return modelSpecSnapshot; }
    public void setModelSpecSnapshot(String modelSpecSnapshot) { this.modelSpecSnapshot = modelSpecSnapshot; }
    public String getUnitSnapshot() { return unitSnapshot; }
    public void setUnitSnapshot(String unitSnapshot) { this.unitSnapshot = unitSnapshot; }
    public BigDecimal getDisplayPriceSnapshot() { return displayPriceSnapshot; }
    public void setDisplayPriceSnapshot(BigDecimal displayPriceSnapshot) { this.displayPriceSnapshot = displayPriceSnapshot; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(BigDecimal requestedQuantity) { this.requestedQuantity = requestedQuantity; }
    public BigDecimal getPreparedQuantity() { return preparedQuantity; }
    public void setPreparedQuantity(BigDecimal preparedQuantity) { this.preparedQuantity = preparedQuantity; }
    public String getItemStatus() { return itemStatus; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
