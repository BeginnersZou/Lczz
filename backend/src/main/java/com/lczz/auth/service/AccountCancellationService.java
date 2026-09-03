package com.lczz.auth.service;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.service.FileService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCancellationService {
    private final JdbcTemplate jdbcTemplate;
    private final FileService fileService;

    public AccountCancellationService(JdbcTemplate jdbcTemplate, FileService fileService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileService = fileService;
    }

    @Transactional
    public void cancel(AuthenticatedUser principal, boolean confirmed) {
        if (!confirmed) {
            throw new BusinessException(400, "CANCELLATION_NOT_CONFIRMED", "请确认注销账号");
        }
        Set<RoleCode> roles = principal.roles();
        if (roles.size() != 1 || !roles.contains(RoleCode.CUSTOMER)) {
            throw new BusinessException(403, "ACCOUNT_CANCELLATION_FORBIDDEN", "业务人员账号请联系管理员处理");
        }

        long userId = principal.userId();
        List<Long> orderIds = jdbcTemplate.queryForList(
                "SELECT id FROM work_order WHERE customer_user_id=?", Long.class, userId);
        for (Long orderId : orderIds) {
            jdbcTemplate.update("UPDATE work_order SET customer_user_id=NULL, customer_name=?, customer_phone=?, "
                            + "province_code=NULL, province_name=NULL, city_code=NULL, city_name=NULL, "
                            + "district_code=NULL, district_name=NULL, detailed_address=?, updated_at=? WHERE id=?",
                    "已注销用户", "已删除", "已删除", LocalDateTime.now(ZoneOffset.UTC), orderId);
        }

        List<Long> reviewIds = jdbcTemplate.queryForList(
                "SELECT id FROM work_order_review WHERE reviewer_user_id=?", Long.class, userId);
        fileService.deleteBusinessFiles("REVIEW", reviewIds);
        jdbcTemplate.update("DELETE FROM work_order_review WHERE reviewer_user_id=?", userId);
        jdbcTemplate.update("DELETE FROM user_wechat_identity WHERE user_id=?", userId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
        jdbcTemplate.update("UPDATE sys_user SET username=NULL, password_hash=NULL, nickname=?, real_name=NULL, "
                        + "gender=NULL, phone=NULL, avatar_file_id=NULL, account_status='DISABLED', audit_reason=NULL, "
                        + "last_login_at=NULL, last_login_ip=NULL, deleted=TRUE, deleted_at=?, updated_at=? WHERE id=?",
                "已注销用户", LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC), userId);
    }
}
