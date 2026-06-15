package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.tenant.persistence.lowlevel.TenantInspectionReadRepository;
import com.zeromail.core.admin.tenant.projection.TenantActivitySnapshot;
import com.zeromail.core.admin.tenant.projection.TenantBillingSnapshot;
import com.zeromail.core.admin.tenant.projection.TenantDeletionPreview;
import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import com.zeromail.core.admin.tenant.projection.TenantHealthSnapshot;
import com.zeromail.core.admin.tenant.projection.TenantListPage;
import com.zeromail.core.admin.tenant.projection.TenantListQuery;
import com.zeromail.core.admin.tenant.projection.TenantListRow;
import com.zeromail.core.admin.tenant.projection.TenantListSummary;
import com.zeromail.core.admin.tenant.projection.TenantSpendSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the admin tenant detail / list views. Raw SQL lives in {@link
 * TenantInspectionReadRepository}; this service composes the snapshots, applies the LOW/MEDIUM/HIGH
 * spend bucket derivation, and throws {@link NoSuchElementException} when a tenant is missing.
 */
@Service
public class TenantInspectionService {

    private static final int HIGH_BUCKET_THRESHOLD = 100;
    private static final int MEDIUM_BUCKET_THRESHOLD = 10;

    private final TenantInspectionReadRepository tenantInspectionReadRepository;

    public TenantInspectionService(TenantInspectionReadRepository tenantInspectionReadRepository) {
        this.tenantInspectionReadRepository =
                Objects.requireNonNull(
                        tenantInspectionReadRepository,
                        "tenantInspectionReadRepository must not be null");
    }

    @Transactional(readOnly = true)
    public TenantListPage listTenants(TenantListQuery query) {
        TenantListQuery tenantListQuery = Objects.requireNonNull(query, "query must not be null");
        List<TenantListRow> rows =
                tenantInspectionReadRepository.findTenantListRows(tenantListQuery);
        boolean hasNextPage = rows.size() > tenantListQuery.limit();
        List<TenantListRow> visibleRows =
                hasNextPage ? rows.subList(0, tenantListQuery.limit()) : rows;
        String nextCursor =
                hasNextPage
                        ? String.valueOf(tenantListQuery.offset() + tenantListQuery.limit())
                        : null;
        TenantListSummary summary =
                tenantInspectionReadRepository.findTenantListSummary(tenantListQuery);
        return new TenantListPage(visibleRows, nextCursor, hasNextPage, summary);
    }

    @Transactional(readOnly = true)
    public TenantDetailOverview getOverview(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return tenantInspectionReadRepository
                .findOverview(targetTenantId)
                .orElseThrow(() -> tenantNotFound(targetTenantId));
    }

    @Transactional(readOnly = true)
    public TenantHealthSnapshot getHealth(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return tenantInspectionReadRepository
                .findHealth(targetTenantId)
                .orElseThrow(() -> tenantNotFound(targetTenantId));
    }

    @Transactional(readOnly = true)
    public TenantBillingSnapshot getBilling(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireTenantExists(targetTenantId);
        int creditsBalance = tenantInspectionReadRepository.findCreditsBalance(targetTenantId);
        Instant lastTopUpAt = tenantInspectionReadRepository.findLastTopUpAt(targetTenantId);
        String currentPlanCode = tenantInspectionReadRepository.findCurrentPlanCode(targetTenantId);
        return new TenantBillingSnapshot(creditsBalance, currentPlanCode, lastTopUpAt);
    }

    @Transactional(readOnly = true)
    public TenantSpendSnapshot getSpend(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireTenantExists(targetTenantId);
        int last7dCallCount =
                tenantInspectionReadRepository.countCallsSince(targetTenantId, Duration.ofDays(7));
        int last30dCallCount =
                tenantInspectionReadRepository.countCallsSince(targetTenantId, Duration.ofDays(30));
        Map<String, Integer> perFeatureCallCount =
                tenantInspectionReadRepository.findPerFeatureCallCount(targetTenantId);
        return new TenantSpendSnapshot(
                last7dCallCount,
                last30dCallCount,
                bucketForCallCount(last7dCallCount),
                bucketForCallCount(last30dCallCount),
                perFeatureCallCount);
    }

    @Transactional(readOnly = true)
    public TenantActivitySnapshot getActivity(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return tenantInspectionReadRepository
                .findActivity(targetTenantId)
                .orElseThrow(() -> tenantNotFound(targetTenantId));
    }

    @Transactional(readOnly = true)
    public String getGmailAccountEmail(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return tenantInspectionReadRepository.findGmailAccountEmail(targetTenantId);
    }

    @Transactional(readOnly = true)
    public TenantDeletionPreview getDeletionPreview(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireTenantExists(targetTenantId);
        return new TenantDeletionPreview(
                tenantInspectionReadRepository.countTenantOwnedRows(
                        "gmail_connections", targetTenantId),
                tenantInspectionReadRepository.countTenantOwnedRows("chat", targetTenantId),
                tenantInspectionReadRepository.countTenantOwnedRows("rules", targetTenantId),
                tenantInspectionReadRepository.countTenantOwnedRows("triage_audit", targetTenantId),
                tenantInspectionReadRepository.countTenantOwnedRows("chat_message", targetTenantId),
                tenantInspectionReadRepository.countTenantOwnedRows(
                        "tenant_byok_credentials", targetTenantId));
    }

    private void requireTenantExists(UUID tenantId) {
        if (!tenantInspectionReadRepository.exists(tenantId)) {
            throw tenantNotFound(tenantId);
        }
    }

    private static NoSuchElementException tenantNotFound(UUID tenantId) {
        return new NoSuchElementException("Tenant not found: " + tenantId);
    }

    private static String bucketForCallCount(int callCount) {
        if (callCount >= HIGH_BUCKET_THRESHOLD) {
            return "HIGH";
        }
        if (callCount >= MEDIUM_BUCKET_THRESHOLD) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
