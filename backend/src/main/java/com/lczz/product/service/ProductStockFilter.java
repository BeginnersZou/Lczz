package com.lczz.product.service;

import com.lczz.common.exception.BusinessException;
import java.util.Locale;

/** Shared stock predicates for queries against the product table. */
public final class ProductStockFilter {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String ACTIVE_SKUS = " FROM product_sku ps "
            + "WHERE ps.product_id=product.id AND ps.enabled=TRUE AND ps.deleted=FALSE";
    private static final String MIN_STOCK = "COALESCE((SELECT MIN(ps.stock)" + ACTIVE_SKUS
            + "), product.display_stock, 0)";
    private static final String MAX_STOCK = "COALESCE((SELECT MAX(ps.stock)" + ACTIVE_SKUS
            + "), product.display_stock, 0)";

    private ProductStockFilter() {}

    // Only fixed SQL is returned; request values select a predicate, never enter the SQL.
    public static String condition(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) return null;
        return switch (rawStatus.trim().toLowerCase(Locale.ROOT)) {
            case "all" -> null;
            case "warning" -> "product.enabled=TRUE AND " + MIN_STOCK + " <= " + LOW_STOCK_THRESHOLD;
            case "empty" -> MAX_STOCK + " = 0";
            case "low" -> MAX_STOCK + " > 0 AND " + MIN_STOCK + " <= " + LOW_STOCK_THRESHOLD;
            case "normal" -> MIN_STOCK + " > " + LOW_STOCK_THRESHOLD;
            default -> throw new BusinessException("INVALID_STOCK_STATUS", "库存状态只支持 warning、normal、low 或 empty");
        };
    }
}
