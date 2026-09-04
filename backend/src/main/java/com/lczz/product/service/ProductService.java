package com.lczz.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.audit.OperationAuditService;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.service.FileService;
import com.lczz.product.persistence.BusinessFileRelationEntity;
import com.lczz.product.persistence.BusinessFileRelationMapper;
import com.lczz.product.persistence.FileAssetEntity;
import com.lczz.product.persistence.FileAssetMapper;
import com.lczz.product.persistence.ProductCategoryEntity;
import com.lczz.product.persistence.ProductCategoryMapper;
import com.lczz.product.persistence.ProductEntity;
import com.lczz.product.persistence.ProductMapper;
import com.lczz.product.service.ProductSkuService.DimensionCommand;
import com.lczz.product.service.ProductSkuService.ProductSpecsView;
import com.lczz.product.service.ProductSkuService.SkuCommand;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private static final String CAROUSEL_USAGE = "CAROUSEL";
    private static final BigDecimal LOW_STOCK_THRESHOLD = BigDecimal.valueOf(5);

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final FileAssetMapper fileMapper;
    private final BusinessFileRelationMapper relationMapper;
    private final FileService fileService;
    private final OperationAuditService auditService;
    private final ProductSkuService skuService;

    public ProductService(ProductMapper productMapper, ProductCategoryMapper categoryMapper,
                          FileAssetMapper fileMapper, BusinessFileRelationMapper relationMapper,
                          FileService fileService, OperationAuditService auditService,
                          ProductSkuService skuService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.fileService = fileService;
        this.auditService = auditService;
        this.skuService = skuService;
    }

    public ProductPage list(AuthenticatedUser actor, int page, int pageSize, String keyword,
                            String category, Boolean enabled, String stockStatus) {
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
        applyStockStatus(query, stockStatus);
        Page<ProductEntity> result = productMapper.selectPage(new Page<>(page, pageSize), query);
        return new ProductPage(toViews(actor, result.getRecords(), false), result.getTotal(), page, pageSize);
    }

    public ProductView detail(AuthenticatedUser actor, long id) {
        ProductEntity product = requireProduct(id);
        if (!isAdmin(actor) && !Boolean.TRUE.equals(product.getEnabled())) {
            throw notFound("PRODUCT_NOT_FOUND", "产品不存在");
        }
        return toViews(actor, List.of(product), true).getFirst();
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
        validateFiles(command.coverFileId(), command.imageFileIds(), command.detailFileIds());
        ProductEntity product = new ProductEntity();
        product.setProductCode(normalizeProductCode(command.productCode()));
        applyProduct(product, command, category, actor.userId());
        product.setCreatedBy(actor.userId());
        product.setVersion(0);
        product.setDeleted(false);
        productMapper.insert(product);
        skuService.replace(product.getId(), product.getProductCode(), product.getModelSpec(), product.getUnit(),
                product.getDisplayStock(), command.specDimensions(), command.skus());
        replaceFiles(product.getId(), CAROUSEL_USAGE, normalizedCarousel(command), actor.userId());
        replaceDetailFiles(product.getId(), command.detailFileIds(), actor.userId());
        return detail(actor, product.getId());
    }

    @Transactional
    public ProductView update(AuthenticatedUser actor, long id, ProductCommand command) {
        ProductEntity product = requireProduct(id);
        ProductCategoryEntity category = requireCategory(command.categoryId());
        validateProductCode(command.productCode(), id);
        validateFiles(command.coverFileId(), command.imageFileIds(), command.detailFileIds());
        if (command.productCode() != null && !command.productCode().isBlank()) {
            product.setProductCode(command.productCode().trim().toUpperCase(Locale.ROOT));
        }
        applyProduct(product, command, category, actor.userId());
        productMapper.updateById(product);
        skuService.replace(product.getId(), product.getProductCode(), product.getModelSpec(), product.getUnit(),
                product.getDisplayStock(), command.specDimensions(), command.skus());
        replaceFiles(product.getId(), CAROUSEL_USAGE, normalizedCarousel(command), actor.userId());
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
    public ProductView adjustStock(AuthenticatedUser actor, long id, StockAdjustmentCommand command,
                                   AuditContext context) {
        ProductEntity product = productMapper.selectForUpdate(id);
        if (product == null) throw notFound("PRODUCT_NOT_FOUND", "产品不存在");
        String type = normalizeAdjustmentType(command.type());
        skuService.ensureDefaultSku(product.getId(), product.getProductCode(), product.getModelSpec(),
                product.getUnit(), product.getDisplayStock(), Boolean.TRUE.equals(product.getEnabled()));
        ProductSkuService.StockAdjustmentResult adjustment = skuService.adjustStock(
                id, command.skuId(), type, command.quantity());
        ProductView result = detail(actor, id);
        auditService.recordSuccess(actor.userId(), "PRODUCT_STOCK_ADJUSTMENT", "PRODUCT", id,
                context.requestId(), context.clientIp(),
                new StockAuditSnapshot(adjustment.skuId(), adjustment.skuCode(), adjustment.specLabel(),
                        adjustment.before(), null, null, null),
                new StockAuditSnapshot(adjustment.skuId(), adjustment.skuCode(), adjustment.specLabel(),
                        adjustment.after(), type, command.quantity(), command.reason().trim()));
        return result;
    }

    @Transactional
    public void delete(long id) {
        requireProduct(id);
        productMapper.deleteById(id);
    }

    private List<ProductView> toViews(AuthenticatedUser actor, List<ProductEntity> products, boolean includeDetails) {
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
                ? loadFilesByUsage(actor, products.stream().map(ProductEntity::getId).toList(), DETAIL_USAGE) : Map.of();
        Map<Long, List<FileView>> carouselFiles = includeDetails
                ? loadFilesByUsage(actor, products.stream().map(ProductEntity::getId).toList(), CAROUSEL_USAGE) : Map.of();
        Map<Long, ProductSpecsView> specsByProduct = skuService.getBatch(
                products.stream().map(ProductEntity::getId).toList(), isAdmin(actor));
        return products.stream().map(product -> {
            ProductCategoryEntity child = categories.get(product.getCategoryId());
            ProductCategoryEntity parent = child == null ? null : categories.get(child.getParentId());
            List<String> categoryPath = new ArrayList<>();
            if (parent != null) categoryPath.add(parent.getCategoryName());
            if (child != null) categoryPath.add(child.getCategoryName());
            FileAssetEntity cover = product.getCoverFileId() == null ? null : files.get(product.getCoverFileId());
            ProductSpecsView specs = specsByProduct.getOrDefault(product.getId(),
                    new ProductSpecsView(List.of(), List.of()));
            BigDecimal skuStock = specs.skus().stream().filter(ProductSkuService.SkuView::enabled)
                    .map(ProductSkuService.SkuView::stock).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, BigDecimal> stockByUnit = specs.skus().stream()
                    .filter(ProductSkuService.SkuView::enabled)
                    .collect(Collectors.groupingBy(ProductSkuService.SkuView::unit, LinkedHashMap::new,
                            Collectors.reducing(BigDecimal.ZERO, ProductSkuService.SkuView::stock, BigDecimal::add)));
            boolean mixedUnits = stockByUnit.size() > 1;
            String stockSummary = stockByUnit.isEmpty()
                    ? (product.getDisplayStock() == null ? BigDecimal.ZERO : product.getDisplayStock())
                    .stripTrailingZeros().toPlainString() + " " + product.getUnit()
                    : stockByUnit.entrySet().stream()
                    .map(entry -> entry.getValue().stripTrailingZeros().toPlainString() + " " + entry.getKey())
                    .collect(Collectors.joining("；"));
            return new ProductView(product.getId(), product.getProductCode(), product.getProductName(),
                    product.getCategoryId(), child == null ? null : child.getCategoryCode(), categoryPath,
                    product.getModelSpec(), mixedUnits ? "多单位" : stockByUnit.keySet().stream().findFirst().orElse(product.getUnit()),
                    mixedUnits ? null : (specs.skus().isEmpty() ? product.getDisplayStock() : skuStock),
                    product.getDisplayPrice(),
                    cover == null ? null : fileService.issueAccess(actor, cover.getId()).url(), product.getDescription(),
                    detailFiles.getOrDefault(product.getId(), List.of()), Boolean.TRUE.equals(product.getEnabled()),
                    product.getSortOrder(), product.getCreatedAt(), product.getUpdatedAt(),
                    specs.dimensions(), specs.skus(), carouselFiles.getOrDefault(product.getId(),
                    cover == null ? List.of() : List.of(new FileView(cover.getId(), fileService.issueAccess(actor, cover.getId()).url()))),
                    specs.skus().size(), stockSummary);
        }).toList();
    }

    private Map<Long, List<FileView>> loadFilesByUsage(AuthenticatedUser actor, List<Long> productIds, String usage) {
        List<BusinessFileRelationEntity> relations = relationMapper.selectList(
                new LambdaQueryWrapper<BusinessFileRelationEntity>()
                        .eq(BusinessFileRelationEntity::getBusinessType, BUSINESS_TYPE)
                        .eq(BusinessFileRelationEntity::getUsageType, usage)
                        .in(BusinessFileRelationEntity::getBusinessId, productIds)
                        .orderByAsc(BusinessFileRelationEntity::getSortOrder));
        Map<Long, FileAssetEntity> files = loadFiles(relations.stream()
                .map(BusinessFileRelationEntity::getFileId).collect(Collectors.toSet()));
        Map<Long, List<FileView>> result = new HashMap<>();
        for (BusinessFileRelationEntity relation : relations) {
            FileAssetEntity file = files.get(relation.getFileId());
            if (file != null) result.computeIfAbsent(relation.getBusinessId(), ignored -> new ArrayList<>())
                    .add(new FileView(file.getId(), fileService.issueAccess(actor, file.getId()).url()));
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

    private void applyStockStatus(LambdaQueryWrapper<ProductEntity> query, String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank() || "all".equalsIgnoreCase(rawStatus.trim())) return;
        String maxStock = "COALESCE((SELECT MAX(ps.stock) FROM product_sku ps "
                + "WHERE ps.product_id=product.id AND ps.enabled=TRUE AND ps.deleted=FALSE), display_stock, 0)";
        String minStock = "COALESCE((SELECT MIN(ps.stock) FROM product_sku ps "
                + "WHERE ps.product_id=product.id AND ps.enabled=TRUE AND ps.deleted=FALSE), display_stock, 0)";
        switch (rawStatus.trim().toLowerCase(Locale.ROOT)) {
            case "empty" -> query.apply(maxStock + " = 0");
            case "low" -> query.apply(maxStock + " > 0 AND " + minStock + " <= {0}", LOW_STOCK_THRESHOLD);
            case "normal" -> query.apply(minStock + " > {0}", LOW_STOCK_THRESHOLD);
            default -> throw new BusinessException("INVALID_STOCK_STATUS", "库存状态只支持 normal、low 或 empty");
        }
    }

    private String normalizeAdjustmentType(String rawType) {
        String value = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("IN", "OUT").contains(value)) {
            throw new BusinessException("INVALID_STOCK_ADJUSTMENT_TYPE", "库存调整类型只支持 IN 或 OUT");
        }
        return value;
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
        replaceFiles(productId, DETAIL_USAGE, rawFileIds, actorId);
    }

    private void replaceFiles(long productId, String usage, List<Long> rawFileIds, long actorId) {
        relationMapper.delete(new LambdaQueryWrapper<BusinessFileRelationEntity>()
                .eq(BusinessFileRelationEntity::getBusinessType, BUSINESS_TYPE)
                .eq(BusinessFileRelationEntity::getBusinessId, productId)
                .eq(BusinessFileRelationEntity::getUsageType, usage));
        List<Long> fileIds = distinctFileIds(rawFileIds);
        for (int index = 0; index < fileIds.size(); index++) {
            BusinessFileRelationEntity relation = new BusinessFileRelationEntity();
            relation.setBusinessType(BUSINESS_TYPE);
            relation.setBusinessId(productId);
            relation.setUsageType(usage);
            relation.setFileId(fileIds.get(index));
            relation.setSortOrder(index);
            relation.setCreatedBy(actorId);
            relationMapper.insert(relation);
        }
    }

    private void validateFiles(Long coverFileId, List<Long> imageFileIds, List<Long> detailFileIds) {
        List<Long> images = distinctFileIds(imageFileIds);
        if (images.size() > 3) throw new BusinessException("PRODUCT_CAROUSEL_LIMIT", "耗材轮播图最多 3 张");
        List<Long> details = distinctFileIds(detailFileIds);
        if (details.size() > 9) throw new BusinessException("PRODUCT_IMAGES_LIMIT", "产品详情图最多 9 张");
        LinkedHashSet<Long> all = new LinkedHashSet<>(details);
        all.addAll(images);
        if (coverFileId != null) all.add(coverFileId);
        if (!all.isEmpty() && fileMapper.selectBatchIds(all).size() != all.size()) {
            throw new BusinessException("FILE_NOT_FOUND", "存在无效或已删除的图片文件");
        }
    }

    private List<Long> normalizedCarousel(ProductCommand command) {
        List<Long> images = distinctFileIds(command.imageFileIds());
        if (!images.isEmpty()) return images;
        return command.coverFileId() == null ? List.of() : List.of(command.coverFileId());
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
                                 List<Long> imageFileIds, List<Long> detailFileIds, Boolean enabled, Integer sortOrder,
                                 List<DimensionCommand> specDimensions, List<SkuCommand> skus) { }

    public record StockAdjustmentCommand(Long skuId, String type, BigDecimal quantity, String reason) { }

    public record AuditContext(String requestId, String clientIp) { }

    private record StockAuditSnapshot(Long skuId, String skuCode, String specLabel, BigDecimal stock,
                                      String type, BigDecimal quantity, String reason) { }

    public record CategoryCommand(String code, String name, Long parentId, Integer sortOrder, Boolean enabled) { }

    public record ProductPage(List<ProductView> list, long total, long page, long pageSize) { }

    public record ProductView(Long id, String code, String name, Long categoryId, String type,
                              List<String> category, String spec, String unit, BigDecimal stock,
                              BigDecimal price, String image, String remark, List<FileView> detailImages,
                              boolean enabled, Integer sortOrder, LocalDateTime createdAt,
                              LocalDateTime updatedAt, List<ProductSkuService.DimensionView> specDimensions,
                              List<ProductSkuService.SkuView> skus, List<FileView> images,
                              int skuCount, String stockSummary) { }

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
