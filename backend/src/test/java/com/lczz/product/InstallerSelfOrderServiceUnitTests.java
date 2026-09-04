package com.lczz.product;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.product.service.InstallerSelfOrderService;
import java.util.Set;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallerSelfOrderServiceUnitTests {
    JdbcTemplate jdbc;
    InstallerSelfOrderService service;
    final AuthenticatedUser installer = new AuthenticatedUser(11, "installer-a", "师傅甲", "13800000011", Set.of(RoleCode.INSTALLER));
    final AuthenticatedUser otherInstaller = new AuthenticatedUser(12, "installer-b", "师傅乙", "13800000012", Set.of(RoleCode.INSTALLER));
    final AuthenticatedUser customer = new AuthenticatedUser(21, "customer", "客户", "13800000021", Set.of(RoleCode.CUSTOMER));

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:cart_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE product(id BIGINT PRIMARY KEY,product_name VARCHAR(255),enabled BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE product_sku(id BIGINT PRIMARY KEY,product_id BIGINT,sku_code VARCHAR(200),spec_label VARCHAR(1000),unit VARCHAR(32),stock DECIMAL(12,3),enabled BOOLEAN,deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE installer_cart_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,installer_id BIGINT,sku_id BIGINT,quantity INT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(installer_id,sku_id))");
        jdbc.execute("CREATE TABLE material_self_order(id BIGINT AUTO_INCREMENT PRIMARY KEY,order_no VARCHAR(64),order_name VARCHAR(64),installer_id BIGINT,request_token VARCHAR(64),order_status VARCHAR(32),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(installer_id,request_token))");
        jdbc.execute("CREATE TABLE material_self_order_item(id BIGINT AUTO_INCREMENT PRIMARY KEY,self_order_id BIGINT,sku_id BIGINT,product_id BIGINT,product_name_snapshot VARCHAR(255),sku_code_snapshot VARCHAR(200),spec_snapshot VARCHAR(1000),unit_snapshot VARCHAR(32),quantity INT,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO product VALUES (1,'通用管材',TRUE,FALSE)");
        jdbc.update("INSERT INTO product_sku VALUES (101,1,'PIPE-WHITE-2M','颜色=白 / 长度=2米','根',10,TRUE,FALSE)");
        service = new InstallerSelfOrderService(jdbc, "027-82710326");
    }

    @Test
    void installerCanPersistCartAndSubmitSnapshotOrderWithoutDeductingStock() {
        assertThat(service.add(installer, 101, 3).totalQuantity()).isEqualTo(3);
        var cart = service.add(installer, 101, 2);
        assertThat(cart.items()).singleElement().satisfies(item -> assertThat(item.quantity()).isEqualTo(5));
        long cartId = cart.items().getFirst().id();
        service.update(installer, cartId, 4);

        var order = service.submit(installer, "submit-once");
        assertThat(order.orderNo()).startsWith("A");
        assertThat(order.orderName()).isEqualTo("客户下单");
        assertThat(order.pickupPhone()).isEqualTo("027-82710326");
        assertThat(order.items()).singleElement().satisfies(item -> {
            assertThat(item.specLabel()).isEqualTo("颜色=白 / 长度=2米");
            assertThat(item.quantity()).isEqualTo(4);
        });
        assertThat(service.cart(installer).items()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=101", java.math.BigDecimal.class)).isEqualByComparingTo("10");
        assertThat(service.list(installer, 1, 10).list()).hasSize(1);
        assertThat(service.list(otherInstaller, 1, 10).list()).isEmpty();
        assertThatThrownBy(() -> service.detail(otherInstaller, order.id())).isInstanceOf(BusinessException.class);
        assertThat(service.submit(installer, "submit-once").id()).isEqualTo(order.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material_self_order", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsNonInstallerInvalidQuantityAndStockOverflow() {
        assertThatThrownBy(() -> service.add(customer, 101, 1)).isInstanceOf(BusinessException.class).hasMessageContaining("安装师傅");
        assertThatThrownBy(() -> service.add(installer, 101, 0)).isInstanceOf(BusinessException.class).hasMessageContaining("正整数");
        assertThatThrownBy(() -> service.add(installer, 101, 11)).isInstanceOf(BusinessException.class).hasMessageContaining("库存");
    }
}
