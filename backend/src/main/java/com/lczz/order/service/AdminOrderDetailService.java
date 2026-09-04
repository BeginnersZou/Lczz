package com.lczz.order.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.service.OrderService.OrderView;
import com.lczz.progress.service.WorkProgressService;
import com.lczz.progress.service.WorkProgressService.ProgressView;
import com.lczz.review.service.OrderReviewService;
import com.lczz.review.service.OrderReviewService.ReviewView;
import com.lczz.stocking.service.MaterialRequestService;
import com.lczz.stocking.service.MaterialRequestService.RequestView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderDetailService {
    private final OrderService orderService;
    private final WorkProgressService progressService;
    private final MaterialRequestService materialRequestService;
    private final OrderReviewService reviewService;

    public AdminOrderDetailService(OrderService orderService, WorkProgressService progressService,
                                   MaterialRequestService materialRequestService, OrderReviewService reviewService) {
        this.orderService = orderService;
        this.progressService = progressService;
        this.materialRequestService = materialRequestService;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailView detail(AuthenticatedUser actor, long orderId) {
        if (!actor.hasRole(RoleCode.ADMIN)) {
            throw new BusinessException(403, "ADMIN_ORDER_DETAIL_FORBIDDEN", "仅管理员可查看后台订单详情");
        }
        // Resolve the order first so missing/deleted orders cannot expose related records.
        OrderView order = orderService.detail(actor, orderId);
        return new AdminOrderDetailView(order, progressService.list(actor, orderId),
                materialRequestService.listByOrder(actor, orderId), reviewService.byOrder(actor, orderId));
    }

    public record AdminOrderDetailView(OrderView order, List<ProgressView> progress,
                                       List<RequestView> materialRequests,
                                       @JsonInclude(JsonInclude.Include.ALWAYS) ReviewView review) { }
}
