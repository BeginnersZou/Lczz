package com.lczz.product;

import com.lczz.common.exception.BusinessException;
import com.lczz.product.service.ProductSkuService;
import com.lczz.product.service.ProductSkuService.DimensionCommand;
import com.lczz.product.service.ProductSkuService.SkuCommand;
import com.lczz.product.service.ProductSkuService.ValueCommand;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSkuServiceUnitTests {
    JdbcTemplate jdbc;
    ProductSkuService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:sku_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,display_stock DECIMAL(12,3))");
        jdbc.execute("CREATE TABLE product_spec_dimension(id BIGINT AUTO_INCREMENT PRIMARY KEY,product_id BIGINT,dimension_name VARCHAR(64),sort_order INT,deleted BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(product_id,dimension_name,deleted))");
        jdbc.execute("CREATE TABLE product_spec_value(id BIGINT AUTO_INCREMENT PRIMARY KEY,dimension_id BIGINT,spec_value VARCHAR(128),sort_order INT,deleted BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(dimension_id,spec_value,deleted))");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT AUTO_INCREMENT PRIMARY KEY,product_id BIGINT,sku_code VARCHAR(96),spec_signature VARCHAR(2000),spec_signature_hash VARCHAR(64),spec_label VARCHAR(2000),unit VARCHAR(32),stock DECIMAL(12,3),enabled BOOLEAN,default_sku BOOLEAN,sort_order INT,version INT,deleted BOOLEAN,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(product_id,sku_code,deleted),UNIQUE(product_id,spec_signature_hash,deleted))");
        jdbc.execute("CREATE TABLE product_sku_spec_value(sku_id BIGINT,dimension_id BIGINT,spec_value_id BIGINT,PRIMARY KEY(sku_id,dimension_id))");
        service = new ProductSkuService(jdbc);
    }

    @Test
    void supportsArbitraryNamesAndMultiDimensionCartesianSkus() {
        List<DimensionCommand> dimensions = List.of(
                new DimensionCommand("材质", List.of(new ValueCommand("PVC", 0), new ValueCommand("PPR", 1)), 0),
                new DimensionCommand("长度", List.of(new ValueCommand("1米", 0), new ValueCommand("2.5米", 1)), 1));
        service.replace(7, "PIPE", null, "根", BigDecimal.ZERO, dimensions, List.of(
                sku("PIPE-1", "PVC", "1米", 3), sku("PIPE-2", "PVC", "2.5米", 4),
                sku("PIPE-3", "PPR", "1米", 5), sku("PIPE-4", "PPR", "2.5米", 6)));

        var view = service.get(7, true);
        assertThat(view.dimensions()).extracting(ProductSkuService.DimensionView::name).containsExactly("材质", "长度");
        assertThat(view.skus()).hasSize(4);
        assertThat(view.skus().getLast().specValues()).containsEntry("材质", "PPR").containsEntry("长度", "2.5米");
    }

    @Test
    void createsDefaultSkuAndRejectsIncompleteCombinations() {
        service.replace(8, "LEGACY", "通用", "件", BigDecimal.TEN, List.of(), List.of());
        assertThat(service.get(8, false).skus()).singleElement().satisfies(sku -> {
            assertThat(sku.defaultSku()).isTrue();
            assertThat(sku.stock()).isEqualByComparingTo("10");
        });

        assertThatThrownBy(() -> service.replace(9, "BAD", null, "件", BigDecimal.ZERO,
                List.of(new DimensionCommand("大小", List.of(new ValueCommand("大", 0), new ValueCommand("小", 1)), 0)),
                List.of(new SkuCommand("BAD-1", java.util.Map.of("大小", "大"), "件", BigDecimal.ONE, true, 0))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("完整提交");
    }

    @Test
    void adjustsOneConcreteSkuAndRefreshesAggregateStock() {
        jdbc.update("INSERT INTO product(id,display_stock) VALUES(?,?)", 10L, BigDecimal.ZERO);
        service.replace(10, "PIPE", null, "件", BigDecimal.ZERO,
                List.of(new DimensionCommand("口径", List.of(new ValueCommand("25", 0), new ValueCommand("35", 1)), 0)),
                List.of(new SkuCommand("PIPE-25", java.util.Map.of("口径", "25"), "个", BigDecimal.valueOf(3), true, 0),
                        new SkuCommand("PIPE-35", java.util.Map.of("口径", "35"), "个", BigDecimal.valueOf(5), true, 1)));
        long skuId = service.get(10, true).skus().getFirst().id();

        var result = service.adjustStock(10, skuId, "IN", BigDecimal.valueOf(2));

        assertThat(result.after()).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("SELECT display_stock FROM product WHERE id=10", BigDecimal.class))
                .isEqualByComparingTo("10");
        assertThatThrownBy(() -> service.adjustStock(10, null, "IN", BigDecimal.ONE))
                .isInstanceOf(BusinessException.class).hasMessageContaining("具体SKU");
    }

    @Test
    void updatesMaxLengthCodeAndAcceptsLongValidSpecificationWithoutDatabaseOverflow() {
        String code = "S".repeat(96);
        List<DimensionCommand> dimensions = new java.util.ArrayList<>();
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < 8; index++) {
            String name = ("维" + index + "N".repeat(62)).substring(0, 64);
            String value = ("值" + index + "V".repeat(126)).substring(0, 128);
            dimensions.add(new DimensionCommand(name, List.of(new ValueCommand(value, 0)), index));
            values.put(name, value);
        }
        List<SkuCommand> skus = List.of(new SkuCommand(code, values, "件", BigDecimal.ONE, true, 0));

        service.replace(11, "LONG", null, "件", BigDecimal.ZERO, dimensions, skus);
        service.replace(11, "LONG", null, "件", BigDecimal.ZERO, dimensions, skus);

        assertThat(service.get(11, true).skus()).singleElement()
                .extracting(ProductSkuService.SkuView::code).isEqualTo(code);
    }

    private SkuCommand sku(String code, String material, String length, int stock) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("材质", material); values.put("长度", length);
        return new SkuCommand(code, values, "根", BigDecimal.valueOf(stock), true, null);
    }
}
