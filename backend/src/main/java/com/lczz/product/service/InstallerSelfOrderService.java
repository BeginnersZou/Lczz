package com.lczz.product.service;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallerSelfOrderService {
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final JdbcTemplate jdbc;
    private final String pickupPhone;

    public InstallerSelfOrderService(JdbcTemplate jdbc,
                                     @Value("${lczz.self-order.pickup-phone:}") String pickupPhone) {
        this.jdbc = jdbc;
        this.pickupPhone = pickupPhone == null ? "" : pickupPhone.trim();
    }

    public CartView cart(AuthenticatedUser actor) {
        requireInstaller(actor);
        List<CartItemView> items = jdbc.query("""
                SELECT c.id, c.sku_id, c.quantity, p.id product_id, p.product_name,
                       s.sku_code, s.spec_label, s.unit, s.stock, s.enabled sku_enabled,
                       p.enabled product_enabled,s.deleted sku_deleted,p.deleted product_deleted
                FROM installer_cart_item c
                JOIN product_sku s ON s.id=c.sku_id
                JOIN product p ON p.id=s.product_id
                WHERE c.installer_id=?
                ORDER BY c.updated_at DESC,c.id DESC
                """, (rs, row) -> {
            BigDecimal stock = rs.getBigDecimal("stock");
            int quantity = rs.getInt("quantity");
            boolean active = rs.getBoolean("sku_enabled") && rs.getBoolean("product_enabled")
                    && !rs.getBoolean("sku_deleted") && !rs.getBoolean("product_deleted");
            boolean inStock = stock != null && stock.signum() > 0;
            boolean withinStock = inStock && BigDecimal.valueOf(quantity).compareTo(stock) <= 0;
            boolean available = active && withinStock;
            String unavailableReason = !active ? "该规格已停用，请删除后重新选择"
                    : !inStock ? "该规格已售罄，请删除后重新选择"
                    : "选择数量超过当前库存，请调整数量";
            return new CartItemView(rs.getLong("id"), rs.getLong("product_id"),
                    rs.getString("product_name"), rs.getLong("sku_id"), rs.getString("sku_code"),
                    rs.getString("spec_label"), rs.getString("unit"), stock,
                    quantity, available, available ? null : unavailableReason);
        }, actor.userId());
        return new CartView(items, items.stream().mapToInt(CartItemView::quantity).sum());
    }

    @Transactional
    public CartView add(AuthenticatedUser actor, long skuId, int quantity) {
        requireInstaller(actor);
        validateQuantity(quantity);
        SkuRow sku = requireAvailableSku(skuId, true);
        ensureWithinStock(quantity, sku.stock());
        List<Long> ids = jdbc.query("SELECT id FROM installer_cart_item WHERE installer_id=? AND sku_id=?",
                (rs, row) -> rs.getLong(1), actor.userId(), skuId);
        if (ids.isEmpty()) {
            jdbc.update("INSERT INTO installer_cart_item(installer_id,sku_id,quantity) VALUES (?,?,?)",
                    actor.userId(), skuId, quantity);
        } else {
            Integer existing = jdbc.queryForObject("SELECT quantity FROM installer_cart_item WHERE id=?",
                    Integer.class, ids.getFirst());
            int next = Math.addExact(existing == null ? 0 : existing, quantity);
            ensureWithinStock(next, sku.stock());
            jdbc.update("UPDATE installer_cart_item SET quantity=? WHERE id=? AND installer_id=?",
                    next, ids.getFirst(), actor.userId());
        }
        return cart(actor);
    }

    @Transactional
    public CartView update(AuthenticatedUser actor, long itemId, int quantity) {
        requireInstaller(actor);
        validateQuantity(quantity);
        List<Long> skuIds = jdbc.query("SELECT sku_id FROM installer_cart_item WHERE id=? AND installer_id=?",
                (rs, row) -> rs.getLong(1), itemId, actor.userId());
        if (skuIds.isEmpty()) throw new BusinessException(404, "CART_ITEM_NOT_FOUND", "购物车明细不存在");
        SkuRow sku = requireAvailableSku(skuIds.getFirst(), true);
        ensureWithinStock(quantity, sku.stock());
        jdbc.update("UPDATE installer_cart_item SET quantity=? WHERE id=? AND installer_id=?",
                quantity, itemId, actor.userId());
        return cart(actor);
    }

    @Transactional
    public CartView remove(AuthenticatedUser actor, long itemId) {
        requireInstaller(actor);
        int changed = jdbc.update("DELETE FROM installer_cart_item WHERE id=? AND installer_id=?", itemId, actor.userId());
        if (changed == 0) throw new BusinessException(404, "CART_ITEM_NOT_FOUND", "购物车明细不存在");
        return cart(actor);
    }

    @Transactional
    public CartView clear(AuthenticatedUser actor) {
        requireInstaller(actor);
        jdbc.update("DELETE FROM installer_cart_item WHERE installer_id=?", actor.userId());
        return new CartView(List.of(), 0);
    }

    @Transactional
    public SelfOrderView submit(AuthenticatedUser actor, String requestToken) {
        requireInstaller(actor);
        String normalizedToken = requestToken == null ? "" : requestToken.trim();
        if (normalizedToken.isBlank() || normalizedToken.length() > 64) {
            throw new BusinessException("INVALID_REQUEST_TOKEN", "提交标识不合法，请刷新页面后重试");
        }
        if (pickupPhone.isBlank()) {
            throw new BusinessException(503, "PICKUP_PHONE_NOT_CONFIGURED", "取货联系电话尚未配置，请联系管理员");
        }
        List<SubmitRow> rows = jdbc.query("""
                SELECT c.id cart_id,c.sku_id,c.quantity,p.id product_id,p.product_name,
                       p.enabled product_enabled,p.deleted product_deleted,s.sku_code,s.spec_label,
                       s.unit,s.stock,s.enabled sku_enabled,s.deleted sku_deleted
                FROM installer_cart_item c
                JOIN product_sku s ON s.id=c.sku_id
                JOIN product p ON p.id=s.product_id
                WHERE c.installer_id=? ORDER BY c.id FOR UPDATE
                """, (rs, row) -> new SubmitRow(rs.getLong("cart_id"), rs.getLong("sku_id"),
                rs.getInt("quantity"), rs.getLong("product_id"), rs.getString("product_name"),
                rs.getBoolean("product_enabled"), rs.getBoolean("product_deleted"), rs.getString("sku_code"),
                rs.getString("spec_label"), rs.getString("unit"), rs.getBigDecimal("stock"),
                rs.getBoolean("sku_enabled"), rs.getBoolean("sku_deleted")), actor.userId());
        SelfOrderView submitted = findByRequestToken(actor, normalizedToken);
        if (submitted != null) return submitted;
        if (rows.isEmpty()) throw new BusinessException("EMPTY_CART", "购物车为空，无法提交");
        for (SubmitRow row : rows) {
            validateQuantity(row.quantity());
            if (!row.productEnabled() || row.productDeleted() || !row.skuEnabled() || row.skuDeleted()) {
                throw new BusinessException(409, "SKU_UNAVAILABLE", row.productName() + "的所选规格已停用");
            }
            ensureWithinStock(row.quantity(), row.stock());
        }
        String orderNo = "A" + LocalDateTime.now().format(NUMBER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        long orderId = insertAndGet("""
                INSERT INTO material_self_order(order_no,order_name,installer_id,request_token,order_status)
                VALUES (?,'客户下单',?,?,'ORDERED')
                """, orderNo, actor.userId(), normalizedToken);
        for (SubmitRow row : rows) {
            jdbc.update("""
                    INSERT INTO material_self_order_item(self_order_id,sku_id,product_id,product_name_snapshot,
                      sku_code_snapshot,spec_snapshot,unit_snapshot,quantity)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, orderId, row.skuId(), row.productId(), row.productName(), row.skuCode(),
                    row.specLabel(), row.unit(), row.quantity());
        }
        jdbc.update("DELETE FROM installer_cart_item WHERE installer_id=?", actor.userId());
        return detail(actor, orderId);
    }

    public SelfOrderPage list(AuthenticatedUser actor, int page, int pageSize) {
        requireInstaller(actor);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM material_self_order WHERE installer_id=?",
                Long.class, actor.userId());
        int offset = (page - 1) * pageSize;
        List<SelfOrderSummary> rows = jdbc.query("""
                SELECT o.id,o.order_no,o.order_name,o.order_status,o.created_at,
                       COUNT(i.id) item_count,COALESCE(SUM(i.quantity),0) total_quantity
                FROM material_self_order o LEFT JOIN material_self_order_item i ON i.self_order_id=o.id
                WHERE o.installer_id=? GROUP BY o.id,o.order_no,o.order_name,o.order_status,o.created_at
                ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new SelfOrderSummary(rs.getLong("id"), rs.getString("order_no"),
                rs.getString("order_name"), rs.getString("order_status"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getInt("item_count"), rs.getInt("total_quantity")), actor.userId(), pageSize, offset);
        return new SelfOrderPage(rows, total, page, pageSize, pickupPhone);
    }

    public SelfOrderView detail(AuthenticatedUser actor, long id) {
        requireInstaller(actor);
        List<SelfOrderView> orders = jdbc.query("""
                SELECT id,order_no,order_name,order_status,created_at FROM material_self_order
                WHERE id=? AND installer_id=?
                """, (rs, row) -> new SelfOrderView(rs.getLong("id"), rs.getString("order_no"),
                rs.getString("order_name"), rs.getString("order_status"), rs.getTimestamp("created_at").toLocalDateTime(),
                pickupPhone, new java.util.ArrayList<>()), id, actor.userId());
        if (orders.isEmpty()) throw new BusinessException(404, "SELF_ORDER_NOT_FOUND", "取货订单不存在");
        SelfOrderView order = orders.getFirst();
        order.items().addAll(jdbc.query("""
                SELECT id,sku_id,product_id,product_name_snapshot,sku_code_snapshot,spec_snapshot,
                       unit_snapshot,quantity FROM material_self_order_item WHERE self_order_id=? ORDER BY id
                """, (rs, row) -> new SelfOrderItemView(rs.getLong("id"), rs.getObject("sku_id", Long.class),
                rs.getObject("product_id", Long.class), rs.getString("product_name_snapshot"),
                rs.getString("sku_code_snapshot"), rs.getString("spec_snapshot"),
                rs.getString("unit_snapshot"), rs.getInt("quantity")), id));
        return order;
    }

    private SelfOrderView findByRequestToken(AuthenticatedUser actor, String requestToken) {
        List<Long> ids = jdbc.query("SELECT id FROM material_self_order WHERE installer_id=? AND request_token=?",
                (rs, row) -> rs.getLong(1), actor.userId(), requestToken);
        return ids.isEmpty() ? null : detail(actor, ids.getFirst());
    }

    private SkuRow requireAvailableSku(long skuId, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<SkuRow> rows = jdbc.query("""
                SELECT s.id,s.stock,s.enabled,s.deleted,p.enabled product_enabled,p.deleted product_deleted
                FROM product_sku s JOIN product p ON p.id=s.product_id WHERE s.id=?
                """ + suffix, (rs, row) -> new SkuRow(rs.getLong("id"), rs.getBigDecimal("stock"),
                rs.getBoolean("enabled"), rs.getBoolean("deleted"), rs.getBoolean("product_enabled"),
                rs.getBoolean("product_deleted")), skuId);
        if (rows.isEmpty()) throw new BusinessException(404, "SKU_NOT_FOUND", "耗材规格不存在");
        SkuRow sku = rows.getFirst();
        if (!sku.enabled() || sku.deleted() || !sku.productEnabled() || sku.productDeleted()) {
            throw new BusinessException(409, "SKU_UNAVAILABLE", "该耗材规格已停用");
        }
        return sku;
    }

    private void requireInstaller(AuthenticatedUser actor) {
        if (actor == null || !actor.hasRole(RoleCode.INSTALLER)) {
            throw new BusinessException(403, "INSTALLER_ONLY", "仅安装师傅可以使用耗材购物车和自助下单");
        }
    }
    private void validateQuantity(int quantity) {
        if (quantity <= 0) throw new BusinessException("INVALID_QUANTITY", "数量必须为正整数");
    }
    private void ensureWithinStock(int quantity, BigDecimal stock) {
        if (stock == null || BigDecimal.valueOf(quantity).compareTo(stock) > 0) {
            throw new BusinessException(409, "INSUFFICIENT_SKU_STOCK", "选择数量不能超过当前库存");
        }
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

    private record SkuRow(long id, BigDecimal stock, boolean enabled, boolean deleted,
                          boolean productEnabled, boolean productDeleted) { }
    private record SubmitRow(long cartId, long skuId, int quantity, long productId, String productName,
                             boolean productEnabled, boolean productDeleted, String skuCode, String specLabel,
                             String unit, BigDecimal stock, boolean skuEnabled, boolean skuDeleted) { }
    public record CartView(List<CartItemView> items, int totalQuantity) { }
    public record CartItemView(long id, long productId, String productName, long skuId, String skuCode,
                               String specLabel, String unit, BigDecimal stock, int quantity,
                               boolean available, String unavailableReason) { }
    public record SelfOrderPage(List<SelfOrderSummary> list, long total, int page, int pageSize,
                                String pickupPhone) { }
    public record SelfOrderSummary(long id, String orderNo, String orderName, String status,
                                   LocalDateTime createdAt, int itemCount, int totalQuantity) { }
    public record SelfOrderView(long id, String orderNo, String orderName, String status,
                                LocalDateTime createdAt, String pickupPhone,
                                List<SelfOrderItemView> items) { }
    public record SelfOrderItemView(long id, Long skuId, Long productId, String productName,
                                    String skuCode, String specLabel, String unit, int quantity) { }
}
