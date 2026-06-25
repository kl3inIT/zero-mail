package com.zeromail.core.admin.overview.usecases;

import com.zeromail.core.admin.overview.persistence.lowlevel.AdminOverviewReadRepository;
import com.zeromail.core.admin.overview.projection.AdminOverviewAlert;
import com.zeromail.core.admin.overview.projection.AdminOverviewKpis;
import com.zeromail.core.admin.overview.projection.AdminOverviewQuery;
import com.zeromail.core.admin.overview.projection.AdminOverviewRange;
import com.zeromail.core.admin.overview.projection.AdminOverviewSnapshot;
import com.zeromail.core.admin.overview.projection.AdminOverviewSuccessRate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOverviewQueryService {

    private static final int TOP_TENANT_LIMIT = 5;

    private final AdminOverviewReadRepository adminOverviewReadRepository;
    private final Clock clock;

    public AdminOverviewQueryService(
            AdminOverviewReadRepository adminOverviewReadRepository, Clock clock) {
        this.adminOverviewReadRepository =
                Objects.requireNonNull(
                        adminOverviewReadRepository,
                        "adminOverviewReadRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public AdminOverviewSnapshot snapshot(AdminOverviewQuery adminOverviewQuery) {
        Objects.requireNonNull(adminOverviewQuery, "adminOverviewQuery must not be null");
        AdminOverviewKpis kpis =
                adminOverviewReadRepository.findKpis(
                        adminOverviewQuery, adminOverviewQuery.to().minus(Duration.ofDays(7)));
        AdminOverviewSuccessRate successRate = successRate(kpis);
        return new AdminOverviewSnapshot(
                new AdminOverviewRange(adminOverviewQuery.from(), adminOverviewQuery.to()),
                kpis,
                successRate,
                adminOverviewReadRepository.findDailyActivity(adminOverviewQuery),
                adminOverviewReadRepository.findActionDistribution(adminOverviewQuery),
                adminOverviewReadRepository.findTopActivityTenants(
                        adminOverviewQuery, TOP_TENANT_LIMIT),
                adminOverviewReadRepository.findTopSpendTenants(
                        adminOverviewQuery, TOP_TENANT_LIMIT),
                alerts(kpis, successRate),
                clock.instant());
    }

    private static AdminOverviewSuccessRate successRate(AdminOverviewKpis kpis) {
        int total = kpis.triageActionCount();
        double failureRatePercent = percent(kpis.failedTriageActionCount(), total);
        double successRatePercent = total == 0 ? 0.0 : 100.0 - failureRatePercent;
        return new AdminOverviewSuccessRate(successRatePercent, failureRatePercent);
    }

    private static List<AdminOverviewAlert> alerts(
            AdminOverviewKpis kpis, AdminOverviewSuccessRate successRate) {
        List<AdminOverviewAlert> alerts = new ArrayList<>(6);
        alerts.add(
                new AdminOverviewAlert(
                        "GMAIL_UNHEALTHY",
                        kpis.gmailUnhealthyTenants() > 0 ? "ERROR" : "INFO",
                        "Gmail token refresh lỗi",
                        kpis.gmailUnhealthyTenants() + " tenant cần kiểm tra kết nối Gmail",
                        kpis.gmailUnhealthyTenants(),
                        "Hiện tại"));
        alerts.add(
                new AdminOverviewAlert(
                        "PUBSUB_BACKLOG",
                        kpis.pubsubBacklogCount() > 0 ? "WARNING" : "INFO",
                        "Pub/Sub backlog cao",
                        kpis.pubsubBacklogCount() + " message/job đang chờ xử lý",
                        kpis.pubsubBacklogCount(),
                        "Hiện tại"));
        alerts.add(
                new AdminOverviewAlert(
                        "TRIAGE_FAILURE_RATE",
                        successRate.failureRatePercent() >= 2.0 ? "WARNING" : "INFO",
                        "Triage failure rate cao",
                        formatPercent(successRate.failureRatePercent()) + " trong khoảng đã chọn",
                        kpis.failedTriageActionCount(),
                        "Hiện tại"));
        alerts.add(
                new AdminOverviewAlert(
                        "OUTBOUND_BLOCKED",
                        kpis.blockedOutboundActionCount() > 0 ? "ERROR" : "INFO",
                        "Outbound action bị blocked nhiều",
                        kpis.blockedOutboundActionCount() + " action bị chặn trong khoảng đã chọn",
                        kpis.blockedOutboundActionCount(),
                        "Hiện tại"));
        alerts.add(
                new AdminOverviewAlert(
                        "LOW_CREDIT",
                        kpis.lowCreditTenantCount() > 0 ? "WARNING" : "INFO",
                        "Tenant sắp hết credit",
                        kpis.lowCreditTenantCount() + " tenant có số dư thấp",
                        kpis.lowCreditTenantCount(),
                        "Hiện tại"));
        alerts.add(
                new AdminOverviewAlert(
                        "DEAD_LETTER",
                        kpis.deadLetterJobCount() > 0 ? "ERROR" : "INFO",
                        "Queue job stuck / dead-letter",
                        kpis.deadLetterJobCount() + " job trong dead-letter",
                        kpis.deadLetterJobCount(),
                        "Hiện tại"));
        return List.copyOf(alerts);
    }

    private static double percent(int value, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static String formatPercent(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
        return rounded.stripTrailingZeros().toPlainString() + "%";
    }
}
