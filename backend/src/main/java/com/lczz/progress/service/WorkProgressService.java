package com.lczz.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.persistence.FileAssetRecord;
import com.lczz.file.persistence.FileAssetRecordMapper;
import com.lczz.file.persistence.FileRelationRecord;
import com.lczz.file.persistence.FileRelationRecordMapper;
import com.lczz.file.service.FileService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.order.persistence.WorkOrderStatusHistoryEntity;
import com.lczz.order.persistence.WorkOrderStatusHistoryMapper;
import com.lczz.progress.persistence.WorkOrderProgressEntity;
import com.lczz.progress.persistence.WorkOrderProgressMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkProgressService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING_VISIT", "IN_PROGRESS");
    private final WorkOrderProgressMapper progressMapper;
    private final WorkOrderMapper orderMapper;
    private final WorkOrderStatusHistoryMapper historyMapper;
    private final FileAssetRecordMapper fileMapper;
    private final FileRelationRecordMapper relationMapper;
    private final FileService fileService;

    public WorkProgressService(WorkOrderProgressMapper progressMapper, WorkOrderMapper orderMapper,
                               WorkOrderStatusHistoryMapper historyMapper,
                               FileAssetRecordMapper fileMapper,
                               FileRelationRecordMapper relationMapper, FileService fileService) {
        this.progressMapper = progressMapper;
        this.orderMapper = orderMapper;
        this.historyMapper = historyMapper;
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.fileService = fileService;
    }

    public List<ProgressView> list(AuthenticatedUser actor, long orderId) {
        requireAccessibleOrder(actor, orderId);
        List<WorkOrderProgressEntity> records = progressMapper.selectList(
                new LambdaQueryWrapper<WorkOrderProgressEntity>()
                        .eq(WorkOrderProgressEntity::getOrderId, orderId)
                        .orderByAsc(WorkOrderProgressEntity::getSubmittedAt)
                        .orderByAsc(WorkOrderProgressEntity::getId));
        return toViews(actor, records);
    }

    @Transactional
    public ProgressView submitProgress(AuthenticatedUser actor, long orderId, ProgressCommand command) {
        WorkOrderEntity order = requireAssignedOrder(actor, orderId);
        ensureActive(order, "ORDER_NOT_ACCEPTING_PROGRESS", "当前订单状态不能提交施工进度");
        WorkOrderProgressEntity progress = insert(actor, orderId, "PROGRESS", command.description());
        bindFiles(actor, progress.getId(), "PROGRESS", normalizeFiles(command.fileIds()));
        transitionToInProgress(order, actor.userId(), "安装师傅提交施工进度");
        return toViews(actor, List.of(progress)).getFirst();
    }

    private WorkOrderProgressEntity insert(AuthenticatedUser actor, long orderId, String type, String description) {
        WorkOrderProgressEntity record = new WorkOrderProgressEntity();
        record.setOrderId(orderId);
        record.setInstallerUserId(actor.userId());
        record.setProgressType(type);
        record.setDescription(description.trim());
        record.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        progressMapper.insert(record);
        return progressMapper.selectById(record.getId());
    }

    private void transitionToInProgress(WorkOrderEntity order, long actorId, String reason) {
        if (!"PENDING_VISIT".equals(order.getOrderStatus())) return;
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getId, order.getId())
                .eq(WorkOrderEntity::getInstallerUserId, actorId)
                .eq(WorkOrderEntity::getOrderStatus, "PENDING_VISIT")
                .eq(WorkOrderEntity::getVersion, order.getVersion())
                .set(WorkOrderEntity::getOrderStatus, "IN_PROGRESS")
                .set(WorkOrderEntity::getUpdatedBy, actorId)
                .set(WorkOrderEntity::getVersion, order.getVersion() + 1));
        if (updated != 1) throw new BusinessException(409, "ORDER_STATUS_CONFLICT", "订单状态已变化，请刷新后重试");
        recordStatus(order.getId(), "PENDING_VISIT", "IN_PROGRESS", reason, actorId);
        order.setOrderStatus("IN_PROGRESS");
        order.setVersion(order.getVersion() + 1);
    }

    private void bindFiles(AuthenticatedUser actor, long progressId, String usage, List<Long> fileIds) {
        for (int index = 0; index < fileIds.size(); index++) {
            fileService.bind(actor, fileIds.get(index),
                    new FileService.RelationCommand("PROGRESS", progressId, usage, index));
        }
    }

    private List<Long> normalizeFiles(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : fileIds) {
            if (id == null || id < 1) throw new BusinessException("INVALID_PROGRESS_FILE", "图片 ID 不合法");
            if (!unique.add(id)) throw new BusinessException("DUPLICATE_PROGRESS_FILE", "施工图片不能重复");
        }
        if (unique.size() > 9) throw new BusinessException("FILE_COUNT_LIMIT", "施工图片最多 9 张");
        return List.copyOf(unique);
    }

    private WorkOrderEntity requireAssignedOrder(AuthenticatedUser actor, long orderId) {
        if (!actor.hasRole(RoleCode.INSTALLER)) {
            throw new BusinessException(403, "PROGRESS_SUBMIT_FORBIDDEN", "仅安装师傅可以提交施工信息");
        }
        WorkOrderEntity order = orderMapper.selectForUpdate(orderId);
        if (order == null || !Objects.equals(order.getInstallerUserId(), actor.userId())) {
            throw new BusinessException(404, "ORDER_NOT_ASSIGNED", "订单不存在或未指派给当前安装师傅");
        }
        return order;
    }

    private WorkOrderEntity requireAccessibleOrder(AuthenticatedUser actor, long orderId) {
        LambdaQueryWrapper<WorkOrderEntity> query = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getId, orderId).eq(WorkOrderEntity::getDeleted, false);
        if (!actor.hasRole(RoleCode.ADMIN)) {
            if (actor.hasRole(RoleCode.INSTALLER)) query.eq(WorkOrderEntity::getInstallerUserId, actor.userId());
            else query.eq(WorkOrderEntity::getCustomerUserId, actor.userId());
        }
        WorkOrderEntity order = orderMapper.selectOne(query);
        if (order == null) throw new BusinessException(404, "ORDER_NOT_FOUND", "订单不存在");
        return order;
    }

    private void ensureActive(WorkOrderEntity order, String code, String message) {
        if (!ACTIVE_STATUSES.contains(order.getOrderStatus())) throw new BusinessException(409, code, message);
    }

    private void recordStatus(long orderId, String from, String to, String reason, long actorId) {
        WorkOrderStatusHistoryEntity history = new WorkOrderStatusHistoryEntity();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangeReason(reason);
        history.setOperatorUserId(actorId);
        history.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        historyMapper.insert(history);
    }

    private List<ProgressView> toViews(AuthenticatedUser actor, List<WorkOrderProgressEntity> records) {
        if (records.isEmpty()) return List.of();
        List<Long> progressIds = records.stream().map(WorkOrderProgressEntity::getId).toList();
        List<FileRelationRecord> links = relationMapper.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, "PROGRESS")
                .in(FileRelationRecord::getBusinessId, progressIds)
                .orderByAsc(FileRelationRecord::getSortOrder).orderByAsc(FileRelationRecord::getId));
        Set<Long> fileIds = links.stream().map(FileRelationRecord::getFileId).collect(java.util.stream.Collectors.toSet());
        Map<Long, FileAssetRecord> files = new HashMap<>();
        if (!fileIds.isEmpty()) fileMapper.selectByIds(fileIds).forEach(file -> files.put(file.getId(), file));
        Map<Long, List<ProgressFileView>> grouped = new HashMap<>();
        links.stream().sorted(Comparator.comparing(FileRelationRecord::getSortOrder)
                        .thenComparing(FileRelationRecord::getId))
                .forEach(link -> {
                    FileAssetRecord file = files.get(link.getFileId());
                    if (file != null && !Boolean.TRUE.equals(file.getDeleted())) {
                        grouped.computeIfAbsent(link.getBusinessId(), ignored -> new ArrayList<>())
                                .add(new ProgressFileView(file.getId(), file.getOriginalName(), file.getMimeType(),
                                        fileService.issueAccess(actor, file.getId()).url()));
                    }
                });
        return records.stream().map(record -> new ProgressView(record.getId(), record.getOrderId(),
                record.getInstallerUserId(), record.getProgressType(), record.getDescription(),
                grouped.getOrDefault(record.getId(), List.of()), record.getSubmittedAt())).toList();
    }

    public record ProgressCommand(String description, List<Long> fileIds) { }
    public record ProgressFileView(long id, String originalName, String mimeType, String url) { }
    public record ProgressView(long id, long orderId, long installerUserId, String type, String description,
                               List<ProgressFileView> images, LocalDateTime submittedAt) { }
}
