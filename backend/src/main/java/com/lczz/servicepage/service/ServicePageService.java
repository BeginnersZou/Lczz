package com.lczz.servicepage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.persistence.FileAssetRecord;
import com.lczz.file.persistence.FileAssetRecordMapper;
import com.lczz.file.persistence.FileRelationRecord;
import com.lczz.file.persistence.FileRelationRecordMapper;
import com.lczz.file.service.FileService;
import com.lczz.servicepage.persistence.ServicePageConfigEntity;
import com.lczz.servicepage.persistence.ServicePageConfigMapper;
import com.lczz.servicepage.persistence.ServicePageItemEntity;
import com.lczz.servicepage.persistence.ServicePageItemMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicePageService {
    public static final long PAGE_ID = 1L;
    public static final String BUSINESS_TYPE = "SERVICE_PAGE";
    public static final String GALLERY_USAGE = "COMPANY_DISPLAY";
    public static final int MAX_GALLERY_IMAGES = 20;
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final String HERO_STAT = "HERO_STAT";
    private static final String SERVICE = "SERVICE";
    private static final String PROFILE_TAG = "PROFILE_TAG";
    private static final String ADVANTAGE = "ADVANTAGE";

    private final ServicePageConfigMapper configMapper;
    private final ServicePageItemMapper itemMapper;
    private final FileAssetRecordMapper fileMapper;
    private final FileRelationRecordMapper relationMapper;
    private final FileService fileService;

    public ServicePageService(ServicePageConfigMapper configMapper, ServicePageItemMapper itemMapper,
                              FileAssetRecordMapper fileMapper, FileRelationRecordMapper relationMapper,
                              FileService fileService) {
        this.configMapper = configMapper;
        this.itemMapper = itemMapper;
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.fileService = fileService;
    }

    public ServicePageView get(AuthenticatedUser actor) {
        ServicePageConfigEntity config = requireConfig();
        List<ServicePageItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<ServicePageItemEntity>()
                .eq(ServicePageItemEntity::getServicePageId, PAGE_ID)
                .orderByAsc(ServicePageItemEntity::getItemType)
                .orderByAsc(ServicePageItemEntity::getSortOrder)
                .orderByAsc(ServicePageItemEntity::getId));
        return toView(actor, config, items);
    }

    @Transactional
    public ServicePageView update(AuthenticatedUser actor, ServicePageCommand command) {
        ServicePageConfigEntity config = requireConfig();
        List<Long> galleryImageIds = validateImages(command.galleryImageFileIds());
        config.setCompanyName(command.companyName().trim());
        config.setCompanySubtitle(command.companySubtitle().trim());
        config.setSlogan(command.slogan().trim());
        config.setProfileText(command.profileText().trim());
        config.setPhonePrimary(command.phonePrimary().trim());
        config.setPhoneSecondary(trimToNull(command.phoneSecondary()));
        config.setAddress(command.address().trim());
        config.setLongitude(command.longitude());
        config.setLatitude(command.latitude());
        config.setBusinessHours(command.businessHours().trim());
        config.setBrandVisible(command.brandVisible());
        config.setServicesVisible(command.servicesVisible());
        config.setProfileVisible(command.profileVisible());
        config.setGalleryVisible(command.galleryVisible());
        config.setAdvantagesVisible(command.advantagesVisible());
        config.setContactVisible(command.contactVisible());
        config.setUpdatedBy(actor.userId());
        configMapper.updateById(config);

        itemMapper.delete(new LambdaQueryWrapper<ServicePageItemEntity>()
                .eq(ServicePageItemEntity::getServicePageId, PAGE_ID));
        insertItems(HERO_STAT, command.heroStats());
        insertItems(SERVICE, command.services());
        insertItems(PROFILE_TAG, command.profileTags().stream().map(tag -> new ContentItem(tag, null)).toList());
        insertItems(ADVANTAGE, command.advantages());
        replaceImages(galleryImageIds, actor.userId());
        return get(actor);
    }

    private ServicePageView toView(AuthenticatedUser actor, ServicePageConfigEntity config,
                                   List<ServicePageItemEntity> items) {
        Map<String, List<ServicePageItemEntity>> byType = items.stream()
                .collect(Collectors.groupingBy(ServicePageItemEntity::getItemType));
        List<ContentItem> heroStats = toItems(byType.get(HERO_STAT));
        List<ContentItem> services = toItems(byType.get(SERVICE));
        List<String> profileTags = byType.getOrDefault(PROFILE_TAG, List.of()).stream()
                .map(ServicePageItemEntity::getTitle).toList();
        List<ContentItem> advantages = toItems(byType.get(ADVANTAGE));
        return new ServicePageView(config.getId(), config.getCompanyName(), config.getCompanySubtitle(),
                config.getSlogan(), Boolean.TRUE.equals(config.getBrandVisible()), heroStats,
                Boolean.TRUE.equals(config.getServicesVisible()), services,
                config.getProfileText(), Boolean.TRUE.equals(config.getProfileVisible()), profileTags,
                Boolean.TRUE.equals(config.getGalleryVisible()), loadImages(actor),
                Boolean.TRUE.equals(config.getAdvantagesVisible()), advantages,
                Boolean.TRUE.equals(config.getContactVisible()), config.getPhonePrimary(), config.getPhoneSecondary(),
                config.getAddress(), config.getLongitude(), config.getLatitude(), config.getBusinessHours(),
                config.getUpdatedAt());
    }

    private List<ContentItem> toItems(List<ServicePageItemEntity> items) {
        if (items == null) return List.of();
        return items.stream().map(item -> new ContentItem(item.getTitle(), item.getDescription())).toList();
    }

    private List<FileView> loadImages(AuthenticatedUser actor) {
        List<FileRelationRecord> relations = relationMapper.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, BUSINESS_TYPE)
                .eq(FileRelationRecord::getBusinessId, PAGE_ID)
                .eq(FileRelationRecord::getUsageType, GALLERY_USAGE)
                .orderByAsc(FileRelationRecord::getSortOrder)
                .orderByAsc(FileRelationRecord::getId));
        if (relations.isEmpty()) return List.of();
        Map<Long, FileAssetRecord> files = fileMapper.selectBatchIds(relations.stream()
                        .map(FileRelationRecord::getFileId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(FileAssetRecord::getId, Function.identity()));
        List<FileView> result = new ArrayList<>();
        for (FileRelationRecord relation : relations) {
            FileAssetRecord file = files.get(relation.getFileId());
            if (file != null && !Boolean.TRUE.equals(file.getDeleted())) {
                result.add(new FileView(file.getId(), fileService.issueAccess(actor, file.getId()).url()));
            }
        }
        return result;
    }

    private List<Long> validateImages(List<Long> rawFileIds) {
        List<Long> ids = rawFileIds == null ? List.of() : rawFileIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        if (ids.size() > MAX_GALLERY_IMAGES) {
            throw new BusinessException("SERVICE_GALLERY_LIMIT", "公司展示最多上传 20 张图片");
        }
        if (ids.isEmpty()) return ids;
        List<FileAssetRecord> files = fileMapper.selectBatchIds(ids);
        if (files.size() != ids.size() || files.stream().anyMatch(file -> Boolean.TRUE.equals(file.getDeleted()))) {
            throw new BusinessException("FILE_NOT_FOUND", "存在无效或已删除的公司展示图片");
        }
        for (FileAssetRecord file : files) {
            if (file.getMimeType() == null || !file.getMimeType().startsWith("image/")) {
                throw new BusinessException("SERVICE_IMAGE_TYPE_INVALID", "公司展示仅支持图片文件");
            }
            if (file.getFileSize() == null || file.getFileSize() > MAX_IMAGE_BYTES) {
                throw new BusinessException(413, "SERVICE_IMAGE_TOO_LARGE", "单张公司展示图片不能超过 10 MB");
            }
        }
        return ids;
    }

    private void insertItems(String type, List<ContentItem> items) {
        for (int index = 0; index < items.size(); index++) {
            ContentItem source = items.get(index);
            ServicePageItemEntity item = new ServicePageItemEntity();
            item.setServicePageId(PAGE_ID);
            item.setItemType(type);
            item.setTitle(source.title().trim());
            item.setDescription(trimToNull(source.description()));
            item.setSortOrder(index);
            itemMapper.insert(item);
        }
    }

    private void replaceImages(List<Long> imageIds, long actorId) {
        relationMapper.delete(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, BUSINESS_TYPE)
                .eq(FileRelationRecord::getBusinessId, PAGE_ID)
                .eq(FileRelationRecord::getUsageType, GALLERY_USAGE));
        for (int index = 0; index < imageIds.size(); index++) {
            FileRelationRecord relation = new FileRelationRecord();
            relation.setBusinessType(BUSINESS_TYPE);
            relation.setBusinessId(PAGE_ID);
            relation.setUsageType(GALLERY_USAGE);
            relation.setFileId(imageIds.get(index));
            relation.setSortOrder(index);
            relation.setCreatedBy(actorId);
            relationMapper.insert(relation);
        }
    }

    private ServicePageConfigEntity requireConfig() {
        ServicePageConfigEntity config = configMapper.selectById(PAGE_ID);
        if (config == null) throw new BusinessException(404, "SERVICE_PAGE_NOT_FOUND", "服务页配置不存在");
        return config;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ServicePageCommand(
            String companyName, String companySubtitle, String slogan, boolean brandVisible,
            List<ContentItem> heroStats, boolean servicesVisible, List<ContentItem> services,
            String profileText, boolean profileVisible, List<String> profileTags,
            boolean galleryVisible, List<Long> galleryImageFileIds,
            boolean advantagesVisible, List<ContentItem> advantages,
            boolean contactVisible, String phonePrimary, String phoneSecondary, String address,
            BigDecimal longitude, BigDecimal latitude, String businessHours) { }

    public record ContentItem(String title, String description) { }
    public record FileView(long id, String url) { }
    public record ServicePageView(
            long id, String companyName, String companySubtitle, String slogan, boolean brandVisible,
            List<ContentItem> heroStats, boolean servicesVisible, List<ContentItem> services,
            String profileText, boolean profileVisible, List<String> profileTags,
            boolean galleryVisible, List<FileView> galleryImages,
            boolean advantagesVisible, List<ContentItem> advantages,
            boolean contactVisible, String phonePrimary, String phoneSecondary, String address,
            BigDecimal longitude, BigDecimal latitude, String businessHours, LocalDateTime updatedAt) { }
}
