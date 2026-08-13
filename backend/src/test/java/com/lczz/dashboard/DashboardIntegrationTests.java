package com.lczz.dashboard;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserMapper userMapper;
    @Autowired WorkOrderMapper orderMapper;
    @Autowired JwtService jwtService;

    private long adminId;
    private long installerId;
    private long otherInstallerId;
    private long customerId;
    private long dealerId;
    private String adminToken;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM work_order_review");
        jdbcTemplate.update("DELETE FROM work_order_progress");
        jdbcTemplate.update("DELETE FROM material_request_item");
        jdbcTemplate.update("DELETE FROM material_request");
        jdbcTemplate.update("DELETE FROM work_order_status_history");
        jdbcTemplate.update("DELETE FROM work_order_assignment");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        adminId = createUser("dashboard-admin", "管理员", "13950000001", RoleCode.ADMIN, "APPROVED");
        installerId = createUser(null, "安装师傅甲", "13950000002", RoleCode.INSTALLER, "APPROVED");
        otherInstallerId = createUser(null, "安装师傅乙", "13950000003", RoleCode.INSTALLER, "APPROVED");
        customerId = createUser(null, "普通客户", "13850000001", RoleCode.CUSTOMER, "APPROVED");
        dealerId = createUser(null, "经销商客户", "13850000002", RoleCode.DEALER, "APPROVED");
        adminToken = token(adminId, RoleCode.ADMIN);
    }

    @Test
    void adminDashboardAggregatesOrdersStockAuditsAndReviews() throws Exception {
        long pendingOrder = createOrder("WO-DASH-001", customerId, installerId, "PENDING_VISIT", false);
        createOrder("WO-DASH-002", customerId, installerId, "IN_PROGRESS", false);
        createOrder("WO-DASH-003", customerId, installerId, "PENDING_REVIEW", false);
        long reviewedOrder = createOrder("WO-DASH-004", customerId, installerId, "REVIEWED", false);
        createOrder("WO-DASH-DELETED", customerId, installerId, "PENDING_VISIT", true);
        createUser(null, "待审核用户", "13850000003", RoleCode.CUSTOMER, "PENDING");
        jdbcTemplate.update("INSERT INTO product_category(category_code, category_name, category_level, enabled, sort_order, deleted) "
                + "VALUES ('DASHBOARD', '辅材', 1, TRUE, 0, FALSE)");
        Long categoryId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM product_category", Long.class);
        jdbcTemplate.update("INSERT INTO product(product_code, product_name, category_id, unit, display_stock, enabled, "
                + "sort_order, version, deleted) VALUES ('P-DASH-1', '低库存耗材', ?, '件', 3, TRUE, 0, 0, FALSE)", categoryId);
        jdbcTemplate.update("INSERT INTO work_order_review(order_id, reviewer_user_id, score, liked, content) "
                + "VALUES (?, ?, 5, TRUE, '很好')", reviewedOrder, customerId);

        mockMvc.perform(get("/api/v1/dashboard/overview").param("range", "day")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrders").value(4))
                .andExpect(jsonPath("$.data.pendingAssign").value(1))
                .andExpect(jsonPath("$.data.processingOrders").value(1))
                .andExpect(jsonPath("$.data.completedOrders").value(2))
                .andExpect(jsonPath("$.data.lowStock").value(1))
                .andExpect(jsonPath("$.data.pendingAudit").value(1))
                .andExpect(jsonPath("$.data.rating").value(5.0))
                .andExpect(jsonPath("$.data.ratingCount").value(1))
                .andExpect(jsonPath("$.data.ratingBars[0].percent").value(100));

        mockMvc.perform(get("/api/dashboard/order-status").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].value").value(1));
        mockMvc.perform(get("/api/dashboard/order-trend").param("range", "7d")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.xAxis.length()").value(7))
                .andExpect(jsonPath("$.data.series[0].data.length()").value(7));
        assertThat(pendingOrder).isPositive();
    }

    @Test
    void roleTodosAreIsolatedByAssignedOrBoundUser() throws Exception {
        createOrder("WO-DASH-101", customerId, installerId, "PENDING_VISIT", false);
        createOrder("WO-DASH-102", customerId, installerId, "IN_PROGRESS", false);
        createOrder("WO-DASH-103", customerId, installerId, "PENDING_REVIEW", false);
        createOrder("WO-DASH-104", dealerId, otherInstallerId, "PENDING_REVIEW", false);
        createOrder("WO-DASH-105", dealerId, otherInstallerId, "PENDING_VISIT", false);

        mockMvc.perform(get("/api/dashboard/todo").header("Authorization", "Bearer " + token(installerId, RoleCode.INSTALLER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("INSTALLER"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("WO-DASH-102"));
        mockMvc.perform(get("/api/dashboard/todo").header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("WO-DASH-103"));
        mockMvc.perform(get("/api/v1/dashboard/todo").header("Authorization", "Bearer " + token(dealerId, RoleCode.DEALER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("DEALER"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("WO-DASH-104"));
        mockMvc.perform(get("/api/dashboard/todo").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    void managementStatisticsRequireAdminAndRejectUnknownRange() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview")
                        .header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/dashboard/order-trend").param("range", "quarter")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_DASHBOARD_RANGE"));
    }

    @Test
    void emptyDashboardReturnsZeroesAndEmptyTodoInsteadOfServerError() throws Exception {
        mockMvc.perform(get("/api/dashboard/overview").param("range", "30d")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.todayOrders").value(0))
                .andExpect(jsonPath("$.data.rating").value(0.0))
                .andExpect(jsonPath("$.data.ratingBars.length()").value(5));
        mockMvc.perform(get("/api/dashboard/todo").header("Authorization", "Bearer " + token(installerId, RoleCode.INSTALLER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());
    }

    private long createOrder(String number, long customer, long installer, String status, boolean deleted) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(number);
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus(status);
        order.setCustomerUserId(customer);
        order.setCustomerName("测试客户");
        order.setCustomerPhone("13850000001");
        order.setInstallerUserId(installer);
        order.setDetailedAddress("测试地址");
        order.setRequiredStartAt(LocalDate.now().atTime(9, 0));
        order.setVersion(0);
        order.setDeleted(deleted);
        order.setCreatedBy(adminId);
        orderMapper.insert(order);
        return order.getId();
    }

    private long createUser(String username, String name, String phone, RoleCode role, String auditStatus) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setRealName(name);
        user.setPhone(phone);
        user.setAccountStatus("ENABLED");
        user.setAuditStatus(auditStatus);
        user.setBlacklist(false);
        user.setDeleted(false);
        userMapper.insert(user);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?",
                user.getId(), role.name());
        return user.getId();
    }

    private String token(long userId, RoleCode role) {
        return jwtService.issue(new AuthenticatedUser(userId, null, "测试用户", null, Set.of(role))).value();
    }
}
