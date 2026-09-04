package com.lczz.product.service;

import com.lczz.common.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class ProductSkuService {
    private final JdbcTemplate jdbc;

    public ProductSkuService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProductSpecsView get(long productId, boolean admin) {
        List<DimensionView> dimensions = jdbc.query("""
                SELECT id, dimension_name, sort_order FROM product_spec_dimension
                WHERE product_id=? AND deleted=FALSE ORDER BY sort_order,id
                """, (rs, row) -> new DimensionView(rs.getLong("id"), rs.getString("dimension_name"),
                rs.getInt("sort_order"), new ArrayList<>()), productId);
        Map<Long, DimensionView> byId = new LinkedHashMap<>();
        dimensions.forEach(value -> byId.put(value.id(), value));
        if (!byId.isEmpty()) {
            jdbc.query("""
                    SELECT id, dimension_id, spec_value, sort_order FROM product_spec_value
                    WHERE dimension_id IN (SELECT id FROM product_spec_dimension WHERE product_id=? AND deleted=FALSE)
                      AND deleted=FALSE ORDER BY sort_order,id
                    """, rs -> {
                DimensionView dimension = byId.get(rs.getLong("dimension_id"));
                if (dimension != null) dimension.values().add(new ValueView(rs.getLong("id"),
                        rs.getString("spec_value"), rs.getInt("sort_order")));
            }, productId);
        }
        String enabledClause = admin ? "" : " AND enabled=TRUE";
        List<SkuView> skus = jdbc.query("""
                SELECT id, sku_code, spec_signature, spec_label, unit, stock, enabled, default_sku, sort_order
                FROM product_sku WHERE product_id=? AND deleted=FALSE
                """ + enabledClause + " ORDER BY sort_order,id", (rs, row) -> new SkuView(
                rs.getLong("id"), rs.getString("sku_code"), parseSignature(rs.getString("spec_signature")),
                rs.getString("spec_label"), rs.getString("unit"), rs.getBigDecimal("stock"),
                rs.getBoolean("enabled"), rs.getBoolean("default_sku"), rs.getInt("sort_order")), productId);
        return new ProductSpecsView(dimensions, skus);
    }

    public Map<Long, ProductSpecsView> getBatch(Collection<Long> rawIds, boolean admin) {
        List<Long> ids = rawIds == null ? List.of() : rawIds.stream().distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = ids.toArray();
        Map<Long, List<DimensionView>> dimensions = new LinkedHashMap<>();
        Map<Long, List<SkuView>> skus = new LinkedHashMap<>();
        Map<Long, DimensionView> dimensionsById = new HashMap<>();
        ids.forEach(id -> {
            dimensions.put(id, new ArrayList<>());
            skus.put(id, new ArrayList<>());
        });
        jdbc.query("SELECT id,product_id,dimension_name,sort_order FROM product_spec_dimension "
                + "WHERE deleted=FALSE AND product_id IN (" + placeholders + ") ORDER BY product_id,sort_order,id", rs -> {
            DimensionView view = new DimensionView(rs.getLong("id"), rs.getString("dimension_name"),
                    rs.getInt("sort_order"), new ArrayList<>());
            dimensions.get(rs.getLong("product_id")).add(view);
            dimensionsById.put(view.id(), view);
        }, args);
        jdbc.query("SELECT id,dimension_id,spec_value,sort_order FROM product_spec_value "
                + "WHERE deleted=FALSE AND dimension_id IN (SELECT id FROM product_spec_dimension "
                + "WHERE deleted=FALSE AND product_id IN (" + placeholders + ")) ORDER BY dimension_id,sort_order,id", rs -> {
            DimensionView dimension = dimensionsById.get(rs.getLong("dimension_id"));
            if (dimension != null) dimension.values().add(new ValueView(rs.getLong("id"),
                    rs.getString("spec_value"), rs.getInt("sort_order")));
        }, args);
        String enabled = admin ? "" : " AND enabled=TRUE";
        jdbc.query("SELECT id,product_id,sku_code,spec_signature,spec_label,unit,stock,enabled,default_sku,sort_order "
                + "FROM product_sku WHERE deleted=FALSE" + enabled + " AND product_id IN (" + placeholders
                + ") ORDER BY product_id,sort_order,id", rs -> {
            skus.get(rs.getLong("product_id")).add(new SkuView(
                    rs.getLong("id"), rs.getString("sku_code"), parseSignature(rs.getString("spec_signature")),
                    rs.getString("spec_label"), rs.getString("unit"), rs.getBigDecimal("stock"),
                    rs.getBoolean("enabled"), rs.getBoolean("default_sku"), rs.getInt("sort_order")));
        }, args);
        Map<Long, ProductSpecsView> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new ProductSpecsView(dimensions.get(id), skus.get(id))));
        return result;
    }

    public long ensureDefaultSku(long productId, String productCode, String legacySpec,
                                 String unit, BigDecimal stock, boolean enabled) {
        List<Long> ids = jdbc.query("SELECT id FROM product_sku WHERE product_id=? AND deleted=FALSE ORDER BY id",
                (rs, row) -> rs.getLong(1), productId);
        if (!ids.isEmpty()) return ids.getFirst();
        insertSku(productId, normalizeCode(null, productCode + "-DEFAULT"), "",
                legacySpec == null ? "" : legacySpec, requireText(unit, "SKU单位不能为空", 32),
                nonNegative(stock), enabled, true, 0, Map.of(), Map.of());
        return jdbc.queryForObject("SELECT id FROM product_sku WHERE product_id=? AND deleted=FALSE", Long.class, productId);
    }

    public StockAdjustmentResult adjustStock(long productId, Long requestedSkuId, String type, BigDecimal quantity) {
        List<SkuStockRow> rows = jdbc.query("""
                SELECT id,sku_code,spec_label,unit,stock FROM product_sku
                WHERE product_id=? AND deleted=FALSE ORDER BY sort_order,id FOR UPDATE
                """, (rs, row) -> new SkuStockRow(rs.getLong("id"), rs.getString("sku_code"),
                rs.getString("spec_label"), rs.getString("unit"), rs.getBigDecimal("stock")), productId);
        if (rows.isEmpty()) throw new BusinessException(404, "SKU_NOT_FOUND", "产品没有可调整的SKU");
        SkuStockRow target;
        if (requestedSkuId == null) {
            if (rows.size() != 1) throw new BusinessException("SKU_REQUIRED_FOR_STOCK_ADJUSTMENT", "多规格耗材必须选择具体SKU后调整库存");
            target = rows.getFirst();
        } else {
            target = rows.stream().filter(row -> row.id() == requestedSkuId).findFirst()
                    .orElseThrow(() -> new BusinessException(404, "SKU_NOT_FOUND", "所选SKU不属于该耗材或已删除"));
        }
        BigDecimal before = target.stock() == null ? BigDecimal.ZERO : target.stock();
        BigDecimal after = "IN".equals(type) ? before.add(quantity) : before.subtract(quantity);
        if (after.signum() < 0) throw new BusinessException(409, "INSUFFICIENT_SKU_STOCK", "所选SKU库存不足，不能完成本次出库");
        jdbc.update("UPDATE product_sku SET stock=?,version=version+1 WHERE id=?", after, target.id());
        refreshAggregateStock(productId);
        return new StockAdjustmentResult(target.id(), target.code(), target.specLabel(), target.unit(), before, after);
    }

    public void replace(long productId, String productCode, String legacySpec, String legacyUnit,
                        BigDecimal legacyStock, List<DimensionCommand> rawDimensions,
                        List<SkuCommand> rawSkus) {
        List<DimensionCommand> dimensions = rawDimensions == null ? List.of() : rawDimensions;
        List<SkuCommand> skus = rawSkus == null ? List.of() : rawSkus;
        validate(dimensions, skus);
        Map<Long, ExistingSku> existingSkus = jdbc.query("""
                SELECT id,spec_signature,default_sku FROM product_sku
                WHERE product_id=? AND deleted=FALSE
                """, rs -> {
            Map<Long, ExistingSku> rows = new LinkedHashMap<>();
            while (rs.next()) rows.put(rs.getLong("id"), new ExistingSku(rs.getLong("id"),
                    rs.getString("spec_signature"), rs.getBoolean("default_sku")));
            return rows;
        }, productId);
        Set<Long> retainedSkuIds = new HashSet<>();
        existingSkus.keySet().forEach(id -> jdbc.update(
                "UPDATE product_sku SET sku_code=?,spec_signature_hash=? WHERE id=?",
                "__EDIT__" + id, sha256("__EDIT__" + id), id));

        jdbc.update("DELETE FROM product_sku_spec_value WHERE sku_id IN (SELECT id FROM product_sku WHERE product_id=?)", productId);
        jdbc.update("DELETE FROM product_spec_value WHERE dimension_id IN "
                + "(SELECT id FROM product_spec_dimension WHERE product_id=?)", productId);
        jdbc.update("DELETE FROM product_spec_dimension WHERE product_id=?", productId);

        if (dimensions.isEmpty()) {
            String code = normalizeCode(skus.isEmpty() ? null : skus.getFirst().code(), productCode + "-DEFAULT");
            String unit = requireText(skus.isEmpty() ? legacyUnit : skus.getFirst().unit(), "SKU单位不能为空", 32);
            BigDecimal stock = nonNegative(skus.isEmpty() ? legacyStock : skus.getFirst().stock());
            boolean enabled = skus.isEmpty() || skus.getFirst().enabled() == null || skus.getFirst().enabled();
            Long reusableId = existingSkus.values().stream().filter(ExistingSku::defaultSku)
                    .map(ExistingSku::id).findFirst().orElse(null);
            retainedSkuIds.add(upsertSku(reusableId, productId, code, "",
                    legacySpec == null ? "" : legacySpec.trim(), unit, stock,
                    enabled, true, 0, Map.of(), Map.of()));
            deleteStaleSkus(existingSkus.keySet(), retainedSkuIds);
            refreshAggregateStock(productId);
            return;
        }

        Map<String, Long> dimensionIds = new LinkedHashMap<>();
        Map<String, Map<String, Long>> valueIds = new LinkedHashMap<>();
        for (int index = 0; index < dimensions.size(); index++) {
            DimensionCommand command = dimensions.get(index);
            String name = command.name().trim();
            long dimensionId = insertAndGet("INSERT INTO product_spec_dimension(product_id,dimension_name,sort_order,deleted) VALUES (?,?,?,FALSE)",
                    productId, name, command.sortOrder() == null ? index : command.sortOrder());
            dimensionIds.put(name, dimensionId);
            Map<String, Long> ids = new LinkedHashMap<>();
            for (int valueIndex = 0; valueIndex < command.values().size(); valueIndex++) {
                ValueCommand value = command.values().get(valueIndex);
                String text = value.value().trim();
                long valueId = insertAndGet("INSERT INTO product_spec_value(dimension_id,spec_value,sort_order,deleted) VALUES (?,?,?,FALSE)",
                        dimensionId, text, value.sortOrder() == null ? valueIndex : value.sortOrder());
                ids.put(text, valueId);
            }
            valueIds.put(name, ids);
        }
        for (int index = 0; index < skus.size(); index++) {
            SkuCommand sku = skus.get(index);
            LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
            dimensions.forEach(d -> ordered.put(d.name().trim(), sku.specValues().get(d.name().trim()).trim()));
            String signature = signature(ordered);
            String label = ordered.entrySet().stream().map(e -> e.getKey() + "：" + e.getValue()).reduce((a, b) -> a + " / " + b).orElse("");
            if (signature.length() > 2000 || label.length() > 2000) {
                throw new BusinessException("SPEC_TEXT_TOO_LONG", "规格组合文本过长，请缩短规格名称或规格值");
            }
            ExistingSku existing = sku.id() == null ? null : existingSkus.get(sku.id());
            Long reusableId = existing != null && signature.equals(existing.signature()) ? existing.id() : null;
            retainedSkuIds.add(upsertSku(reusableId, productId,
                    normalizeCode(sku.code(), productCode + "-" + (index + 1)), signature, label,
                    requireText(sku.unit(), "SKU单位不能为空", 32), nonNegative(sku.stock()),
                    sku.enabled() == null || sku.enabled(), false,
                    sku.sortOrder() == null ? index : sku.sortOrder(), dimensionIds, valueIds));
        }
        deleteStaleSkus(existingSkus.keySet(), retainedSkuIds);
        refreshAggregateStock(productId);
    }

    private void validate(List<DimensionCommand> dimensions, List<SkuCommand> skus) {
        if (dimensions.isEmpty()) {
            if (skus.size() > 1) throw new BusinessException("INVALID_SKU_CONFIG", "无规格产品只能配置一个默认SKU");
            return;
        }
        if (skus.isEmpty()) throw new BusinessException("SKU_REQUIRED", "配置规格后必须生成SKU组合");
        if (dimensions.size() > 8) throw new BusinessException("SPEC_DIMENSION_LIMIT", "规格维度最多8个");
        Set<String> dimensionNames = new HashSet<>();
        long expected = 1;
        for (DimensionCommand dimension : dimensions) {
            String name = requireText(dimension.name(), "规格维度名称不能为空", 64);
            if (!dimensionNames.add(name)) throw new BusinessException("DUPLICATE_SPEC_DIMENSION", "规格维度名称不能重复");
            if (dimension.values() == null || dimension.values().isEmpty()) {
                throw new BusinessException("SPEC_VALUE_REQUIRED", "每个规格维度至少需要一个规格值");
            }
            Set<String> values = new HashSet<>();
            for (ValueCommand value : dimension.values()) {
                String text = requireText(value.value(), "规格值不能为空", 128);
                if (!values.add(text)) throw new BusinessException("DUPLICATE_SPEC_VALUE", "同一维度的规格值不能重复");
            }
            expected *= values.size();
            if (expected > 500) throw new BusinessException("SKU_COMBINATION_LIMIT", "SKU组合最多500个");
        }
        if (skus.size() != expected) throw new BusinessException("INCOMPLETE_SKU_COMBINATIONS", "必须完整提交所有规格组合");
        Set<String> signatures = new HashSet<>();
        Set<String> codes = new HashSet<>();
        boolean anyEnabled = false;
        for (SkuCommand sku : skus) {
            if (sku.specValues() == null || !sku.specValues().keySet().equals(dimensionNames)) {
                throw new BusinessException("INVALID_SKU_SELECTION", "每个SKU必须且只能选择所有规格维度");
            }
            LinkedHashMap<String, String> selected = new LinkedHashMap<>();
            for (DimensionCommand dimension : dimensions) {
                String name = dimension.name().trim();
                String value = requireText(sku.specValues().get(name), "SKU规格值不能为空", 128);
                boolean exists = dimension.values().stream().anyMatch(item -> item.value().trim().equals(value));
                if (!exists) throw new BusinessException("INVALID_SKU_SELECTION", "SKU包含未配置的规格值");
                selected.put(name, value);
            }
            if (!signatures.add(signature(selected))) throw new BusinessException("DUPLICATE_SKU_COMBINATION", "SKU规格组合不能重复");
            if (sku.code() != null && !sku.code().isBlank() && !codes.add(sku.code().trim().toUpperCase(Locale.ROOT))) {
                throw new BusinessException("DUPLICATE_SKU_CODE", "同一产品的SKU编码不能重复");
            }
            requireText(sku.unit(), "SKU单位不能为空", 32);
            nonNegative(sku.stock());
            anyEnabled |= sku.enabled() == null || sku.enabled();
        }
        if (!anyEnabled) throw new BusinessException("ENABLED_SKU_REQUIRED", "至少保留一个启用的SKU");
    }

    private long upsertSku(Long existingId, long productId, String code, String signature, String label, String unit,
                           BigDecimal stock, boolean enabled, boolean defaultSku, int sortOrder,
                           Map<String, Long> dimensionIds, Map<String, Map<String, Long>> valueIds) {
        if (existingId == null) {
            return insertSku(productId, code, signature, label, unit, stock, enabled, defaultSku, sortOrder,
                    dimensionIds, valueIds);
        }
        jdbc.update("""
                UPDATE product_sku SET sku_code=?,spec_signature=?,spec_signature_hash=?,spec_label=?,
                    unit=?,stock=?,enabled=?,default_sku=?,sort_order=?,version=version+1,deleted=FALSE
                WHERE id=? AND product_id=?
                """, code, signature, sha256(signature), label, unit, stock, enabled, defaultSku,
                sortOrder, existingId, productId);
        insertSkuValues(existingId, signature, dimensionIds, valueIds);
        return existingId;
    }

    private long insertSku(long productId, String code, String signature, String label, String unit,
                           BigDecimal stock, boolean enabled, boolean defaultSku, int sortOrder,
                           Map<String, Long> dimensionIds, Map<String, Map<String, Long>> valueIds) {
        long skuId = insertAndGet("""
                INSERT INTO product_sku(product_id,sku_code,spec_signature,spec_signature_hash,spec_label,unit,stock,enabled,default_sku,sort_order,version,deleted)
                VALUES (?,?,?,?,?,?,?,?,?,?,0,FALSE)
                """, productId, code, signature, sha256(signature), label, unit, stock, enabled, defaultSku, sortOrder);
        insertSkuValues(skuId, signature, dimensionIds, valueIds);
        return skuId;
    }

    private void insertSkuValues(long skuId, String signature, Map<String, Long> dimensionIds,
                                 Map<String, Map<String, Long>> valueIds) {
        parseSignature(signature).forEach((name, value) -> jdbc.update(
                "INSERT INTO product_sku_spec_value(sku_id,dimension_id,spec_value_id) VALUES (?,?,?)",
                skuId, dimensionIds.get(name), valueIds.get(name).get(value)));
    }

    private void deleteStaleSkus(Set<Long> existingIds, Set<Long> retainedIds) {
        existingIds.stream().filter(id -> !retainedIds.contains(id))
                .forEach(id -> jdbc.update("DELETE FROM product_sku WHERE id=?", id));
    }

    private void refreshAggregateStock(long productId) {
        BigDecimal aggregate = jdbc.queryForObject("""
                SELECT CASE WHEN COUNT(DISTINCT unit)=1 THEN COALESCE(SUM(stock),0) ELSE NULL END
                FROM product_sku
                WHERE product_id=? AND enabled=TRUE AND deleted=FALSE
                """, BigDecimal.class, productId);
        jdbc.update("UPDATE product SET display_stock=? WHERE id=?", aggregate, productId);
    }

    private long insertAndGet(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null) throw new IllegalStateException("数据库未返回主键");
        return key.longValue();
    }

    static String signature(Map<String, String> values) {
        return values.entrySet().stream().map(e -> escape(e.getKey()) + "=" + escape(e.getValue()))
                .reduce((a, b) -> a + "|" + b).orElse("");
    }

    static Map<String, String> parseSignature(String signature) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (signature == null || signature.isBlank()) return result;
        for (String part : signature.split("\\|")) {
            int separator = part.indexOf('=');
            if (separator > 0) result.put(unescape(part.substring(0, separator)), unescape(part.substring(separator + 1)));
        }
        return result;
    }

    private static String escape(String value) { return value.replace("%", "%25").replace("|", "%7C").replace("=", "%3D"); }
    private static String unescape(String value) { return value.replace("%3D", "=").replace("%7C", "|").replace("%25", "%"); }
    private static String normalizeCode(String value, String fallback) {
        String result = value == null || value.isBlank() ? fallback : value;
        result = result.trim().toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9_-]{1,96}")) throw new BusinessException("INVALID_SKU_CODE", "SKU编码仅支持字母、数字、下划线和横线");
        return result;
    }
    private static String requireText(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new BusinessException("VALIDATION_ERROR", message);
        String text = value.trim();
        if (text.length() > max) throw new BusinessException("VALIDATION_ERROR", message);
        return text;
    }
    private static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0) throw new BusinessException("INVALID_SKU_STOCK", "SKU库存不能小于0");
        return result;
    }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256不可用", impossible);
        }
    }

    public record DimensionCommand(String name, List<ValueCommand> values, Integer sortOrder) { }
    public record ValueCommand(String value, Integer sortOrder) { }
    public record SkuCommand(Long id, String code, Map<String, String> specValues, String unit,
                             BigDecimal stock, Boolean enabled, Integer sortOrder) {
        public SkuCommand(String code, Map<String, String> specValues, String unit,
                          BigDecimal stock, Boolean enabled, Integer sortOrder) {
            this(null, code, specValues, unit, stock, enabled, sortOrder);
        }
    }
    public record ProductSpecsView(List<DimensionView> dimensions, List<SkuView> skus) { }
    public record DimensionView(Long id, String name, Integer sortOrder, List<ValueView> values) { }
    public record ValueView(Long id, String value, Integer sortOrder) { }
    public record SkuView(Long id, String code, Map<String, String> specValues, String specLabel,
                          String unit, BigDecimal stock, boolean enabled, boolean defaultSku,
                          Integer sortOrder) { }
    private record SkuStockRow(long id, String code, String specLabel, String unit, BigDecimal stock) { }
    private record ExistingSku(long id, String signature, boolean defaultSku) { }
    public record StockAdjustmentResult(long skuId, String skuCode, String specLabel, String unit,
                                        BigDecimal before, BigDecimal after) { }
}
