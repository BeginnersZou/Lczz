package com.lczz.stocking.service;

import com.lczz.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnifiedPreparationServiceUnitTests {
    private JdbcTemplate jdbc;
    private UnifiedPreparationService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:unified_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,username VARCHAR(64),nickname VARCHAR(64),real_name VARCHAR(64))");
        jdbc.execute("CREATE TABLE work_order(id BIGINT PRIMARY KEY,order_no VARCHAR(64),description VARCHAR(1000),task_type VARCHAR(64),customer_name VARCHAR(64),deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE material_request(id BIGINT PRIMARY KEY,request_no VARCHAR(64),order_id BIGINT,installer_user_id BIGINT,request_status VARCHAR(32),submitted_at TIMESTAMP,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,display_stock DECIMAL(12,3))");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT PRIMARY KEY,stock DECIMAL(12,3))");
        jdbc.execute("CREATE TABLE material_request_item(id BIGINT PRIMARY KEY,request_id BIGINT,product_id BIGINT,sku_id BIGINT,product_code_snapshot VARCHAR(64),sku_code_snapshot VARCHAR(96),product_name_snapshot VARCHAR(255),sku_spec_snapshot VARCHAR(1000),model_spec_snapshot VARCHAR(255),unit_snapshot VARCHAR(32),requested_quantity DECIMAL(12,3),prepared_quantity DECIMAL(12,3),item_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE material_self_order(id BIGINT PRIMARY KEY,order_no VARCHAR(64),order_name VARCHAR(64),installer_id BIGINT,order_status VARCHAR(32),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE material_self_order_item(id BIGINT PRIMARY KEY,self_order_id BIGINT,sku_id BIGINT,product_id BIGINT,product_name_snapshot VARCHAR(255),sku_code_snapshot VARCHAR(96),spec_snapshot VARCHAR(1000),unit_snapshot VARCHAR(32),quantity INT)");
        service = new UnifiedPreparationService(jdbc);
        seed();
    }

    @Test
    void listsBothDomainsAndKeepsSelfOrderDetachedFromWorkOrder() {
        var page = service.list(1, 10, null, null, null);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.list()).extracting(UnifiedPreparationService.PreparationSummary::source)
                .containsExactlyInAnyOrder("W", "A");
        var self = page.list().stream().filter(row -> "A".equals(row.source())).findFirst().orElseThrow();
        assertThat(self.orderNo()).isEqualTo("A202609040001");
        assertThat(self.productName()).isEqualTo("客户下单");
        assertThat(self.orderId()).isNull();
        assertThat(self.itemCount()).isEqualTo(1);
        assertThat(self.totalQuantity()).isEqualByComparingTo("3");
        assertThat(service.list(1, 10, "25mm", "ORDERED", "A").total()).isOne();
        assertThat(service.list(1, 10, null, "PENDING", "A").total()).isZero();
    }

    @Test
    void exportsFilteredAndSingleOrderCsvWithoutPricesOrPhones() {
        String all = new String(service.exportList(null, null, null).content(), StandardCharsets.UTF_8);
        assertThat(all).contains("WO202609040001", "A202609040001", "PVC弯头管", "PVC-25-2M", "口径：25mm / 长度：2米");
        assertThat(all).doesNotContain("13810000001", "18.80");
        String one = new String(service.exportDetail("A", 20).content(), StandardCharsets.UTF_8);
        assertThat(one).contains("师傅自助下单", "张师傅", "客户下单");
        assertThat(service.exportDetail("A", 20).filename()).contains("A202609040001");
    }

    @Test
    void workDetailUsesSkuSnapshotAndSkuStockButKeepsLegacyFallback() {
        jdbc.update("INSERT INTO product VALUES (101,99)");
        jdbc.update("INSERT INTO product_sku VALUES (300,7)");
        jdbc.update("INSERT INTO material_request_item(id,request_id,product_id,sku_id,product_code_snapshot,sku_code_snapshot,product_name_snapshot,sku_spec_snapshot,model_spec_snapshot,unit_snapshot,requested_quantity,prepared_quantity,item_status) "
                + "VALUES (13,11,101,300,'PVC','PVC-35','PVC弯头管','口径：35mm','旧规格','个',4,0,'PENDING')");

        var detail = service.detail("W", 11);
        assertThat(detail.materials()).hasSize(2);
        var skuLine = detail.materials().stream().filter(item -> item.id() == 13).findFirst().orElseThrow();
        assertThat(skuLine.skuCode()).isEqualTo("PVC-35");
        assertThat(skuLine.spec()).isEqualTo("口径：35mm");
        assertThat(skuLine.stock()).isEqualByComparingTo("7");
        var legacyLine = detail.materials().stream().filter(item -> item.id() == 12).findFirst().orElseThrow();
        assertThat(legacyLine.skuCode()).isEqualTo("COPPER");
        assertThat(legacyLine.spec()).isEqualTo("φ6×0.8mm");
        assertThat(legacyLine.stock()).isEqualByComparingTo("8");
    }

    @Test
    void emptyFilteredExportStillContainsHeaders() {
        String csv = new String(service.exportList("不存在的耗材", null, null).content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("订单编号", "SKU编码", "数量");
        assertThat(csv.lines()).hasSize(1);
    }

    @Test
    void validatesSourceAndStatus() {
        assertThatThrownBy(() -> service.list(1, 10, null, null, "X"))
                .isInstanceOf(BusinessException.class).hasMessage("备货来源不合法");
        assertThatThrownBy(() -> service.list(1, 10, null, "UNKNOWN", null))
                .isInstanceOf(BusinessException.class).hasMessage("备货状态不合法");
    }

    private void seed() {
        jdbc.update("INSERT INTO sys_user VALUES (1,'installer',NULL,'张师傅')");
        jdbc.update("INSERT INTO work_order VALUES (10,'WO202609040001','空调安装','INSTALL','王客户',FALSE)");
        jdbc.update("INSERT INTO material_request VALUES (11,'MR001',10,1,'PENDING',CURRENT_TIMESTAMP,'现场申请')");
        jdbc.update("INSERT INTO product VALUES (100,8)");
        jdbc.update("INSERT INTO material_request_item(id,request_id,product_id,sku_id,product_code_snapshot,sku_code_snapshot,product_name_snapshot,sku_spec_snapshot,model_spec_snapshot,unit_snapshot,requested_quantity,prepared_quantity,item_status) "
                + "VALUES (12,11,100,NULL,'COPPER',NULL,'铜管',NULL,'φ6×0.8mm','米',2,0,'PENDING')");
        jdbc.update("INSERT INTO material_self_order VALUES (20,'A202609040001','客户下单',1,'ORDERED',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO material_self_order_item VALUES (21,20,200,101,'PVC弯头管','PVC-25-2M','口径：25mm / 长度：2米','根',3)");
    }
}
