package com.lczz.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.persistence.FileRelationRecord;
import com.lczz.file.persistence.FileRelationRecordMapper;
import com.lczz.file.service.FileService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.order.persistence.WorkOrderStatusHistoryEntity;
import com.lczz.order.persistence.WorkOrderStatusHistoryMapper;
import com.lczz.review.persistence.WorkOrderReviewEntity;
import com.lczz.review.persistence.WorkOrderReviewMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReviewService {
    private static final Pattern FILE_URL = Pattern.compile("/api(?:/v1)?/files/(?:access/)?(\\d+)(?:[/?].*)?");
    private final WorkOrderReviewMapper reviewMapper;
    private final WorkOrderMapper orderMapper;
    private final WorkOrderStatusHistoryMapper historyMapper;
    private final FileRelationRecordMapper relationMapper;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public OrderReviewService(WorkOrderReviewMapper reviewMapper, WorkOrderMapper orderMapper,
                              WorkOrderStatusHistoryMapper historyMapper,
                              FileRelationRecordMapper relationMapper, FileService fileService,
                              ObjectMapper objectMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.historyMapper = historyMapper;
        this.relationMapper = relationMapper;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    public ReviewView byOrder(AuthenticatedUser actor, long orderId) {
        ensureAdminReader(actor);
        requireAccessibleOrder(actor, orderId);
        WorkOrderReviewEntity review = findByOrder(orderId);
        return review == null ? null : toView(actor, review);
    }

    public List<String> reviewedOrderIds(AuthenticatedUser actor) {
        ensureAdminReader(actor);
        LambdaQueryWrapper<WorkOrderEntity> orderQuery = scopedOrders(actor)
                .eq(WorkOrderEntity::getOrderStatus, "REVIEWED");
        Set<Long> reviewed = reviewMapper.selectList(new LambdaQueryWrapper<WorkOrderReviewEntity>())
                .stream().map(WorkOrderReviewEntity::getOrderId).collect(java.util.stream.Collectors.toSet());
        if (reviewed.isEmpty()) return List.of();
        return orderMapper.selectList(orderQuery.in(WorkOrderEntity::getId, reviewed)
                        .orderByDesc(WorkOrderEntity::getUpdatedAt).orderByDesc(WorkOrderEntity::getId))
                .stream().map(order -> Long.toString(order.getId())).toList();
    }

    @Transactional
    public ReviewSubmissionView submit(AuthenticatedUser actor, ReviewCommand command) {
        ensureReviewerRole(actor);
        WorkOrderEntity order = orderMapper.selectForUpdate(command.orderId());
        if (order == null || !Objects.equals(order.getCustomerUserId(), actor.userId())) {
            throw new BusinessException(404, "ORDER_NOT_BOUND", "订单不存在或未绑定到当前用户");
        }
        if (findByOrder(order.getId()) != null || "REVIEWED".equals(order.getOrderStatus())) {
            throw new BusinessException(409, "ORDER_ALREADY_REVIEWED", "该订单已经评价，不能重复提交");
        }
        if (!"PENDING_REVIEW".equals(order.getOrderStatus())) {
            throw new BusinessException(409, "ORDER_NOT_REVIEWABLE", "仅待评价订单可以提交评价");
        }
        List<Long> fileIds = normalizeFiles(command.fileIds(), command.images());
        List<String> labels = normalizeLabels(command.labels(), command.label());
        WorkOrderReviewEntity review = new WorkOrderReviewEntity();
        review.setOrderId(order.getId());
        review.setReviewerUserId(actor.userId());
        review.setScore(command.score());
        review.setLiked(Boolean.TRUE.equals(command.liked()));
        review.setContent(command.content().trim());
        review.setLabelsJson(writeLabels(labels));
        review.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "ORDER_ALREADY_REVIEWED", "该订单已经评价，不能重复提交");
        }
        for (int index = 0; index < fileIds.size(); index++) {
            fileService.bind(actor, fileIds.get(index),
                    new FileService.RelationCommand("REVIEW", review.getId(), "REVIEW", index));
        }
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getId, order.getId())
                .eq(WorkOrderEntity::getCustomerUserId, actor.userId())
                .eq(WorkOrderEntity::getOrderStatus, "PENDING_REVIEW")
                .eq(WorkOrderEntity::getVersion, order.getVersion())
                .set(WorkOrderEntity::getOrderStatus, "REVIEWED")
                .set(WorkOrderEntity::getUpdatedBy, actor.userId())
                .set(WorkOrderEntity::getVersion, order.getVersion() + 1));
        if (updated != 1) throw new BusinessException(409, "ORDER_STATUS_CONFLICT", "订单状态已变化，请刷新后重试");
        recordStatus(order.getId(), actor.userId());
        // 评价内容属于后台管理信息。提交端仅获得提交凭证，不回传评分、文字、标签或图片。
        return new ReviewSubmissionView(review.getId(), order.getId(), "SUBMITTED");
    }

    private ReviewView toView(AuthenticatedUser actor, WorkOrderReviewEntity review) {
        List<String> images = relationMapper.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                        .eq(FileRelationRecord::getBusinessType, "REVIEW")
                        .eq(FileRelationRecord::getBusinessId, review.getId())
                        .eq(FileRelationRecord::getUsageType, "REVIEW")
                        .orderByAsc(FileRelationRecord::getSortOrder).orderByAsc(FileRelationRecord::getId))
                .stream().map(link -> fileService.issueAccess(actor, link.getFileId()).url()).toList();
        return new ReviewView(review.getId(), review.getOrderId(), review.getReviewerUserId(),
                review.getScore() == null ? 0 : review.getScore(),
                Boolean.TRUE.equals(review.getLiked()), review.getContent(), readLabels(review.getLabelsJson()),
                images, review.getCreatedAt());
    }

    private WorkOrderReviewEntity findByOrder(long orderId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<WorkOrderReviewEntity>()
                .eq(WorkOrderReviewEntity::getOrderId, orderId));
    }

    private WorkOrderEntity requireAccessibleOrder(AuthenticatedUser actor, long orderId) {
        WorkOrderEntity order = orderMapper.selectOne(scopedOrders(actor).eq(WorkOrderEntity::getId, orderId));
        if (order == null) throw new BusinessException(404, "ORDER_NOT_FOUND", "订单不存在");
        return order;
    }

    private LambdaQueryWrapper<WorkOrderEntity> scopedOrders(AuthenticatedUser actor) {
        LambdaQueryWrapper<WorkOrderEntity> query = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getDeleted, false);
        if (actor.hasRole(RoleCode.ADMIN)) return query;
        if (actor.hasRole(RoleCode.INSTALLER)) return query.eq(WorkOrderEntity::getInstallerUserId, actor.userId());
        return query.eq(WorkOrderEntity::getCustomerUserId, actor.userId());
    }

    private void ensureReviewerRole(AuthenticatedUser actor) {
        if (!actor.hasRole(RoleCode.CUSTOMER) && !actor.hasRole(RoleCode.DEALER)) {
            throw new BusinessException(403, "REVIEW_SUBMIT_FORBIDDEN", "仅订单绑定客户可以提交评价");
        }
    }

    private void ensureAdminReader(AuthenticatedUser actor) {
        if (!actor.hasRole(RoleCode.ADMIN)) {
            throw new BusinessException(403, "REVIEW_READ_FORBIDDEN", "评价内容仅管理员可查看");
        }
    }

    private List<Long> normalizeFiles(List<Long> fileIds, List<String> images) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        if (fileIds != null) {
            for (Long id : fileIds) {
                if (id == null || id < 1) throw new BusinessException("INVALID_REVIEW_FILE", "评价图片 ID 不合法");
                if (!unique.add(id)) throw new BusinessException("DUPLICATE_REVIEW_FILE", "评价图片不能重复");
            }
        }
        if (images != null) {
            for (String image : images) {
                Matcher matcher = FILE_URL.matcher(image == null ? "" : image.trim());
                if (!matcher.matches()) throw new BusinessException("INVALID_REVIEW_FILE", "评价图片必须来自统一文件服务");
                if (!unique.add(Long.parseLong(matcher.group(1)))) {
                    throw new BusinessException("DUPLICATE_REVIEW_FILE", "评价图片不能重复");
                }
            }
        }
        if (unique.size() > 9) throw new BusinessException("FILE_COUNT_LIMIT", "评价图片最多 9 张");
        return List.copyOf(unique);
    }

    private List<String> normalizeLabels(List<String> labels, String label) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (labels != null) labels.forEach(value -> addLabel(normalized, value));
        addLabel(normalized, label);
        if (normalized.size() > 5) throw new BusinessException("REVIEW_LABEL_LIMIT", "评价标签最多 5 个");
        return List.copyOf(normalized);
    }

    private void addLabel(Set<String> labels, String value) {
        if (value == null || value.isBlank()) return;
        String normalized = value.trim();
        if (normalized.length() > 20) throw new BusinessException("INVALID_REVIEW_LABEL", "评价标签不能超过 20 个字符");
        labels.add(normalized);
    }

    private String writeLabels(List<String> labels) {
        try { return objectMapper.writeValueAsString(labels); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private List<String> readLabels(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("评价标签数据损坏", exception); }
    }

    private void recordStatus(long orderId, long actorId) {
        WorkOrderStatusHistoryEntity history = new WorkOrderStatusHistoryEntity();
        history.setOrderId(orderId);
        history.setFromStatus("PENDING_REVIEW");
        history.setToStatus("REVIEWED");
        history.setChangeReason("订单客户提交评价");
        history.setOperatorUserId(actorId);
        history.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        historyMapper.insert(history);
    }

    public record ReviewCommand(long orderId, int score, String content, Boolean liked, String label,
                                List<String> labels, List<Long> fileIds, List<String> images) { }
    public record ReviewSubmissionView(long id, long orderId, String status) { }
    public record ReviewView(long id, long orderId, long reviewerUserId, int score, boolean liked, String content,
                             List<String> labels, List<String> images, LocalDateTime createTime) { }
}
