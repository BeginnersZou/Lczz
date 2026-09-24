package com.lczz.servicepage.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("service_page_config")
public class ServicePageConfigEntity {
    @TableId
    private Long id;
    private String companyName;
    private String companySubtitle;
    private String slogan;
    private String profileText;
    private String phonePrimary;
    private String phoneSecondary;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String businessHours;
    private Boolean brandVisible;
    private Boolean servicesVisible;
    private Boolean profileVisible;
    private Boolean galleryVisible;
    private Boolean advantagesVisible;
    private Boolean contactVisible;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCompanySubtitle() { return companySubtitle; }
    public void setCompanySubtitle(String companySubtitle) { this.companySubtitle = companySubtitle; }
    public String getSlogan() { return slogan; }
    public void setSlogan(String slogan) { this.slogan = slogan; }
    public String getProfileText() { return profileText; }
    public void setProfileText(String profileText) { this.profileText = profileText; }
    public String getPhonePrimary() { return phonePrimary; }
    public void setPhonePrimary(String phonePrimary) { this.phonePrimary = phonePrimary; }
    public String getPhoneSecondary() { return phoneSecondary; }
    public void setPhoneSecondary(String phoneSecondary) { this.phoneSecondary = phoneSecondary; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }
    public Boolean getBrandVisible() { return brandVisible; }
    public void setBrandVisible(Boolean brandVisible) { this.brandVisible = brandVisible; }
    public Boolean getServicesVisible() { return servicesVisible; }
    public void setServicesVisible(Boolean servicesVisible) { this.servicesVisible = servicesVisible; }
    public Boolean getProfileVisible() { return profileVisible; }
    public void setProfileVisible(Boolean profileVisible) { this.profileVisible = profileVisible; }
    public Boolean getGalleryVisible() { return galleryVisible; }
    public void setGalleryVisible(Boolean galleryVisible) { this.galleryVisible = galleryVisible; }
    public Boolean getAdvantagesVisible() { return advantagesVisible; }
    public void setAdvantagesVisible(Boolean advantagesVisible) { this.advantagesVisible = advantagesVisible; }
    public Boolean getContactVisible() { return contactVisible; }
    public void setContactVisible(Boolean contactVisible) { this.contactVisible = contactVisible; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
