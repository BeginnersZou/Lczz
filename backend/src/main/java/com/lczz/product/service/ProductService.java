package com.lczz.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.product.persistence.BusinessFileRelationEntity;
import com.lczz.product.persistence.BusinessFileRelationMapper;
import com.lczz.product.persistence.FileAssetEntity;
import com.lczz.product.persistence.FileAssetMapper;
import com.lczz.product.persistence.ProductCategoryEntity;
import com.lczz.product.persistence.ProductCategoryMapper;
import com.lczz.product.persistence.ProductEntity;
import com.lczz.product.persistence.ProductMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private static final String BUSINESS_TYPE = "PRODUCT";
    private static final String DETAIL_USAGE = "DETAIL";

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final FileAssetMapper fileMapper;
    private final BusinessFileRelationMapper relationMapper;

    public ProductService(ProductMapper productMapper, ProductCategoryMapper categoryMapper,
                          FileAssetMapper fileMapper, BusinessFileRelationMapper relationMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
    }

    public ProductPage list(AuthenticatedUser actor, int page, int pageSize, String keyword,
                            String category, Boolean enabled) {
        boolean admin = isAdmin(actor);
        LambdaQueryWrapper<ProductEntity> query = new LambdaQueryWrapper<>();
        if (!admin) {
            query.eq(ProductEntity::getEnabled, true);
        } else if (enabled != null) {
            query.eq(ProductEntity::getEnabled, enabled);
        }
        query.and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(ProductEntity::getProductName, keyword.trim())
                        .or().like(ProductEntity::getModelSpec, keyword.trim())
                        .or().like(ProductEntity::getProductCode, keyword.trim()))
                .orderByAsc(ProductEntity::getSortOrder)
                .orderByDesc(ProductEntity::getUpdatedAt)
                .orderByDesc(ProductEntity::getId);
        Set<Long> categoryIds = resolveCategoryIds(category, admin);
        if (category != null && !category.isBlank()) {
            if (categoryIds.isEmpty()) return new ProductPage(List.of(), 0, page, pageSize);
            query.in(ProductEntity::getCategoryId, categoryIds);
        }
        Page<ProductEntity> result = productMapper.selectPage(new Page<>(page, pageSize), query);
        return new ProductPage(toViews(result.getRecords(), false), result.getTotal(), page, pageSize);
    }

    public ProductView detail(AuthenticatedUser actor, long id) {
        ProductEntity product = requireProduct(id);
        if (!isAdmin(actor) && !Boolean.TRUE.equals(product.getEnabled())) {
            throw notFound("PRODUCT_NOT_FOUND", "产品不存在");
        }
        return toViews(List.of(product), true).getFirst();
    }

    public List<CategoryView> categories(AuthenticatedUser actor) {
        LambdaQueryWrapper<ProductCategoryEntity> query = new LambdaQueryWrapper<ProductCategoryEntity>()
                .eq(!isAdmin(actor), ProductCategoryEntity::getEnabled, true)
                .orderByAsc(ProductCategoryEntity::getCategoryLevel)
                .orderByAsc(ProductCategoryEntity::getSortOrder)
                .orderByAsc(ProductCategoryEntity::getId);
        return categoryMapper.selectList(query).stream().map(CategoryView::from).toList();
    }

    @Transactional
    public CategoryView createCategory(AuthenticatedUser actor, CategoryCommand command) {
        ensureCategoryCodeAvailable(command.code(), null);
        ProductCategoryEntity parent = validateParent(command.parentId());
        ProductCategoryEntity category = new ProductCategoryEntity();
        applyCategory(category, command, parent, actor.userId());
        category.setCreatedBy(actor.userId());
        category.setDeleted(false);
        categoryMapper.insert(category);
        return CategoryView.from(category);
    }

    @Transactional
    public CategoryView updateCategory(AuthenticatedUser actor, long id, CategoryCommand command) {
        ProductCategoryEntity category = requireCategory(id);
        ensureCategoryCodeAvailable(command.code(), id);
        if (Objects.equals(command.parentId(), id)) {
            throw new BusinessException("CATEGORY_PARENT_INVALID", "分类不能以自身作为上级分类");
        }
        ProductCategoryEntity parent = validateParent(command.parentId());
        if (parent != null && Objects.equals(parent.getParentId(), id)) {
            throw new BusinessException("CATEGORY_PARENT_INVALID", "分类层级不能形成循环");
        }
        applyCategory(category, command, parent, actor.userId());
        categoryMapper.updateById(category);
        return CategoryView.from(category);
    }

    @Transactional
    public void deleteCategory(long id) {
        requireCategory(id);
        long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategoryEntity>()
                .eq(ProductCategoryEntity::getParentId, id));
        long productCount = productMapper.selectCount(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getCategoryId, id));
        if (childCount > 0 || productCount > 0) {
            throw new BusinessException(409, "CATEGORY_IN_USE", "分类仍包含子分类或产品，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    @Transactional
    public ProductView create(AuthenticatedUser actor, ProductCommand command) {
        ProductCategoryEntity category = requireCategory(command.categoryId());
        validateProductCode(command.productCode(), null);
        validateFiles(command.coverFileId(), command.detailFileIds());
        ProductEntity product = new ProductEntity();
        product.setProductCode(normalizeProductCode(command.productCode()));
        applyProduct(product, command, category, actor.userId());
        product.setCreatedBy(actor.userId());
        product.setVersion(0);
        product.setDeleted(false);
        productMapper.insert(product);
        replaceDetailFiles(product.getId(), command.detailFileIds(), actor.userId());
        return detail(actor, product.getId());
    }

    @Transactional
    public ProductView update(AuthenticatedUser actor, long id, ProductCommand command) {
        ProductEntity product = requireProduct(id);
        ProductCategoryEntity category = requireCategory(command.categoryId());
        validateProductCode(command.productCode(), id);
        validateFiles(command.coverFileId(), command.detailFileIds());
        if (command.productCode() != null && !command.productCode().isBlank()) {
            product.setProductCode(command.productCode().trim().toUpperCase(Locale.ROOT));
        }
        applyProduct(product, command, category, actor.userId());
        productMapper.updateById(product);
        replaceDetailFiles(product.getId(), command.detailFileIds(), actor.userId());
        return detail(actor, product.getId());
    }

    @Transactional
    public ProductView setEnabled(AuthenticatedUser actor, long id, boolean enabled) {
        ProductEntity product = requireProduct(id);
        product.setEnabled(enabled);
        product.setUpdatedBy(actor.userId());
        productMapper.updateById(product);
        return detail(actor, id);
    }

    @Transactional
    public void delete(long id) {
        requireProduct(id);
        productMapper.deleteById(id);
    }

    private List<ProductView> toViews(List<ProductEntity> products, boolean includeDetails) {
        if (products.isEmpty()) return List.of();
        Map<Long, ProductCategoryEntity> categories = categoryMapper.selectBatchIds(products.stream()
                        .map(ProductEntity::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ProductCategoryEntity::getId, Function.identity()));
        Set<Long> parentIds = categories.values().stream().map(ProductCategoryEntity::getParentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!parentIds.isEmpty()) {
            categoryMapper.selectBatchIds(parentIds).forEach(value -> categories.put(value.getId(), value));
        }
        Set<Long> coverIds = products.stream().map(ProductEntity::getCoverFileId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FileAssetEntity> files = loadFiles(coverIds);
        Map<Long, List<FileView>> detailFiles = includeDetails
                ? loadDetailFiles(products.stream().map(ProductEntity::getId).toList()) : Map.of();
        return products.stream().map(product -> {
            ProductCategoryEntity child = categories.get(product.getCategoryId());
            ProductCategoryEntity parent = child == null ? null : categories.get(child.getParentId());
            List<String> categoryPath = new ArrayList<>();
            if (parent != null) categoryPath.add(parent.getCategoryName());
            if (child != null) categoryPath.add(child.getCategoryName());
            FileAssetEntity cover = product.getCoverFileId() == null ? null : files.get(product.getCoverFileId());
            return new ProductView(product.getId(), product.getProductCode(), product.getProductName(),
                    product.getCategoryId(), child == null ? null : child.getCategoryCode(), categoryPath,
                    product.getModelSpec(), product.getUnit(), product.getDisplayStock(), product.getDisplayPrice(),
                    cover == null ? null : cover.getAccessUrl(), product.getDescription(),
                    detailFiles.getOrDefault(product.getId(), List.of()), Boolean.TRUE.equals(product.getEnabled()),
                    product.getSortOrder(), product.getCreatedAt(), product.getUpdatedAt());
        }).toList();
    }

    private Map<Long, List<FileView>> loadDetailFiles(List<Long> productIds) {
        List<BusinessFileRelationEntity> relations = relationMapper.selectList(
                new LambdaQueryWrapper<BusinessFileRelationEntity>()
                        .eq(BusinessFileRelationEntity::getBusinessType, BUSINESS_TYPE)
                        .eq(BusinessFileRelationEntity::getUsageType, DETAIL_USAGE)
                        .in(BusinessFileRelationEntity::getBusinessId, productIds)
                        .orderByAsc(BusinessFileRelationEntity::getSortOrder));
        Map<Long, FileAssetEntity> files = loadFiles(relations.stream()
                .map(BusinessFileRelationEntity::getFileId).collect(Collectors.toSet()));
        Map<Long, List<FileView>> result = new HashMap<>();
        for (BusinessFileRelationEntity relation : relations) {
            FileAssetEntity file = files.get(relation.getFileId());
            if (file != null) result.computeIfAbsent(relation.getBusinessId(), ignored -> new ArrayList<>())
                    .add(new FileView(file.getId(), file.getAccessUrl()));
        }
        return result;
    }

    private Map<Long, FileAssetEntity> loadFiles(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fileMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FileAssetEntity::getId, Function.identity()));
    }

    private Set<Long> resolveCategoryIds(String value, boolean admin) {
        if (value == null || value.isBlank()) return Set.of();
        String query = value.trim();
        List<ProductCategoryEntity> matches = categoryMapper.selectList(
                new LambdaQueryWrapper<ProductCategoryEntity>()
                        .eq(!admin, ProductCategoryEntity::getEnabled, true)
                        .and(wrapper -> wrapper.eq(ProductCategoryEntity::getCategoryCode, query)
                                .or().eq(ProductCategoryEntity::getCategoryName, query)));
        LinkedHashSet<Long> ids = matches.stream().map(ProductCategoryEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> parentIds = matches.stream().filter(item -> item.getCategoryLevel() == 1)
                .map(ProductCategoryEntity::getId).collect(Collectors.toSet());
        if (!parentIds.isEmpty()) {
            categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryEntity>()
                            .in(ProductCategoryEntity::getParentId, parentIds)
                            .eq(!admin, ProductCategoryEntity::getEnabled, true))
                    .forEach(item -> ids.add(item.getId()));
        }
        return ids;
    }

    private void applyProduct(ProductEntity product, ProductCommand command, ProductCategoryEntity category,
                              long actorId) {
        if (category.getCategoryLevel() != 2) {
            throw new BusinessException("PRODUCT_CATEGORY_INVALID", "产品必须选择二级分类");
        }
        product.setProductName(command.name().trim());
        product.setCategoryId(category.getId());
        product.setModelSpec(trimToNull(command.spec()));
        product.setUnit(command.unit().trim());
        product.setDisplayStock(command.stock());
        product.setDisplayPrice(command.price());
        product.setDescription(trimToNull(command.remark()));
        product.setCoverFileId(command.coverFileId());
        product.setEnabled(command.enabled() == null || command.enabled());
        product.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        product.setUpdatedBy(actorId);
    }

    private void applyCategory(ProductCategoryEntity category, CategoryCommand command,
                               ProductCategoryEntity parent, long actorId) {
        category.setCategoryCode(command.code().trim().toLowerCase(Locale.ROOT));
        category.setCategoryName(command.name().trim());
        category.setParentId(parent == null ? null : parent.getId());
        category.setCategoryLevel(parent == null ? 1 : 2);
        category.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        category.setEnabled(command.enabled() == null || command.enabled());
        category.setUpdatedBy(actorId);
    }

    private void replaceDetailFiles(long productId, List<Long> rawFileIds, long actorId) {
        relationMapper.delete(new LambdaQueryWrapper<BusinessFileRelationEntity>()
                .eq(BusinessFileRelationEntity::getBusinessType, BUSINESS_TYPE)
                .eq(BusinessFileRelationEntity::getBusinessId, productId)
                .eq(BusinessFileRelationEntity::getUsageType, DETAIL_USAGE));
        List<Long> fileIds = distinctFileIds(rawFileIds);
        for (int index = 0; index < fileIds.size(); index++) {
            BusinessFileRelationEntity relation = new BusinessFileRelationEntity();
            relation.setBusinessType(BUSINESS_TYPE);
            relation.setBusinessId(productId);
            relation.setUsageType(DETAIL_USAGE);
            relation.setFileId(fileIds.get(index));
            relation.setSortOrder(index);
            relation.setCreatedBy(actorId);
            relationMapper.insert(relation);
        }
    }

    private void validateFiles(Long coverFileId, List<Long> detailFileIds) {
        List<Long> details = distinctFileIds(detailFileIds);
        if (details.size() > 9) throw new BusinessException("PRODUCT_IMAGES_LIMIT", "产品详情图最多 9 张");
        LinkedHashSet<Long> all = new LinkedHashSet<>(details);
        if (coverFileId != null) all.add(coverFileId);
        if (!all.isEmpty() && fileMapper.selectBatchIds(all).size() != all.size()) {
            throw new BusinessException("FILE_NOT_FOUND", "存在无效或已删除的图片文件");
        }
    }

    private List<Long> distinctFileIds(List<Long> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private ProductCategoryEntity validateParent(Long parentId) {
        if (parentId == null) return null;
        ProductCategoryEntity parent = requireCategory(parentId);
        if (parent.getCategoryLevel() != 1 || parent.getParentId() != null) {
            throw new BusinessException("CATEGORY_PARENT_INVALID", "只允许在一级分类下创建二级分类");
        }
        return parent;
    }

    private void ensureCategoryCodeAvailable(String rawCode, Long excludedId) {
        String code = rawCode.trim().toLowerCase(Locale.ROOT);
        ProductCategoryEntity existing = categoryMapper.selectOne(new LambdaQueryWrapper<ProductCategoryEntity>()
                .eq(ProductCategoryEntity::getCategoryCode, code));
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BusinessException(409, "CATEGORY_CODE_EXISTS", "分类编码已存在");
        }
    }

    private void validateProductCode(String rawCode, Long excludedId) {
        if (rawCode == null || rawCode.isBlank()) return;
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        ProductEntity existing = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getProductCode, code));
        if (existing != null && !Objects.equals(existing.getId(), excludedId)) {
            throw new BusinessException(409, "PRODUCT_CODE_EXISTS", "产品编码已存在");
        }
    }

    private String normalizeProductCode(String rawCode) {
        if (rawCode != null && !rawCode.isBlank()) return rawCode.trim().toUpperCase(Locale.ROOT);
        return "PRD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private ProductEntity requireProduct(long id) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null) throw notFound("PRODUCT_NOT_FOUND", "产品不存在");
        return product;
    }

    private ProductCategoryEntity requireCategory(long id) {
        ProductCategoryEntity category = categoryMapper.selectById(id);
        if (category == null) throw notFound("CATEGORY_NOT_FOUND", "产品分类不存在");
        return category;
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(404, code, message);
    }

    private boolean isAdmin(AuthenticatedUser actor) {
        return actor != null && actor.hasRole(RoleCode.ADMIN);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProductCommand(String productCode, String name, Long categoryId, String spec, String unit,
                                 BigDecimal stock, BigDecimal price, String remark, Long coverFileId,
                                 List<Long> detailFileIds, Boolean enabled, Integer sortOrder) { }

    public record CategoryCommand(String code, String name, Long parentId, Integer sortOrder, Boolean enabled) { }

    public record ProductPage(List<ProductView> list, long total, long page, long pageSize) { }

    public record ProductView(Long id, String code, String name, Long categoryId, String type,
                              List<String> category, String spec, String unit, BigDecimal stock,
                              BigDecimal price, String image, String remark, List<FileView> detailImages,
                              boolean enabled, Integer sortOrder, LocalDateTime createdAt,
                              LocalDateTime updatedAt) { }

    public record FileView(Long id, String url) { }

    public record CategoryView(Long id, String code, String name, Long parentId, Integer level,
                               Integer sortOrder, boolean enabled) {
        static CategoryView from(ProductCategoryEntity category) {
            return new CategoryView(category.getId(), category.getCategoryCode(), category.getCategoryName(),
                    category.getParentId(), category.getCategoryLevel(), category.getSortOrder(),
                    Boolean.TRUE.equals(category.getEnabled()));
        }
    }
}
