package com.lczz.common.time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.order.service.OrderConfirmationService.ConfirmationView;
import com.lczz.progress.service.WorkProgressService.ProgressView;
import com.lczz.review.service.OrderReviewService.ReviewView;
import com.lczz.stocking.service.MaterialRequestService.RequestView;
import com.lczz.user.service.UserManagementService.UserView;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
@Import(BusinessTimeJsonConfig.class)
class BusinessTimeJsonTests {
    @Autowired ObjectMapper mapper;

    @Test
    void storedUtcProgressAndBeijingDatabaseTimeDescribeTheSameInstant() throws Exception {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 12, 2, 36);
        var progress = new ProgressView(1, 2, 3, "PROGRESS", "上午施工", List.of(), utc,
                "version", "CONFIRMED", true, 4L, utc, "version", null, null);
        assertThat(mapper.readTree(mapper.writeValueAsString(progress)).path("submittedAt").asText())
                .isEqualTo("2026-10-12T10:36:00+08:00");
        assertThat(mapper.writeValueAsString(utc.plusHours(8))).isEqualTo("\"2026-10-12T10:36:00+08:00\"");
        // Serialization must not migrate or mutate the existing database value.
        assertThat(progress.submittedAt()).isEqualTo(utc);
        assertThat(mapper.readTree(mapper.writeValueAsString(progress)).path("customerConfirmedAt").asText())
                .isEqualTo("2026-10-12T10:36:00+08:00");
    }

    @Test
    void materialSubmissionCompletionAndVoidTimesAreAllMarkedAsStoredUtc() throws Exception {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 11, 16, 15);
        var request = new RequestView(1, "MR", 2, "WO", "安装", "待备货", "PENDING", "待备货",
                null, List.of(), utc, utc, 3L, utc, 3L, utc, null);
        var json = mapper.readTree(mapper.writeValueAsString(request));
        for (String field : List.of("createTime", "submittedAt", "completedAt", "voidedAt")) {
            assertThat(json.path(field).asText()).isEqualTo("2026-10-12T00:15:00+08:00");
        }
    }

    @Test
    void loginTimeIsUtcButRegistrationAndUpdateTimesRemainBeijingWallTimes() throws Exception {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 12, 2, 36);
        LocalDateTime local = utc.plusHours(8);
        var user = new UserView(1, "admin", "管理员", "管理员", "UNKNOWN", "13800138000", "ADMIN",
                List.of("ADMIN"), "ENABLED", "APPROVED", false, null, utc, local, local, local);
        var json = mapper.readTree(mapper.writeValueAsString(user));
        for (String field : List.of("lastLoginAt", "createdAt", "registerTime", "updatedAt")) {
            assertThat(json.path(field).asText()).isEqualTo("2026-10-12T10:36:00+08:00");
        }
    }

    @Test
    void reviewsAndExistingZonedCustomerConfirmationKeepTheCorrectInstant() throws Exception {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 11, 16, 15);
        var review = new ReviewView(1, 2, 3, 5, true, "满意", List.of(), List.of(), List.of(), utc);
        var confirmation = new ConfirmationView(2, "已完成", "PENDING_REVIEW", 3,
                utc.atOffset(ZoneOffset.UTC));
        assertThat(mapper.readTree(mapper.writeValueAsString(review)).path("createTime").asText())
                .isEqualTo("2026-10-12T00:15:00+08:00");
        assertThat(mapper.readTree(mapper.writeValueAsString(confirmation)).path("customerConfirmedAt").asText())
                .isEqualTo("2026-10-12T00:15:00+08:00");
    }
}
