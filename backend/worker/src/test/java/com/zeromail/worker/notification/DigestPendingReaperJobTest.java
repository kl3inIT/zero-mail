package com.zeromail.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.worker.PostgresContainerTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;

@ResourceLock("digest-dispatch-db")
class DigestPendingReaperJobTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DigestPendingReaperJob reaperJob;

    @BeforeEach
    void setUp() {
        DigestDispatchTestData.resetDigestTables(jdbcTemplate);
    }

    @Test
    void reaper_marks_stuck_pending_rows_failed_and_leaves_fresh_pending_rows() {
        UUID tenantId = DigestDispatchTestData.seedTenant(jdbcTemplate, "Asia/Ho_Chi_Minh");
        UUID oldDeliveryId =
                DigestDispatchTestData.seedDigestDelivery(
                        jdbcTemplate,
                        tenantId,
                        LocalDate.parse("2026-05-12"),
                        "PENDING",
                        Instant.now().minusSeconds(45 * 60));
        UUID freshDeliveryId =
                DigestDispatchTestData.seedDigestDelivery(
                        jdbcTemplate,
                        tenantId,
                        LocalDate.parse("2026-05-13"),
                        "PENDING",
                        Instant.now().minusSeconds(5 * 60));

        int processed = reaperJob.reap();

        assertThat(processed).isEqualTo(1);
        assertThat(DigestDispatchTestData.deliveryStatusById(jdbcTemplate, oldDeliveryId))
                .isEqualTo("FAILED");
        assertThat(DigestDispatchTestData.deliveryFailureReasonById(jdbcTemplate, oldDeliveryId))
                .isEqualTo("reaper_stuck_pending");
        assertThat(DigestDispatchTestData.deliveryStatusById(jdbcTemplate, freshDeliveryId))
                .isEqualTo("PENDING");
    }

    @Test
    void scheduled_reap_fails_fast_when_shedlock_is_not_held() {
        DigestPendingReaperJob targetReaperJob = AopTestUtils.getTargetObject(reaperJob);

        assertThatThrownBy(targetReaperJob::scheduledReap)
                .isInstanceOf(IllegalStateException.class);
    }
}
