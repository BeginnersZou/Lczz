package com.lczz.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.common.time.UtcTime;
import com.lczz.file.persistence.FileRelationRecord;
import com.lczz.file.persistence.FileRelationRecordMapper;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.progress.persistence.WorkOrderProgressEntity;
import com.lczz.progress.persistence.WorkOrderProgressMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressConfirmationService {
    private final WorkOrderMapper orders;
    private final WorkOrderProgressMapper progress;
    private final FileRelationRecordMapper relations;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    public ProgressConfirmationService(WorkOrderMapper orders, WorkOrderProgressMapper progress,
                                        FileRelationRecordMapper relations, ObjectMapper json, JdbcTemplate jdbc) {
        this.orders = orders; this.progress = progress; this.relations = relations; this.json = json; this.jdbc = jdbc;
    }

    public ConfirmationView inspect(WorkOrderProgressEntity record, List<FileRelationRecord> links, Long customerId) {
        String version = fingerprint(content(record, links));
        boolean adminApproved = Objects.equals(version, record.getAdminApprovedContentHash());
        boolean confirmed = !adminApproved && Objects.equals(version, record.getConfirmedContentHash()) && customerId != null
                && Objects.equals(customerId, record.getCustomerConfirmedBy());
        return new ConfirmationView(record.getId(), version, adminApproved ? "ADMIN_EDITED" : confirmed ? "CONFIRMED"
                : record.getConfirmedContentHash() == null ? "PENDING" : "CHANGED", confirmed,
                record.getCustomerConfirmedBy(), record.getCustomerConfirmedAt(), record.getConfirmedContentHash());
    }

    /** An administrator approves exactly the amended version, without impersonating a customer confirmation. */
    public void approveAdminCorrection(WorkOrderProgressEntity record, List<FileRelationRecord> links, long adminId) {
        String version = inspect(record, links, null).contentVersion();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        progress.update(null, new LambdaUpdateWrapper<WorkOrderProgressEntity>()
                .eq(WorkOrderProgressEntity::getId, record.getId())
                .set(WorkOrderProgressEntity::getAdminApprovedContentHash, version)
                .set(WorkOrderProgressEntity::getAdminEditedBy, adminId)
                .set(WorkOrderProgressEntity::getAdminEditedAt, now));
    }

    public void approveAdminCorrection(long progressId, long adminId) {
        WorkOrderProgressEntity record = progress.selectById(progressId);
        if (record == null) throw new BusinessException(404, "PROGRESS_NOT_FOUND", "施工进度不存在");
        List<FileRelationRecord> links = relations.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, "PROGRESS")
                .eq(FileRelationRecord::getBusinessId, progressId));
        approveAdminCorrection(record, links, adminId);
    }

    public void invalidateAdminApproval(long progressId) {
        progress.update(null, new LambdaUpdateWrapper<WorkOrderProgressEntity>()
                .eq(WorkOrderProgressEntity::getId, progressId)
                .set(WorkOrderProgressEntity::getAdminApprovedContentHash, null));
    }

    @Transactional
    public ConfirmationView confirm(AuthenticatedUser actor, long orderId, long progressId, String expectedVersion) {
        if (actor == null || !actor.hasRole(RoleCode.CUSTOMER) || actor.hasRole(RoleCode.ADMIN)
                || actor.hasRole(RoleCode.INSTALLER) || actor.hasRole(RoleCode.DEALER)) {
            throw new BusinessException(403, "PROGRESS_CONFIRMATION_FORBIDDEN", "仅订单绑定客户可确认本次进度");
        }
        var order = orders.selectForUpdate(orderId);
        if (order == null || !Objects.equals(order.getCustomerUserId(), actor.userId()))
            throw new BusinessException(404, "ORDER_NOT_BOUND", "订单不存在或未绑定到当前用户");
        if ("CANCELLED".equals(order.getOrderStatus()))
            throw new BusinessException(409, "ORDER_CANCELLED", "已作废订单不能确认施工进度");
        var record = progress.selectById(progressId);
        if (record == null || !Objects.equals(record.getOrderId(), orderId))
            throw new BusinessException(404, "PROGRESS_NOT_FOUND", "施工进度不存在或不属于该订单");
        List<FileRelationRecord> links = relations.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, "PROGRESS").eq(FileRelationRecord::getBusinessId, progressId));
        ConfirmationView state = inspect(record, links, order.getCustomerUserId());
        if (!Objects.equals(expectedVersion, state.contentVersion()))
            throw new BusinessException(409, "PROGRESS_CONTENT_CHANGED", "本条施工内容已变更，请重新查看后确认");
        if ("ADMIN_EDITED".equals(state.confirmationStatus()))
            throw new BusinessException(409, "PROGRESS_ADMIN_APPROVED", "管理员已修订本条进度，无需客户确认");
        if (state.customerConfirmed()) return state;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        progress.update(null, new LambdaUpdateWrapper<WorkOrderProgressEntity>()
                .eq(WorkOrderProgressEntity::getId, progressId)
                .set(WorkOrderProgressEntity::getConfirmedContentHash, state.contentVersion())
                .set(WorkOrderProgressEntity::getCustomerConfirmedBy, actor.userId())
                .set(WorkOrderProgressEntity::getCustomerConfirmedAt, now));
        jdbc.update("""
                INSERT INTO work_order_progress_confirmation(progress_id,customer_user_id,content_hash,content_snapshot,confirmed_at)
                VALUES (?,?,?,?,?)
                """, progressId, actor.userId(), state.contentVersion(), content(record, links), now);
        record.setConfirmedContentHash(state.contentVersion()); record.setCustomerConfirmedBy(actor.userId()); record.setCustomerConfirmedAt(now);
        // Confirmation only acknowledges this content. Never transition or seal the order here.
        return inspect(record, links, order.getCustomerUserId());
    }

    public String submissionFingerprint(String description, List<Long> files) {
        return fingerprint(serialize(List.of(description, files)));
    }

    private String content(WorkOrderProgressEntity record, List<FileRelationRecord> links) {
        var attachments = links.stream().filter(link -> Objects.equals(link.getBusinessId(), record.getId()))
                .sorted(Comparator.comparing((FileRelationRecord link) -> link.getSortOrder() == null ? 0 : link.getSortOrder())
                        .thenComparing(FileRelationRecord::getId))
                .map(link -> new Attachment(link.getFileId(), link.getUsageType(), link.getSortOrder())).toList();
        return serialize(new Content(record.getProgressType(), record.getDescription(), attachments));
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Unable to serialize progress content", error); }
    }
    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private record Attachment(long fileId, String usage, Integer sortOrder) { }
    private record Content(String type, String description, List<Attachment> attachments) { }
    public record ConfirmationView(long id, String contentVersion, String confirmationStatus, boolean customerConfirmed,
                                     Long customerConfirmedBy, @UtcTime LocalDateTime customerConfirmedAt, String confirmedContentVersion) { }
}
